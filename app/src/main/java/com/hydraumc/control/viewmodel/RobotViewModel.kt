// =============================================================================
// HYDRA-UMC CONTROL - Primary ViewModel managing robot state and connectivity
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
//
// Holds one HydraState mirror (model/HydraState.kt) and keeps it in sync
// with a HYDRA-UMC STUDIO server in both directions, following the exact
// contract in HYDRA-UMC-STUDIO/docs/REMOTE_API.md: GET/POST /api/settings
// for the read-modify-write cycle, GET /api/hydra-info for discovery, and
// /ws for live push - the server broadcasts every change (from ANY client)
// to every connected client. Mirrors HYDRA-UMC SUITE's own
// hydra_suite/net/client.py HydraConnection in spirit: every mutating
// action here (jog, speed, tool change, play/pause/stop, enable/disable)
// mutates the local RobotView in place, then pushState() sends the WHOLE
// object back (over the WebSocket if open, REST POST otherwise) - exactly
// like HYDRA-UMC-STUDIO's own browser UI (RobotDetail.tsx's updateRobot()).
// =============================================================================
package com.hydraumc.control.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hydraumc.control.model.BleDevice
import com.hydraumc.control.model.HydraState
import com.hydraumc.control.model.RobotView
import com.hydraumc.control.model.ServerInfo
import com.hydraumc.control.R
import com.hydraumc.control.network.AuthPrefs
import com.hydraumc.control.network.ConnectionPrefs
import com.hydraumc.control.network.HydraApiClient
import com.hydraumc.control.network.HydraApiException
import com.hydraumc.control.network.HydraWebSocket
import com.hydraumc.control.network.HydraBleClient
import com.hydraumc.control.network.StateCache
import com.hydraumc.control.network.WsStatus
import com.hydraumc.control.network.scanSubnets
import com.hydraumc.control.util.NotificationHelper
import kotlinx.coroutines.launch
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.annotation.SuppressLint

/** 
 * Data class for ATC tools with slot and name. 
 * @property slot The ATC slot number.
 * @property name The name of the tool.
 */
data class AtcTool(val slot: Int, val name: String)

/** 
 * Flat, display-friendly snapshot of one Job.
 * @property name Job name.
 */
data class JobState(val name: String)

/** 
 * Flat, display-friendly snapshot of one RobotView.
 * @property id Robot unique ID.
 * @property name Robot name.
 * @property online Connection status.
 * @property isPlaying Movement status.
 * @property isPaused Pause status.
 * @property hasXYTable Presence of XY table.
 * @property hasAtc Presence of ATC.
 * @property currentTool Attached tool.
 * @property atcTools List of available tools.
 * @property speed Current speed.
 * @property acceleration Current acceleration.
 * @property posX X coordinate.
 * @property posY Y coordinate.
 * @property posZ Z coordinate.
 * @property xyPosX XY Table X coordinate.
 * @property xyPosY XY Table Y coordinate.
 */
data class RobotState(
    val id: Int,
    val name: String,
    val model: String,
    val manufacturer: String,
    val role: String,
    val online: Boolean,
    val isPlaying: Boolean,
    val isPaused: Boolean,
    val hasXYTable: Boolean,
    val hasAtc: Boolean,
    val hasCamera: Boolean,
    val hasPnP: Boolean,
    val hasCNC: Boolean,
    val hasLaser: Boolean,
    val hasHeatedBed: Boolean,
    val hasVacuumTable: Boolean,
    val hasRack: Boolean,
    val combinedWith: List<Int>,
    val currentTool: String,
    val atcTools: List<AtcTool>,
    val speed: Double,
    val acceleration: Double,
    val posX: Double,
    val posY: Double,
    val posZ: Double,
    val xyPosX: Double,
    val xyPosY: Double,
)

/** 
 * Extension function to convert a RobotView model into a displayable RobotState. 
 */
