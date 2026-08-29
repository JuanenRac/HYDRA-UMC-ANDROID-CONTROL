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
import com.hydraumc.control.wear.WatchAssistantReply
import com.hydraumc.control.wear.WatchSystemStatus
import com.hydraumc.control.wear.WatchVoiceTurn
import com.hydraumc.control.util.NotificationHelper
import kotlinx.coroutines.launch
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.annotation.SuppressLint
import android.location.LocationManager
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
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
    // Read-only relay state for the future Android Wear Data Layer receiver.
    // It is deliberately separate from robot state: a voice reply must never
    // be mistaken for a physical command or applied optimistically.
    val latestWatchVoiceReply = mutableStateOf<WatchAssistantReply?>(null)
    val latestWatchSystemStatus = mutableStateOf<WatchSystemStatus?>(null)

    // Whether the app process is currently in the foreground. viewModelScope
    // (and the WebSocket it owns) deliberately keeps running while
    // backgrounded - real-time robot state needs to keep arriving live even
    // with the app not on screen - but polling (startMetricsLoop) doesn't
    // need to, and onCleared() alone never catches "just backgrounded": it
    // only fires when the ViewModel itself is destroyed (Activity
    // finishing), which backgrounding alone never triggers. Driven by the
    // appLifecycleObserver registered in init{} below.
    private var isAppInForeground = true

    private val appLifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                isAppInForeground = true
                val client = apiClient
                if ((client != null) && (metricsJob?.isActive != true) &&
                    (connectionStatus.value == getApplication<Application>().getString(R.string.status_connected))
                ) {
                    startMetricsLoop(client)
                }
            }
            Lifecycle.Event.ON_STOP -> {
                isAppInForeground = false
            }
            else -> {}
        }
    }

    // Reacts to the user toggling Bluetooth from outside the app (Quick
    // Settings, system Settings) instead of only refreshing isBtEnabled once
    // when SettingsScreen's Bluetooth tab happens to be composed - without
    // this, turning Bluetooth off while that screen is open left the Switch
    // showing "on" until the user navigated away and back.
    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshBtStatus()
        }
    }

    init {
        connectionStatus.value = application.getString(R.string.status_disconnected)
        NotificationHelper.createChannel(application)

        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)

        val btFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(btStateReceiver, btFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(btStateReceiver, btFilter)
        }

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
                // Mirrors login()'s own success path (isLoggedIn=true immediately
                // followed by connect()) - setting isLoggedIn alone used to swap
                // LoginScreen -> MainScreen (MainActivity gates purely on this
                // flag) with NO live connection behind it: apiClient above only
                // carries the saved token, nothing has opened a WebSocket or
                // synced state yet. Whether or not this resolves while the user
                // is mid-typing a fresh login on LoginScreen (this whole block
                // runs on a background coroutine with no ordering guarantee
                // relative to LoginScreen's composition), the resulting
                // "logged in" dashboard was otherwise dead until a manual
                // logout+login forced the same connect() the button already
                // triggers - so trigger it here too instead of leaving it to
                // the user to notice and work around.
                isLoggedIn.value = true
                // onInitialConnectFailed: a cached session whose server is
                // now unreachable/invalid (wrong ip/port, server moved,
                // different network) used to leave isLoggedIn true forever
                // - MainActivity gates purely on that flag, so the app
                // showed the full main screen with no real connection
                // behind it instead of bouncing back to LoginScreen where
                // the ip/port fields could actually be fixed. Only this
                // cached-session path opts in (see connect()'s own
                // parameter doc) - a manual reconnect/server-switch failure
                // elsewhere must not force a real, already-active session
                // to log out over a transient error.
                connect(onInitialConnectFailed = { isLoggedIn.value = false })
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

    /**
     * Entry point for a paired Watch transport. The transport itself is
     * hardware-dependent, but the authenticated Android -> Server -> Voice
     * UI relay is real and testable now. A reply only carries text/status;
     * it never invokes sendAtomicCommand or changes robot state.
     */
    fun relayWatchVoiceTurn(requestId: String, transcript: String, locale: String) {
        val client = apiClient ?: run {
            lastError.value = "Connect to HYDRA-UMC-SERVER before relaying a Watch voice request."
            return
        }
        viewModelScope.launch {
            try {
                val reply = client.postWatchVoiceTurn(WatchVoiceTurn(requestId, transcript, locale))
                latestWatchVoiceReply.value = reply
                logTelemetry("Watch voice reply received: ${reply.level}; confirmation=${reply.requiresConfirmation}")
            } catch (error: Exception) {
                lastError.value = "Watch voice relay failed: ${error.message}"
                logTelemetry("Watch voice relay failed")
            }
        }
    }

    /** Retrieves the safe health-card shape for the paired Wear transport. */
    fun refreshWatchSystemStatus() {
        val client = apiClient ?: return
        viewModelScope.launch {
            try {
                latestWatchSystemStatus.value = client.getWatchSystemStatus()
            } catch (error: Exception) {
                logTelemetry("Watch system-status refresh failed")
            }
        }
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

    // Guards against two connect() calls racing each other - e.g. the user
    // tapping a discovered server right as scanNetwork()'s own auto-connect
    // (its first discovered result) fires, or a rapid double-tap on Connect.
    // Without this, both coroutines run concurrently and can interleave
    // their writes to apiClient/state/robots.value, with whichever REST
    // response lands last "winning" regardless of which connect() call was
    // actually the user's most recent intent. Cancelling the previous job
    // stops it at its next suspension point (every HydraApiClient call is a
    // real suspend fun on Dispatchers.IO, so cancellation propagates instead
    // of the coroutine running to completion in the background).
    private var connectJob: kotlinx.coroutines.Job? = null

    /**
     * @param onInitialConnectFailed Called only when the very first REST
     *   sync of this call fails (host unreachable, wrong port, not a real
     *   HYDRA-UMC server, etc.) - used exclusively by the cached-session
     *   auto-login in `init` above to bounce back to LoginScreen instead of
     *   leaving `isLoggedIn` true with nothing real behind it (see that
     *   call site's own comment). Left null for every other caller (the
     *   Login button's own post-success connect(), manual reconnect,
     *   server switch) so a transient failure on an ALREADY-established
     *   session never forces a real logout - only the specific case this
     *   was reported for does.
     */
    fun connect(onInitialConnectFailed: (() -> Unit)? = null) {
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

        connectJob?.cancel()
        connectJob = viewModelScope.launch {
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
                onInitialConnectFailed?.invoke()
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

    // Debounce job for the offline-viewing disk cache write in applyState()
    // below - see that call site's own comment for why this exists.
    private var stateCacheJob: kotlinx.coroutines.Job? = null

    private fun startMetricsLoop(client: HydraApiClient) {
        metricsJob?.cancel()
        metricsJob = viewModelScope.launch {
            // isAppInForeground gates this too, not just connectionStatus -
            // without it, backgrounding the app (Home button, app switcher)
            // left this loop polling /api/system/metrics every 5s
            // indefinitely, since the WebSocket connection (and therefore
            // connectionStatus == "Connected") deliberately stays alive while
            // backgrounded. The appLifecycleObserver relaunches this loop via
            // ON_START once the app returns to the foreground.
            while(connectionStatus.value == getApplication<Application>().getString(R.string.status_connected) && isAppInForeground) {
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
                    // Full replace, not a merge - reaches this callback for
                    // "settings" (the first message on connect, and any full
                    // POST /api/settings write) and for a schema-1 "delta"
                    // (a client that hasn't declared schema 2 - not this
                    // app's own connection, see the ?remoteApiVersion=2 in
                    // HydraWebSocket's own openSocket()). A REAL schema-2
                    // delta (server.ts's own broadcastRobotDelta(), a real
                    // partial patch) is routed to onDelta below instead, not
                    // here - HydraWebSocket.handleMessage() splits the two
                    // apart before either callback fires. A prior version of
                    // THIS callback called state.merge(payload) instead,
                    // under the wrong assumption that "delta" always meant a
                    // partial diff, back when it never did -
                    // HydraState.mergeArrays() only ever APPENDED
                    // primitive-array fields (valves/pumps/combinedWith) as
                    // a result, so a robot removed from a combinedWith group
                    // kept receiving that group's play/pause/stop/enable/
                    // disable indefinitely, since its old id never actually
                    // left the merged array. Matches connectBle()'s own
                    // onSettings handling below, which was always a full
                    // replace (BLE has no delta path).
                    applyState(HydraState(payload))
                },
                onDelta = { msg -> applyRobotDelta(msg) },
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
            // Debounced, not written on every single call - applyState()
            // runs on every WS broadcast AND every local optimistic command
            // mutation (sendAtomicCommand calls it directly), so a robot
            // being actively jogged/controlled used to write the ENTIRE
            // current state tree to disk (DataStore's edit{} does a full
            // atomic file replace under the hood) on every single one of
            // those, unthrottled - real flash wear on the eMMC over a
            // device's lifetime of continuous use, for a cache that only
            // exists for offline viewing (StateCache's own doc comment) and
            // is already superseded by a full REST sync on the next
            // connect() anyway. A short quiet-period debounce (same
            // cancel-and-relaunch Job pattern as metricsJob/connectJob/
            // atomicSyncJobs elsewhere in this file) coalesces a burst of
            // rapid state changes into one write instead of one per change.
            stateCacheJob?.cancel()
            stateCacheJob = viewModelScope.launch {
                kotlinx.coroutines.delay(1000)
                stateCache.saveState(newState.toJson())
            }

            newState.allRobots.forEach { robot ->
                val oldRobot = oldState.robotById(robot.id)
                // Gated on isFinished too, not just the isPlaying:true->false
                // transition alone - that transition also happens on a plain
                // manual STOP (from this app, the browser, or SUITE), which
                // used to make every operator-initiated stop show a false
                // "completed successfully" notification. isFinished is only
                // ever set true by the browser client's own natural-completion
                // path (see RobotView.isFinished's own comment) and left
                // false by every play/pause/stop action, atomic or not - a
                // manual stop no longer trips this. Known residual gap: the
                // server's own atomic "play" command (server.ts) doesn't
                // reset isFinished, so a robot that finished a job once and
                // is then replayed+stopped from this app before the browser
                // client resets it can still show a stale true positive -
                // narrow enough (needs a specific prior-session state) not to
                // block fixing the common case, and the underlying gap is in
                // HYDRA-UMC-SERVER's server.ts, not this app.
                if ((oldRobot != null) && oldRobot.isPlaying && !robot.isPlaying && robot.isFinished) {
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
     * Applies one {controllerId, robotId, patch, cameraId?, cameraPatch?}
     * delta (server.ts's own broadcastRobotDelta()) in place onto state's
     * own raw JSONObject tree - RobotView already reads/writes through to
     * this same object (see model/HydraState.kt's own header comment), so
     * mutating it here and re-deriving `robots.value` via applyState()
     * below is the same pattern every localMutate call in
     * sendAtomicCommand() already uses.
     *
     * Validates the robot exists locally BEFORE touching anything: if it
     * doesn't (this mirror is stale, or missed the robot's own initial
     * full-tree load), the delta is discarded and a full GET /api/settings
     * reload is forced instead of ever creating a "ghost" robot from a
     * partial patch - DISEÑO_SYNC_DELTAS.txt section 5b mitigation (b),
     * non-optional. This app has no CameraView (cameras aren't modeled as
     * their own class here, only cameraPatch's raw JSON is merged) - a
     * cameraPatch is still applied to the underlying tree for correctness
     * even though nothing in this app currently reads it back out.
     */
    private fun applyRobotDelta(msg: JSONObject) {
        val controllerId = msg.optString("controllerId", "")
        val robotId = if (msg.has("robotId")) msg.optInt("robotId", Int.MIN_VALUE) else Int.MIN_VALUE
        val patch = msg.optJSONObject("patch")
        if (controllerId.isEmpty() || patch == null) return

        var targetRobotRaw: JSONObject? = null
        var targetCameraRaw: JSONObject? = null
        val controllersArr = state.raw.optJSONArray("controllers")
        if (controllersArr != null) {
            for (i in 0 until controllersArr.length()) {
                val c = controllersArr.optJSONObject(i) ?: continue
                if (c.optString("id", "") != controllerId) continue
                c.optJSONArray("robots")?.let { robotsArr ->
                    for (j in 0 until robotsArr.length()) {
                        val r = robotsArr.optJSONObject(j) ?: continue
                        if (r.optInt("id", Int.MIN_VALUE) == robotId) {
                            targetRobotRaw = r
                            break
                        }
                    }
                }
                if (msg.has("cameraId")) {
                    val cameraId = msg.optInt("cameraId", Int.MIN_VALUE)
                    c.optJSONArray("cameras")?.let { camerasArr ->
                        for (j in 0 until camerasArr.length()) {
                            val cam = camerasArr.optJSONObject(j) ?: continue
                            if (cam.optInt("id", Int.MIN_VALUE) == cameraId) {
                                targetCameraRaw = cam
                                break
                            }
                        }
                    }
                }
                break
            }
        }

        val robotRaw = targetRobotRaw
        if (robotRaw == null) {
            val client = apiClient ?: return
            viewModelScope.launch {
                try {
                    applyState(HydraState(client.getSettings()))
                } catch (_: Exception) {
                    // best-effort - the next broadcast or reconnect will retry
                }
            }
            return
        }
        val keys = patch.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            robotRaw.put(key, patch.get(key))
        }
        val cameraRaw = targetCameraRaw
        val cameraPatch = msg.optJSONObject("cameraPatch")
        if (cameraRaw != null && cameraPatch != null) {
            val cKeys = cameraPatch.keys()
            while (cKeys.hasNext()) {
                val key = cKeys.next()
                cameraRaw.put(key, cameraPatch.get(key))
            }
        }
        applyState(state)
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
                    // Success log for "$command" (play/pause/stop/enable/...) -
                    // previously only the FAILURE path logged anything
                    // (logTelemetry("TX Error [...]") below), so a command
                    // that reached the server with no visible effect (the
                    // still-open Pause/Stop investigation looked identical in the
                    // on-screen log to one that silently failed to send at
                    // all. This line is the missing half: confirms the POST
                    // actually left this app, so the next live repro can
                    // tell "never sent" apart from "sent, server/STUDIO
                    // didn't react" just from this screen's own log, no
                    // logcat needed for that first split.
                    logTelemetry("TX OK [$command] -> robot $robotId")
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
            // Pause is an explicit desired state, computed once from the
            // selected robot and applied identically to its combined group.
            // Toggling every member independently allowed a stale A1/A2 pair
            // to flip in opposite directions, while SERVER/STUDIO used a
            // different current member as their toggle source.
            "pause" -> {
                val robotId = selectedRobotId.value ?: return
                val paused = !(state.robotById(robotId)?.isPaused ?: false)
                val params = JSONObject().put("paused", paused)
                sendAtomicCommand(command, params, propagateToCombined = true) { it.setPaused(paused) }
            }
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
        sendAtomicCommand("vision", params, explicitRobotId = robotId) { r ->
            r.raw.put("visionEnabled", enabled)
            // Keep the legacy embedded camera mirror aligned with the
            // authoritative robot value. A1/A2 used to retain connected=true
            // here after vision was disabled, unlike A3-A8.
            r.raw.optJSONObject("camera")?.put("connected", enabled)
        }
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
                    // Discovery.kt runs the subnet HTTP scan and the mDNS listener
                    // concurrently and independently - the same real server routinely
                    // answers BOTH (its own LAN IP gets probed directly by the subnet
                    // scan, and it also gets found via _hydra._tcp mDNS moments
                    // later), so this collector sees it emitted twice with identical
                    // host/port. Same host+port identity check connect() already uses
                    // below when appending a manually-verified server - without it,
                    // the dropdown showed 2 entries for what was really 1 server.
                    if (discoveredServers.value.none { (it.host == server.host) && (it.port == server.port) }) {
                        discoveredServers.value += server
                    }
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

        // On API 24-30, BLE scan results are silently withheld system-wide
        // if Location Services (GPS) is off at the OS level, even with the
        // runtime permission already granted - startScan() itself reports
        // no error, it just never calls onScanResult(). API 31+ (S) dropped
        // this requirement once BLUETOOTH_SCAN(neverForLocation) replaced
        // the old location-permission-based BLE scan model, so this only
        // needs checking below that version.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val locationManager = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val locationEnabled = locationManager?.isLocationEnabled ?: false
            if (!locationEnabled) {
                lastBtError.value = "Location Services must be enabled for Bluetooth scanning on this Android version"
                return
            }
        }

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
        connectJob?.cancel()
        metricsJob?.cancel()
        stateCacheJob?.cancel()
        ws?.disconnect()
        bleClient?.disconnect()
        stopBtScan()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        try {
            getApplication<Application>().unregisterReceiver(btStateReceiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered (e.g. receiver never successfully bound) - harmless.
        }
    }
}
