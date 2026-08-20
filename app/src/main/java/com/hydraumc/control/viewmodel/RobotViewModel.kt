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
import org.json.JSONObject

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
        // Same validation connect() already applies - without it, an empty
        // host/port here fell through silently (port defaulting to 3000
        // rather than telling the user their input was missing), and an
        // invalid port never surfaced error_invalid_port the way connect()'s
        // own path does. Username/password are checked too: an empty
        // credential otherwise goes straight to the server as an empty
        // string instead of being caught client-side first.
        if (host.isEmpty() || portValue.isEmpty() || user.isBlank() || pass.isBlank()) {
            lastError.value = getApplication<Application>().getString(R.string.error_enter_ip_port)
            return
        }
        val portInt = portValue.toIntOrNull()
        if (portInt == null) {
            lastError.value = getApplication<Application>().getString(R.string.error_invalid_port, portValue)
            return
        }

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

    // Tracked so a reconnect (server switch, manual reconnect) can cancel
    // the PREVIOUS loop before starting a new one - without this, every
    // connect() -> startMetricsLoop() call left the old loop running
    // indefinitely: its own `while` condition only exits once
    // connectionStatus stops reading "Connected", but after a server
    // switch that string becomes true again for the NEW server almost
    // immediately, so the old loop (still closing over the OLD client)
    // never actually saw a false condition - it just kept polling a
    // server that's no longer the active one, racing the new loop to
    // overwrite the same shared `metrics` value.
    private var metricsJob: kotlinx.coroutines.Job? = null

    private fun startMetricsLoop(client: HydraApiClient) {
        metricsJob?.cancel()
        metricsJob = viewModelScope.launch {
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
                    // Merge the incoming delta into the existing state rather than
                    // replacing it outright - a WS push only ever carries what
                    // changed, not the full tree, so a plain replace would drop
                    // every field the payload didn't include.
                    state.merge(payload)
                    applyState(state)
                },
            ) { message -> 
                if (!isSwitchingServer) {
                    lastError.value = message 
                    if (message.contains("autorizada") || message.contains("Access denied")) {
                        isLoggedIn.value = false
                        connectionStatus.value = getApplication<Application>().getString(R.string.status_disconnected)
                        ws?.disconnect()
                    }
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

    // Every write in this app goes through the real atomic
    // POST /api/robot/:id/command (server.ts:210-298) instead of pushing the
    // full settings tree for a single jog tick: the server computes
    // affectedIds itself (self + combinedWith), persists to disk, AND
    // broadcasts a "delta" to every OTHER connected client on its own, so
    // this app doesn't also need to push the full tree over its own
    // WebSocket. Every command shares the same affectedIds computation below
    // (propagateToCombined), so enable/disable, play/pause/stop, and any
    // future command all propagate to a robot's own combinedWith siblings
    // the same way instead of each reimplementing that fan-out separately.
    // Keyed by `command`, not a single shared Job - a pending debounced
    // command (only setSpeed uses debounceMs today) must only be cancelled
    // by a NEW command of that same kind, not by an unrelated jog/valve/
    // pump/play tap that happens to land inside the debounce window. A
    // single shared Job here used to mean: drag the speed slider, then
    // immediately jog before the 300ms debounce fires, and the speed
    // change silently never reaches the server at all - the UI already
    // shows the new speed (the local mutation below is synchronous), so
    // nothing about the screen indicates the robot is still running at
    // the OLD speed.
    private val atomicSyncJobs = mutableMapOf<String, kotlinx.coroutines.Job?>()

    /**
     * Applies [command]/[params] to [robotId] (defaults to the globally
     * selected control robot; the Camera screen passes an explicit id since
     * the camera being browsed there isn't necessarily the robot selected
     * for jogging) - and, for commands that make sense combined, its
     * combinedWith siblings - locally for instant UI feedback, then sends it
     * to the server via the atomic endpoint. [propagateToCombined] is false
     * for per-axis/per-slot actions (jog, valve, pump, tool, speed, vision)
     * that only ever make sense for the one robot being controlled, true for
     * play/pause/stop/enable/disable, which are meant to act on a whole
     * combined group at once.
     */
    private fun sendAtomicCommand(
        command: String,
        params: JSONObject? = null,
        propagateToCombined: Boolean = false,
        debounceMs: Long = 0,
        explicitRobotId: Int? = null,
        localMutate: (RobotView) -> Unit,
    ) {
        val robotId = explicitRobotId ?: selectedRobotId.value ?: return
        val targetRobot = state.robotById(robotId) ?: run {
            lastError.value = getApplication<Application>().getString(R.string.error_robot_not_found)
            return
        }

        // Checked BEFORE the optimistic mutation below, not after (used to
        // be `val client = apiClient ?: return`, reached only once the UI
        // already showed the change as applied) - with no REST client at
        // all (BLE-only session: connectBle() sets apiClient = null and
        // this app has no BLE command path yet), every command used to
        // mutate the local/UI state and then silently discard itself,
        // so every button appeared to work while doing nothing whatsoever
        // to the real robot.
        val client = apiClient ?: run {
            lastError.value = getApplication<Application>().getString(R.string.error_not_connected)
            return
        }

        val affectedIds = mutableListOf(robotId)
        if (propagateToCombined) {
            targetRobot.raw.optJSONArray("combinedWith")?.let { arr ->
                for (i in 0 until arr.length()) affectedIds.add(arr.getInt(i))
            }
        }
        // Snapshot every affected robot's raw state as JSON text before
        // mutating, so a failed send can restore it - the mutation below is
        // optimistic (applied to the UI immediately, before the network
        // round-trip even starts), and until this snapshot existed a
        // failed POST (network error, server rejection, timeout - anything
        // that isn't the one already-handled 401 case) left the UI showing
        // the command as applied forever, with no way back to the real
        // last-known-good state short of a full reconnect.
        val snapshots = affectedIds.mapNotNull { id -> state.robotById(id)?.let { id to it.raw.toString() } }
        affectedIds.forEach { id -> state.robotById(id)?.let(localMutate) }
        applyState(state)

        val payload = JSONObject().put("command", command).apply { if (params != null) put("params", params) }

        fun rollback() {
            for ((id, json) in snapshots) {
                val raw = state.robotById(id)?.raw ?: continue
                val restored = org.json.JSONObject(json)
                val keys = restored.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    raw.put(key, restored.get(key))
                }
            }
            applyState(state)
        }

        val send: () -> Unit = {
            viewModelScope.launch {
                try {
                    client.postRobotCommand(robotId, payload)
                    lastError.value = null
                } catch (e: HydraApiException) {
                    logTelemetry("TX Error [$command]: ${e.message}")
                    if (e.message?.contains("401") == true) {
                        lastError.value = "Unauthorized: Session expired"
                        isLoggedIn.value = false
                        connectionStatus.value = getApplication<Application>().getString(R.string.status_disconnected)
                        ws?.disconnect()
                    } else {
                        lastError.value = getApplication<Application>().getString(R.string.error_command_failed, command)
                    }
                    rollback()
                } catch (e: Exception) {
                    // Was `catch (e: HydraApiException)` only - a plain
                    // timeout/SocketException from OkHttp isn't wrapped in
                    // HydraApiException, so it escaped uncaught, skipping
                    // both the error message AND the rollback below.
                    logTelemetry("TX Error [$command]: ${e.message}")
                    lastError.value = getApplication<Application>().getString(R.string.error_command_failed, command)
                    rollback()
                }
            }
        }

        atomicSyncJobs[command]?.cancel()
        if (debounceMs > 0) {
            atomicSyncJobs[command] = viewModelScope.launch {
                kotlinx.coroutines.delay(debounceMs)
                send()
            }
        } else {
            send()
        }
    }

    fun sendCommand(command: String) {
        when (command) {
            "enable" -> sendAtomicCommand(command, propagateToCombined = true) { it.setOnline(true) }
            "disable" -> sendAtomicCommand(command, propagateToCombined = true) { it.setOnline(false) }
            "play" -> sendAtomicCommand(command, propagateToCombined = true) { it.setPlaying(true) }
            "pause" -> sendAtomicCommand(command, propagateToCombined = true) { it.togglePaused() }
            "stop" -> sendAtomicCommand(command, propagateToCombined = true) { it.stop() }
            else -> lastError.value = getApplication<Application>().getString(R.string.error_unknown_command, command)
        }
    }

    fun jog(target: String, axis: String, amount: Double) {
        val params = JSONObject().put("axis", axis).put("amount", amount).put("target", target)
        sendAtomicCommand("jog", params) { r ->
            if (target == "robot") {
                r.setPosAxis(axis, r.posAxis(axis) + amount)
            } else if (target == "xytable") {
                r.setXyTableAxis(axis, r.xyTablePos.optDouble(axis, 0.0) + amount)
            }
        }
    }

    fun toggleValve(index: Int) {
        val targetRobot = selectedRobotId.value?.let { state.robotById(it) } ?: return
        val newState = !targetRobot.valves.optBoolean(index, false)
        val params = JSONObject().put("index", index).put("state", newState)
        sendAtomicCommand("valve", params) { it.setValve(index, newState) }
    }

    fun togglePump(index: Int) {
        val targetRobot = selectedRobotId.value?.let { state.robotById(it) } ?: return
        val newState = !targetRobot.pumps.optBoolean(index, false)
        val params = JSONObject().put("index", index).put("state", newState)
        sendAtomicCommand("pump", params) { it.setPump(index, newState) }
    }

    /** Debounced (300ms) - a dragged slider fires this many times a second. */
    fun setSpeed(speed: Double, acceleration: Double) {
        val params = JSONObject().put("speed", speed).put("acceleration", acceleration)
        sendAtomicCommand("speed", params, debounceMs = 300) { r ->
            r.setSpeed(speed)
            r.setAcceleration(acceleration)
        }
    }

    /** Toggles a robot's vision system on/off from the Camera screen (server.ts's own "vision" command). Takes an explicit robotId since the camera being browsed isn't necessarily the globally selected control robot. */
    fun setVisionEnabled(robotId: Int, enabled: Boolean) {
        val params = JSONObject().put("enabled", enabled)
        sendAtomicCommand("vision", params, explicitRobotId = robotId) { r -> r.raw.put("visionEnabled", enabled) }
    }

    fun changeTool(slot: Int) {
        val targetRobot = selectedRobotId.value?.let { state.robotById(it) } ?: return
        val tool = targetRobot.atcTools.find { it.slot == slot }?.tool ?: "None"
        mutateSelectedTool(tool)
    }

    fun mutateSelectedTool(toolName: String) {
        val params = JSONObject().put("tool", toolName)
        sendAtomicCommand("tool", params) { it.setTool(toolName) }
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
        metricsJob?.cancel()
        ws?.disconnect()
        bleClient?.disconnect()
        stopBtScan()
    }
}