private fun RobotView.toDisplay(): RobotState = RobotState(
    id = id,
    name = name,
    model = model,
    manufacturer = manufacturer,
    role = role,
    online = online,
    isPlaying = isPlaying,
    isPaused = isPaused,
    hasXYTable = hasXYTable,
    hasAtc = hasAtc,
    hasCamera = hasCamera,
    hasPnP = hasPnP,
    hasCNC = hasCNC,
    hasLaser = hasLaser,
    hasHeatedBed = hasHeatedBed,
    hasVacuumTable = hasVacuumTable,
    hasRack = hasRack,
    combinedWith = mutableListOf<Int>().apply {
        val arr = raw.optJSONArray("combinedWith")
        if (arr != null) {
            for (i in 0 until arr.length()) add(arr.getInt(i))
        }
    },
    currentTool = tool,
    atcTools = atcTools.map { AtcTool(it.slot, it.tool) },
    speed = speed,
    acceleration = acceleration,
    posX = posAxis("x"),
    posY = posAxis("y"),
    posZ = posAxis("z"),
    xyPosX = xyTablePos.optDouble("x", 0.0),
    xyPosY = xyTablePos.optDouble("y", 0.0),
)

/**
 * Shared ViewModel responsible for robot orchestration, networking, and UI state management.
 * @param application The Android application context.
 */
class RobotViewModel(application: Application) : AndroidViewModel(application) {
    /** Current list of robots available for display. */
    val robots = mutableStateOf<List<RobotState>>(emptyList())
    /** List of jobs/trajectories available on the server. */
    val jobs = mutableStateOf<List<JobState>>(emptyList())
    /** ID of the currently selected robot in the Control screen. */
    val selectedRobotId = mutableStateOf<Int?>(null)

    /** User-defined target IP address. */
    val ipAddress = mutableStateOf("192.168.1.100")
    /** User-defined target port. */
    val port = mutableStateOf("3000")
    /** Overall network connection status string. */
    val connectionStatus = mutableStateOf(application.getString(R.string.status_disconnected))

    /** Latest Wi-Fi error message for user feedback. */
    val lastError = mutableStateOf<String?>(null)

    /** Latest Bluetooth error message. */
    val lastBtError = mutableStateOf<String?>(null)

    /** List of servers found during LAN discovery. */
    val discoveredServers = mutableStateOf<List<ServerInfo>>(emptyList())
    /** Currently connected server info. */
    val activeServer = mutableStateOf<ServerInfo?>(null)
    /** Boolean flag for network scanning activity. */
    val isScanning = mutableStateOf(value = false)

    /** List of BLE devices found during scanning. */
    val discoveredBtDevices = mutableStateOf<List<BleDevice>>(emptyList())
    /** Industrial Telemetry Log */
    val telemetryLogs = mutableStateOf<List<String>>(emptyList())
    /** Boolean flag for BLE scanning activity. */
    val isBtScanning = mutableStateOf(value = false)
    /** Status of the Bluetooth adapter on the device. */
    val isBtEnabled = mutableStateOf(value = false)
    /** Active Bluetooth LE scan callback. */
    private var bleScanCallback: ScanCallback? = null

    /** Internal core state container. */
    private var state = HydraState.empty()
    /** Active REST API client. */
    private var apiClient: HydraApiClient? = null
    /** Active WebSocket connection. */
    private var ws: HydraWebSocket? = null
    /** Active Bluetooth LE client. */
    private var bleClient: HydraBleClient? = null
    /** Persistent connection preferences manager. */
    private val prefs = ConnectionPrefs(application)
    /** Persistent authentication preferences manager. */
    private val authPrefs = AuthPrefs(application)
    /** Persistent state cache manager. */
    private val stateCache = StateCache(application)
    /** Flag to prevent clearing server list during intentional reconnection. */
    private var isSwitchingServer = false

    /** Login state */
    val isLoggedIn = mutableStateOf(value = false)
    val loginUsername = mutableStateOf(value = "")
    val loginPassword = mutableStateOf(value = "")
    val loginEmail = mutableStateOf(value = "")
    val loginRememberMe = mutableStateOf(value = false)
    val isBiometricEnabled = mutableStateOf(value = false)

