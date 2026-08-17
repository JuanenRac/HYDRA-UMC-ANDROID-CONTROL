// =============================================================================
// HYDRA-UMC CONTROL - Primary ViewModel managing robot state and connectivity
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
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
 */
data class AtcTool(val slot: Int, val name: String)

/** 
 * Flat, display-friendly snapshot of one Job.
 */
data class JobState(val name: String)

/**
 * System metrics for industrial monitoring.
 */
data class SystemMetrics(
    val cpuLoad: Int,
    val memoryUsage: Int,
    val temp: Double,
    val uptime: Int
)

/** 
 * Flat, display-friendly snapshot of one RobotView.
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
 */
class RobotViewModel(application: Application) : AndroidViewModel(application) {
    val robots = mutableStateOf<List<RobotState>>(emptyList())
    val jobs = mutableStateOf<List<JobState>>(emptyList())
    val metrics = mutableStateOf<SystemMetrics?>(null)
    val selectedRobotId = mutableStateOf<Int?>(null)

    val ipAddress = mutableStateOf("192.168.1.100")
    val port = mutableStateOf("3000")
    val connectionStatus = mutableStateOf(application.getString(R.string.status_disconnected))

    val lastError = mutableStateOf<String?>(null)
    val lastBtError = mutableStateOf<String?>(null)

    val discoveredServers = mutableStateOf<List<ServerInfo>>(emptyList())
    val activeServer = mutableStateOf<ServerInfo?>(null)
    val isScanning = mutableStateOf(value = false)

    val discoveredBtDevices = mutableStateOf<List<BleDevice>>(emptyList())
    val telemetryLogs = mutableStateOf<List<String>>(emptyList())
    val isBtScanning = mutableStateOf(value = false)
    val isBtEnabled = mutableStateOf(value = false)
    private var bleScanCallback: ScanCallback? = null

    private var state = HydraState.empty()
    private var apiClient: HydraApiClient? = null
    private var ws: HydraWebSocket? = null
    private var bleClient: HydraBleClient? = null
    private val prefs = ConnectionPrefs(application)
    private val authPrefs = AuthPrefs(application)
    private val stateCache = StateCache(application)
    private var isSwitchingServer = false

    val isLoggedIn = mutableStateOf(value = false)
    val loginUsername = mutableStateOf(value = "")
    val loginPassword = mutableStateOf(value = "")
    val loginEmail = mutableStateOf(value = "")
    val loginRememberMe = mutableStateOf(value = false)
    val isBiometricEnabled = mutableStateOf(value = false)

    val selectedCameraId = mutableIntStateOf(1)

    init {
        connectionStatus.value = application.getString(R.string.status_disconnected)
        NotificationHelper.createChannel(application)

        viewModelScope.launch {
            prefs.load()?.let { (savedIp, savedPort) ->
                ipAddress.value = savedIp
                port.value = savedPort
            }
            
            val profile = authPrefs.loadAuth()
            loginUsername.value = profile.username
            loginPassword.value = profile.password
            loginEmail.value = profile.email
            loginRememberMe.value = profile.rememberMe
            isBiometricEnabled.value = profile.isBiometricEnabled
            if (profile.rememberMe && profile.isLoggedIn) {
                isLoggedIn.value = true
            }

            stateCache.loadState()?.let { cached ->
                applyState(HydraState(cached), isFromCache = true)
            }
        }
        
        val bluetoothManager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        isBtEnabled.value = bluetoothManager.adapter?.isEnabled ?: false
    }

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

    fun clearLogs() {
        telemetryLogs.value = emptyList()
    }

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
                
