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
    val activeStep: Int,
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
    val j1: Double,
    val j2: Double,
    val j3: Double,
    val j4: Double,
    val j5: Double,
    val j6: Double,
    val endstops: Map<String, Boolean>,
    val valves: List<Boolean>,
    val pumps: List<Boolean>,
    val recordedPointsCount: Int
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
    activeStep = playbackState.optInt("activeStep", -1),
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
    j1 = joints.optDouble("j1", 0.0),
    j2 = joints.optDouble("j2", 0.0),
    j3 = joints.optDouble("j3", 0.0),
    j4 = joints.optDouble("j4", 0.0),
    j5 = joints.optDouble("j5", 0.0),
    j6 = joints.optDouble("j6", 0.0),
    endstops = mutableMapOf<String, Boolean>().apply {
        val obj = raw.optJSONObject("endstops")
        if (obj != null) {
            listOf("x1", "x2", "y1", "y2", "z0").forEach { axis ->
                put(axis, obj.optBoolean(axis, false))
            }
        }
    },
    valves = mutableListOf<Boolean>().apply {
        val arr = raw.optJSONArray("valves")
        if (arr != null) {
            for (i in 0 until arr.length()) add(arr.getBoolean(i))
        } else {
            add(false); add(false)
        }
    },
    pumps = mutableListOf<Boolean>().apply {
        val arr = raw.optJSONArray("pumps")
        if (arr != null) {
            for (i in 0 until arr.length()) add(arr.getBoolean(i))
        } else {
            add(false); add(false)
        }
    },
    recordedPointsCount = raw.optJSONArray("recordedPoints")?.length() ?: 0
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

    var state = HydraState.empty()
    var apiClient: HydraApiClient? = null
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
            
            if (profile.token.isNotEmpty()) {
                val client = HydraApiClient(ipAddress.value, port.value.toIntOrNull() ?: 3000)
                client.authToken = profile.token
                apiClient = client
            }

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
        val host = ipAddress.value.trim()
        val portValue = port.value.trim()
        val portInt = portValue.toIntOrNull() ?: 3000
        
        val client = HydraApiClient(host, portInt)
        apiClient = client
        
        viewModelScope.launch {
            try {
                logTelemetry("Logging in to $host:$portInt...")
                val response = client.login(user, pass)
                if (response.optBoolean("success")) {
                    val token = response.optString("token")
                    client.authToken = token
                    isLoggedIn.value = true
                    loginUsername.value = user
                    loginPassword.value = pass
                    loginRememberMe.value = remember
                    
                    authPrefs.saveAuth(
                        com.hydraumc.control.network.UserProfile(
                            username = user,
                            password = pass,
                            email = loginEmail.value,
                            rememberMe = remember,
                            isLoggedIn = true,
                            token = token
                        ),
                    )
                    logTelemetry("Login successful")
                    connect() // Proceed to full sync
                } else {
                    lastError.value = "Login failed: Server rejected credentials"
                }
            } catch (e: Exception) {
                logTelemetry("Login error: ${e.message}")
                lastError.value = e.message
            }
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

        viewModelScope.launch {
            // Ensure token is carried over or reloaded from persistence
            val token = apiClient?.authToken ?: authPrefs.loadAuth().token
            val client = HydraApiClient(host, portInt)
            client.authToken = token
            apiClient = client

            prefs.save(host, portValue)

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
                
                startMetricsLoop(client)
            } catch (e: HydraApiException) {
                logTelemetry("REST Sync Error: ${e.message}")
                lastError.value = e.message
            } finally {
                isSwitchingServer = false
            }

            setupWebSocket(host, portInt)
        }
    }

    private fun startMetricsLoop(client: HydraApiClient) {
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
                } catch (_: Exception) { }
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    private fun setupWebSocket(host: String, port: Int) {
        viewModelScope.launch {
            val token = authPrefs.loadAuth().token
            ws = HydraWebSocket(
                host = host,
                port = port,
                token = if (token.isNotEmpty()) token else null,
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
                onSettings = { payload -> 
                    // CRITICAL FIX: Merge delta instead of replacing whole state
                    state.merge(payload)
                    applyState(state) 
                },
            ) { message -> 
                if (!isSwitchingServer) {
                    lastError.value = message 
                }
            }.also { it.connect() }
        }
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

    private fun pushState(command: String? = null) {
        val robotId = selectedRobotId.value
        
        // 1. Instant local feedback
        if (command != null && robotId != null) {
            val targetRobot = state.robotById(robotId)
            if (targetRobot != null) {
                val affectedIds = mutableListOf(robotId)
                val combinedArr = targetRobot.raw.optJSONArray("combinedWith")
                if (combinedArr != null) {
                    for (i in 0 until combinedArr.length()) affectedIds.add(combinedArr.getInt(i))
                }
                
                affectedIds.forEach { id ->
                    state.robotById(id)?.let { r ->
                        when (command) {
                            "play" -> r.setPlaying(true)
                            "stop" -> r.stop()
                            "pause" -> r.togglePaused()
                            "enable" -> r.setOnline(true)
                            "disable" -> r.setOnline(false)
                        }
                    }
                }
                applyState(state) // Refresh UI instantly
            }
        }

        val client = apiClient ?: return
        val payload = state.toJson()

        // 2. Dual-Layer Sync Strategy
        // We always perform a Full Sync via REST to ensure the server disk matches.
        viewModelScope.launch {
            try {
                client.postSettings(payload)
                logTelemetry("TX [REST]: Full State Sync")
                lastError.value = null
            } catch (e: HydraApiException) {
                logTelemetry("TX Error [REST Sync]: ${e.message}")
                if (e.message?.contains("401") == true) lastError.value = "Unauthorized: Session expired"
            }
        }

        // Parallel high-speed WebSocket broadcast
        ws?.send(payload)
    }

    private fun mutateSelected(mutate: (RobotView) -> Unit) {
        val robotId = selectedRobotId.value ?: return
        val robotView = state.robotById(robotId) ?: run {
            lastError.value = getApplication<Application>().getString(R.string.error_robot_not_found)
            return
        }
        mutate(robotView)
        applyState(state) // Instant UI feedback
        pushState()
    }

    fun sendCommand(command: String) {
        when (command) {
            "enable" -> mutateSelected { it.setOnline(value = true) }
            "disable" -> mutateSelected { it.setOnline(value = false) }
            "play", "pause", "stop" -> {
                // Sincronización total agresiva para estas acciones críticas
                pushState(command)
            }
            else -> lastError.value = getApplication<Application>().getString(R.string.error_unknown_command, command)
        }
    }

    fun jog(target: String, axis: String, amount: Double) {
        mutateSelected { r ->
            if (target == "robot") {
                r.setPosAxis(axis, r.posAxis(axis) + amount)
            } else if (target == "xytable") {
                r.setXyTableAxis(axis, (r.xyTablePos.optDouble(axis, 0.0) + amount))
            }
        }
    }

    fun toggleValve(index: Int) {
        mutateSelected { r ->
            val current = r.valves.optBoolean(index, false)
            r.setValve(index, !current)
        }
    }

    fun togglePump(index: Int) {
        mutateSelected { r ->
            val current = r.pumps.optBoolean(index, false)
            r.setPump(index, !current)
        }
    }

    fun setSpeed(speed: Double, acceleration: Double) {
        mutateSelected { r ->
            r.setSpeed(speed)
            r.setAcceleration(acceleration)
        }
    }

    fun changeTool(slot: Int) {
        // assuming server handles slot to tool mapping, but we can do it locally too
        mutateSelected { r ->
            val tool = r.atcTools.find { it.slot == slot }?.tool ?: "None"
            r.setTool(tool)
        }
    }

    fun mutateSelectedTool(toolName: String) {
        mutateSelected { it.setTool(toolName) }
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