    /** Camera selection */
    val selectedCameraId = mutableIntStateOf(1)

    init {
        connectionStatus.value = application.getString(R.string.status_disconnected)
        NotificationHelper.createChannel(application)

        /** Load saved connection settings on initialization. */
        viewModelScope.launch {
            prefs.load()?.let { (savedIp, savedPort) ->
                ipAddress.value = savedIp
                port.value = savedPort
            }
            
            // Load auth
            val profile = authPrefs.loadAuth()
            loginUsername.value = profile.username
            loginPassword.value = profile.password
            loginEmail.value = profile.email
            loginRememberMe.value = profile.rememberMe
            isBiometricEnabled.value = profile.isBiometricEnabled
            if (profile.rememberMe && profile.isLoggedIn) {
                // Auto login
                isLoggedIn.value = true
            }

            // Load cached state
            stateCache.loadState()?.let { cached ->
                applyState(HydraState(cached), isFromCache = true)
            }
        }
        
        /** Rationale: Initial check for Bluetooth adapter status. */
        val bluetoothManager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        isBtEnabled.value = bluetoothManager.adapter?.isEnabled ?: false
    }

    /**
     * Attempts to login with demo credentials.
     */
    fun login(user: String, pass: String, remember: Boolean) {
        if ((user == "demo") && (pass == "demo")) {
            isLoggedIn.value = true
            loginUsername.value = user
            loginPassword.value = pass
            loginRememberMe.value = remember
            viewModelScope.launch {
                authPrefs.saveAuth(
                    com.hydraumc.control.network.UserProfile(
                        username = user,
                        password = pass,
                        email = loginEmail.value,
                        rememberMe = remember,
                        isLoggedIn = true,
                    ),
                )
            }
        } else {
            lastError.value = "Invalid credentials"
        }
    }

    /**
     * Updates and persists the user profile.
     */
    fun saveUserProfile(user: String, pass: String, email: String, biometric: Boolean = isBiometricEnabled.value) {
        loginUsername.value = user
        loginPassword.value = pass
        loginEmail.value = email
        isBiometricEnabled.value = biometric
        viewModelScope.launch {
            authPrefs.saveAuth(
                com.hydraumc.control.network.UserProfile(
                    username = user,
                    password = pass,
                    email = email,
                    rememberMe = loginRememberMe.value,
                    isLoggedIn = isLoggedIn.value,
                    isBiometricEnabled = biometric,
                ),
            )
        }
    }

    /**
     * Clears all telemetry logs.
     */
    fun clearLogs() {
        telemetryLogs.value = emptyList()
    }

    /**
     * Logs out and clears session.
     */
    fun logout() {
        isLoggedIn.value = false
        viewModelScope.launch {
            authPrefs.clearAuth()
        }
    }

    private fun logTelemetry(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        telemetryLogs.value = (listOf("[$timestamp] $message") + telemetryLogs.value).take(50)
    }

    /** 
     * Refreshes the current Bluetooth adapter status.
     */
    fun refreshBtStatus() {
        val bluetoothManager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        val isActuallyEnabled = adapter?.isEnabled ?: false
        isBtEnabled.value = isActuallyEnabled
        if (!isActuallyEnabled) {
            disconnectBle()
            lastBtError.value = null
        }
    }