                viewModelScope.launch {
                    while(connectionStatus.value == getApplication<Application>().getString(R.string.status_connected)) {
                        try {
                            val m = client.getSystemMetrics()
                            metrics.value = SystemMetrics(
                                cpuLoad = m.optInt("cpu_load"),
                                memoryUsage = m.optInt("memory_usage"),
                                temp = m.optDouble("temp"),
                                uptime = m.optInt("uptime")
                            )
                        } catch (e: Exception) {
                            // ignore metric failures
                        }
                        kotlinx.coroutines.delay(5000)
                    }
                }
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
                        NotificationHelper.showSafetyNotification(getApplication())
                        getApplication<Application>().getString(R.string.status_connected)
                    }
                    WsStatus.DISCONNECTED -> {
                        logTelemetry("WebSocket DISCONNECTED")
                        NotificationHelper.hideSafetyNotification(getApplication())
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

    private fun applyState(newState: HydraState, isFromCache: Boolean = false) {
        val oldState = state
        state = newState
        robots.value = newState.allRobots.map { it.toDisplay() }
        jobs.value = newState.allJobs.map { JobState(it.name) }
        
        if (!isFromCache) {
            viewModelScope.launch {
                stateCache.saveState(newState.toJson())
            }
            
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

    private fun pushState(command: String? = null, params: org.json.JSONObject? = null) {
        val robotId = selectedRobotId.value
        
        // 1. Try Industrial Atomic API (REST) - Priority for commands
        if (command != null && robotId != null) {
            val client = apiClient ?: return
            viewModelScope.launch {
                try {
                    val cmdPayload = org.json.JSONObject()
                        .put("command", command)
                        .put("params", params ?: org.json.JSONObject())
                    
                    client.postRobotCommand(robotId, cmdPayload)
                    logTelemetry("TX [REST]: Atomic Command '$command' sent")
                    lastError.value = null
                } catch (e: HydraApiException) {
                    logTelemetry("TX Error [REST]: ${e.message}")
                    lastError.value = e.message
                }
            }
            return
        }
        
        // 2. Try BLE
        val payload = state.toJson()
        val sentOverBle = bleClient?.send(payload.toString()) ?: false
        if (sentOverBle) {
            logTelemetry("TX [BLE]: Payload sent")
            return
        }
        
        // 3. Try WebSocket (Real-time Full Sync)
        val sentOverWs = ws?.send(payload) ?: false
        if (sentOverWs) {
            logTelemetry("TX [WS]: State synchronized")
            return
        }
        
        // 4. Try Full State via REST as fallback
        val client = apiClient ?: return
        viewModelScope.launch {
            try {
                client.postSettings(payload)
                logTelemetry("TX [REST]: Full state updated")
                lastError.value = null
            } catch (e: HydraApiException) {
                logTelemetry("TX Error [REST]: ${e.message}")
                lastError.value = e.message
            }
        }
    }

    private fun mutateSelected(mutate: (RobotView) -> Unit) {
        val robotId = selectedRobotId.value ?: return
        val robotView = state.robotById(robotId) ?: run {
            lastError.value = getApplication<Application>().getString(R.string.error_robot_not_found)
            return
        }
        mutate(robotView)
        pushState()
    }

    fun sendCommand(command: String) {
        when (command) {
            "enable" -> mutateSelected { it.setOnline(value = true) }
            "disable" -> mutateSelected { it.setOnline(value = false) }
            "play", "pause", "stop" -> {
                // Send as Atomic Command, let server broadcast state back
                pushState(command)
            }
            else -> lastError.value = getApplication<Application>().getString(R.string.error_unknown_command, command)
        }
    }

    fun jog(target: String, axis: String, amount: Double) {
        val robotId = selectedRobotId.value ?: return
        
        // Send as Atomic Command, let server broadcast position back
        val params = org.json.JSONObject().put("axis", axis).put("amount", amount)
        pushState("jog", params)
    }

    fun toggleValve(index: Int) {
        val params = org.json.JSONObject().put("index", index).put("state", true) // server toggles
        pushState("valve", params)
    }

    fun togglePump(index: Int) {
        val params = org.json.JSONObject().put("index", index).put("state", true) // server toggles
        pushState("pump", params)
    }

    fun setSpeed(speed: Double, acceleration: Double) {
        val params = org.json.JSONObject().put("speed", speed).put("acceleration", acceleration)
        pushState("speed", params)
    }

    fun changeTool(slot: Int) {
        val params = org.json.JSONObject().put("slot", slot)
        pushState("tool", params) // assuming server handles slot to tool mapping
    }

    fun mutateSelectedTool(toolName: String) {
        val params = org.json.JSONObject().put("tool", toolName)
        pushState("tool", params)
    }

    fun scanNetwork() {
        if (isScanning.value) return
        isScanning.value = true
        discoveredServers.value = emptyList()
        viewModelScope.launch {
            try {
                scanSubnets(getApplication(), HydraApiClient.sharedHttpClient, portValue()).collect { server ->
                    discoveredServers.value += server
                    if (connectionStatus.value == getApplication<Application>().getString(R.string.status_disconnected) && discoveredServers.value.size == 1) {
                        logTelemetry("Auto-connecting to first discovered server: ${server.displayName}")
                        connectToDiscovered(server)
                    }
                }
            } finally {
                isScanning.value = false
            }
        }
    }

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

    fun disconnectBle() {
        bleClient?.disconnect()
        bleClient = null
    }

    private fun portValue(): Int = port.value.toIntOrNull() ?: 3000

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