    /** 
     * Connects to a HYDRA-UMC server using the configured IP and Port.
     */
    fun connect() {
        val host = ipAddress.value.trim()
        val portValue = port.value.trim()
        if (host.isEmpty() || portValue.isEmpty()) {
            lastError.value = getApplication<Application>().getString(R.string.error_enter_ip_port)
            return
        }
        val portInt = portValue.toIntOrNull()
        if (portInt == null) {
            lastError.value = getApplication<Application>().getString(R.string.error_invalid_port, portValue)
            return
        }

        connectionStatus.value = getApplication<Application>().getString(R.string.status_connecting)
        lastError.value = null
        isSwitchingServer = true

        ws?.disconnect()
        bleClient?.disconnect()
        bleClient = null
        
        val client = HydraApiClient(host, portInt)
        apiClient = client

        viewModelScope.launch {
            prefs.save(host, portValue)
        }

        viewModelScope.launch {
            try {
                logTelemetry("Connecting to $host:$portInt...")
                val info = client.getHydraInfo()
                if (info != null) {
                    val server = ServerInfo.fromHydraInfo(host, portInt, info)
                    activeServer.value = server
                    logTelemetry("Server verified: ${server.displayName}")
                    if (discoveredServers.value.none { (it.host == host) && (it.port == portInt) }) {
                        discoveredServers.value += server
                    }
                }
                
                val settings = client.getSettings()
                logTelemetry("Initial state synchronized via REST")
                applyState(HydraState(settings))
            } catch (e: HydraApiException) {
                logTelemetry("REST Sync Error: ${e.message}")
                lastError.value = e.message
            } finally {
                isSwitchingServer = false
            }
        }

        ws = HydraWebSocket(
            host = host,
            port = portInt,
            onStatus = { status ->
                connectionStatus.value = when (status) {
                    WsStatus.CONNECTING -> {
                        logTelemetry("WebSocket connecting...")
                        getApplication<Application>().getString(R.string.status_connecting)
                    }
                    WsStatus.CONNECTED -> {
                        logTelemetry("WebSocket CONNECTED")
                        getApplication<Application>().getString(R.string.status_connected)
                    }
                    WsStatus.DISCONNECTED -> {
                        logTelemetry("WebSocket DISCONNECTED")
                        // Only clear if NOT an intentional switch
                        if (!isSwitchingServer) {
                            discoveredServers.value = emptyList()
                        }
                        getApplication<Application>().getString(R.string.status_disconnected)
                    }
                }
            },
            onSettings = { payload -> applyState(HydraState(payload)) },
        ) { message -> 
            if (!isSwitchingServer) {
                lastError.value = message 
            }
        }.also { it.connect() }
    }

    /** 
     * Internal helper to update the UI models when a new core state arrives.
     * @param newState The fresh HydraState object.
     * @param isFromCache Whether this update is from persistent storage.
     */
    private fun applyState(newState: HydraState, isFromCache: Boolean = false) {
        val oldState = state
        state = newState
        robots.value = newState.allRobots.map { it.toDisplay() }
        jobs.value = newState.allJobs.map { JobState(it.name) }
        
        if (!isFromCache) {
            viewModelScope.launch {
                stateCache.saveState(newState.toJson())
            }
            
            // Industrial Notification Logic: Alert on job completion
            newState.allRobots.forEach { robot ->
                val oldRobot = oldState.robotById(robot.id)
                if ((oldRobot != null) && oldRobot.isPlaying && !robot.isPlaying) {
                    NotificationHelper.sendAlert(
                        getApplication(), 
                        "Robot ${robot.name}", 
                        "Job sequence completed successfully.",
                    )
                }
            }
        }

        if ((selectedRobotId.value == null) || (robots.value.none { it.id == selectedRobotId.value })) {
            selectedRobotId.value = robots.value.firstOrNull()?.id
        }
    }

    /** 
     * Pushes the current local state back to the server.
     * Priority: BLE -> WebSocket -> REST API.
     */
    private fun pushState(command: String? = null, params: org.json.JSONObject? = null) {
        val robotId = selectedRobotId.value
        robots.value = state.allRobots.map { it.toDisplay() } 
        
        // 1. Try BLE
        val payload = state.toJson()
        val sentOverBle = bleClient?.send(payload.toString()) ?: false
        if (sentOverBle) {
            logTelemetry("TX [BLE]: Payload sent")
            return
        }
        
        // 2. Try WebSocket (Real-time Full Sync)
        val sentOverWs = ws?.send(payload) ?: false
        if (sentOverWs) {
            logTelemetry("TX [WS]: State synchronized")
            return
        }
        
        // 3. Try Industrial Atomic API (REST) - New & Faster
        val client = apiClient ?: return
        viewModelScope.launch {
            try {
                if (command != null && robotId != null) {
                    val cmdPayload = org.json.JSONObject()
                        .put("command", command)
                        .put("params", params ?: org.json.JSONObject())
                    
                    client.postRobotCommand(robotId, cmdPayload)
                    logTelemetry("TX [REST]: Atomic Command '$command' sent")
                } else {
                    client.postSettings(payload)
                    logTelemetry("TX [REST]: Full state updated")
                }
                lastError.value = null
            } catch (e: HydraApiException) {
                logTelemetry("TX Error [REST]: ${e.message}")
                lastError.value = e.message
            }
        }
    }

    /** 
     * Helper to mutate a property of the selected robot and trigger a sync.
     * @param mutate Lambda that performs the mutation on a RobotView.
     */
    private fun mutateSelected(mutate: (RobotView) -> Unit) {
        val robotId = selectedRobotId.value ?: return
        val robotView = state.robotById(robotId) ?: run {
            lastError.value = getApplication<Application>().getString(R.string.error_robot_not_found)
            return
        }
        mutate(robotView)
        pushState()
    }

    /** 
     * Sends a top-level command to the selected robot.
     * @param command Command string (enable, disable, play, pause, stop).
     */
    fun sendCommand(command: String) {
        when (command) {
            "enable" -> mutateSelected { it.setOnline(value = true) }
            "disable" -> mutateSelected { it.setOnline(value = false) }
            "play" -> {
                mutateSelected { it.setPlaying(playing = true) }
                pushState("play")
                return
            }
            "pause" -> {
                mutateSelected { it.togglePaused() }
                pushState("pause")
                return
            }
            "stop" -> {
                mutateSelected { it.stop() }
                pushState("stop")
                return
            }
            else -> lastError.value = getApplication<Application>().getString(R.string.error_unknown_command, command)
        }
        pushState()
    }

    /** 
     * Moves the robot or XY table along a specific axis.
     * @param target Either "robot" or "xytable".
     * @param axis The axis name (x, y, z).
     * @param amount The distance to move.
     */
    fun jog(target: String, axis: String, amount: Double) {
        val robotId = selectedRobotId.value ?: return
        val controller = state.controllers.find { c -> c.robots.any { it.id == robotId } }
        
        mutateSelected { robot ->
            if (target == "xytable") {
                val currentXy = robot.xyTablePos.optDouble(axis, 0.0)
                val newVal = currentXy + amount
                robot.setXyTableAxis(axis, newVal)
                
                // ALSO update Controller-level stage for hardware sync
                controller?.setKbXyTableAxis(axis, newVal)
                logTelemetry("JOG [XY]: $axis -> $newVal")
                
                val params = org.json.JSONObject().put("axis", axis).put("amount", amount)
                pushState("jog", params)
            } else {
                val currentPos = robot.posAxis(axis)
                val newVal = currentPos + amount
                robot.setPosAxis(axis, newVal)
                
                logTelemetry("JOG [ARM]: $axis -> $newVal (Server IK)")
                val params = org.json.JSONObject().put("axis", axis).put("amount", amount)
                pushState("jog", params)
            }
        }
    }

    /** 
     * Updates the movement speed and acceleration for the selected robot.
     * @param speed Speed percentage.
     * @param acceleration Acceleration percentage.
     */
    fun setSpeed(speed: Double, acceleration: Double) {
        mutateSelected { robot ->
            robot.setSpeed(speed)
            robot.setAcceleration(acceleration)
        }
    }

    /** 
     * Triggers a tool change operation via the ATC.
     * @param slot The target ATC slot.
     */
    fun changeTool(slot: Int) {
        mutateSelected { robot ->
            val tool = robot.atcTools.find { it.slot == slot }
            if (tool == null) {
                lastError.value = getApplication<Application>().getString(R.string.error_no_tool_in_slot, slot)
            } else {
                robot.setTool(tool.tool)
            }
        }
    }

    /**
     * Directly updates the tool name for the selected robot (URTC style).
     */
    fun mutateSelectedTool(toolName: String) {
        mutateSelected { it.setTool(toolName) }
    }

    /** 
     * Starts a subnet scan to find active HYDRA-UMC servers.
     */
    fun scanNetwork() {
        if (isScanning.value) return
        isScanning.value = true
        discoveredServers.value = emptyList()
        viewModelScope.launch {
            try {
                scanSubnets(HydraApiClient.sharedHttpClient, portValue()).collect { server ->
                    discoveredServers.value += server
                }
            } finally {
                isScanning.value = false
            }
        }
    }

    /** 
     * Starts a Bluetooth LE scan to find nearby robots.
     */
    @SuppressLint("MissingPermission")
    fun scanBluetooth() {
        if (isBtScanning.value) return
        
        val bluetoothManager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if ((adapter == null) || (!adapter.isEnabled)) {
            isBtEnabled.value = false
            return
        }
        isBtEnabled.value = true

        isBtScanning.value = true
        discoveredBtDevices.value = emptyList()
        
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            lastBtError.value = "Bluetooth LE Scanner not available"
            isBtScanning.value = false
            return
        }
        
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = BleDevice(result.device.name, result.device.address, result.rssi)
                if (discoveredBtDevices.value.none { it.address == device.address }) {
                    discoveredBtDevices.value += device
                }
            }
        }
        bleScanCallback = callback

        viewModelScope.launch {
            scanner.startScan(callback)
            kotlinx.coroutines.delay(kotlin.time.Duration.parse("5s"))
            stopBtScan()
        }
    }

    /** 
     * Stops an active Bluetooth LE scan.
     */
    @SuppressLint("MissingPermission")
    private fun stopBtScan() {
        if (!isBtScanning.value) return
        val bluetoothManager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner
        bleScanCallback?.let { 
            scanner?.stopScan(it)
        }
        bleScanCallback = null
        isBtScanning.value = false
    }

    /** 
     * Connects to a robot using Bluetooth LE GATT.
     * @param device The BleDevice to connect to.
     */
    fun connectBle(device: BleDevice) {
        ws?.disconnect()
        bleClient?.disconnect()
        apiClient = null
        
        lastError.value = null
        connectionStatus.value = getApplication<Application>().getString(R.string.status_connecting)
        
        bleClient = HydraBleClient(
            context = getApplication(),
            deviceAddress = device.address,
            onStatus = { status ->
                connectionStatus.value = when (status) {
                    WsStatus.CONNECTING -> getApplication<Application>().getString(R.string.status_connecting)
                    WsStatus.CONNECTED -> getApplication<Application>().getString(R.string.status_connected)
                    WsStatus.DISCONNECTED -> getApplication<Application>().getString(R.string.status_disconnected)
                }
            },
            onSettings = { payload -> 
                try {
                    applyState(HydraState(org.json.JSONObject(payload)))
                } catch (e: Exception) {
                    lastError.value = e.message
                }
            },
        ) { message -> lastError.value = message }.also { it.connect() }
    }

    /** 
     * Disconnects the active Bluetooth LE client.
     */
    fun disconnectBle() {
        bleClient?.disconnect()
        bleClient = null
    }

    /** 
     * Internal helper to parse the port string safely.
     * @return The port number.
     */
    private fun portValue(): Int = port.value.toIntOrNull() ?: 3000

    /** 
     * Sets the target server details from discovery results and connects.
     * @param server The selected ServerInfo.
     */
    fun connectToDiscovered(server: ServerInfo) {
        ipAddress.value = server.host
        port.value = server.port.toString()
        connect()
    }

    override fun onCleared() {
        super.onCleared()
        ws?.disconnect()
        bleClient?.disconnect()
        stopBtScan()
    }
}
