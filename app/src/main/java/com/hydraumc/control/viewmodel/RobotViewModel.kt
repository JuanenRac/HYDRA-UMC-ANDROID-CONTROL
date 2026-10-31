// =============================================================================
// HYDRA-UMC CONTROL - Primary ViewModel managing robot state and connectivity
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.util.optStr
import android.app.Application
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hydraumc.control.model.BleDevice
import com.hydraumc.control.model.HydraState
import com.hydraumc.control.model.JOINT_NAMES
import com.hydraumc.control.model.RobotView
import com.hydraumc.control.model.ServerInfo
import com.hydraumc.control.kinematics.PAROL6_JOINT_LIMITS_DEG
import com.hydraumc.control.kinematics.parol6CartesianToJointsOrNull
import com.hydraumc.control.R
import com.hydraumc.control.network.AuthPrefs
import com.hydraumc.control.network.ClaimInfo
import com.hydraumc.control.network.ClaimStanding
import com.hydraumc.control.network.AuditRead
import com.hydraumc.control.network.AuditRecord
import com.hydraumc.control.network.ChainCheck
import com.hydraumc.control.network.ConditionalMode
import com.hydraumc.control.network.ConnectionClient
import com.hydraumc.control.network.ConnectionEvent
import com.hydraumc.control.network.ConnectionHistory
import com.hydraumc.control.network.ConnectionsFailure
import com.hydraumc.control.network.ServerOptionsFailure
import com.hydraumc.control.network.ServerSecurity
import com.hydraumc.control.network.ServerSecurityOptions
import com.hydraumc.control.network.ServerSecurityValues
import com.hydraumc.control.network.RevisionTracker
import com.hydraumc.control.network.isUnknownCommand
import com.hydraumc.control.network.revisionOf
import com.hydraumc.control.network.tableAsJogs
import com.hydraumc.control.network.tableLimitExceeded
import com.hydraumc.control.network.tableParams
import com.hydraumc.control.network.RobotSafety
import com.hydraumc.control.network.SessionController
import com.hydraumc.control.network.claimStanding
import com.hydraumc.control.network.jwtRole
import com.hydraumc.control.network.jogLimitExceeded
import com.hydraumc.control.network.ConnectionPrefs
import com.hydraumc.control.network.HydraApiClient
import com.hydraumc.control.network.HydraApiException
import com.hydraumc.control.network.HydraWebSocket
import com.hydraumc.control.network.BleContract
import com.hydraumc.control.network.BleDeviceList
import com.hydraumc.control.network.BleError
import com.hydraumc.control.network.BleErrorKind
import com.hydraumc.control.network.BlePermissions
import com.hydraumc.control.network.HydraBleClient
import com.hydraumc.control.network.TlsPolicy
import com.hydraumc.control.network.TlsPrefs
import com.hydraumc.control.network.TlsProblem
import com.hydraumc.control.network.TlsSettings
import com.hydraumc.control.network.TlsSettingsStore
import com.hydraumc.control.util.bleErrorMessage
import com.hydraumc.control.network.StateCache
import com.hydraumc.control.network.StopAllResult
import com.hydraumc.control.network.StopAllRobot
import com.hydraumc.control.network.StopAllStatus
import com.hydraumc.control.network.WsStatus
import com.hydraumc.control.network.scanSubnets
import com.hydraumc.control.wear.WatchAssistantReply
import com.hydraumc.control.wear.WatchSystemStatus
import com.hydraumc.control.wear.WatchVoiceTurn
import com.hydraumc.control.util.NotificationHelper
import com.hydraumc.control.util.OptimisticRollback
import com.hydraumc.control.util.StaleData
import com.hydraumc.control.util.reuseUnchangedRows
import kotlinx.coroutines.launch
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
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
 * One ecosystem project's own self-description, as GET /api/ecosystem/status
 * reports it - see server.ts's own getEcosystemStatus() for what this scans
 * (sibling repos' own hydra-umc.project.json manifests on the same machine
 * the server runs from). [maturity] is a static claim from that manifest;
 * [live] is the real thing - non-null only for a project whose manifest
 * opts in with a `service` object, and only then is it a genuine, just-now
 * TCP/HTTP probe result (true/false), never inferred from [maturity].
 */
data class EcosystemProject(
    val name: String,
    val role: String?,
    val stack: String?,
    val maturity: String?,
    val family: String?,
    val version: String?,
    val deploymentTarget: String?,
    val servicePort: Int?,
    val live: Boolean?,
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
    // False while the robot is in safe mode (disabled): it refuses everything but STOP.
    val isEnabled: Boolean = true,
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
    val recordedPointsCount: Int,
    // Real, server-synced jog step (server.ts's own 'jogStep' atomic
    // command) - was a local `var jogStep by remember {...}` in
    // ThreeDScreen.kt/`stepSize` in ControlScreen.kt, so the step shown/
    // used here never matched STUDIO's own. Falls back to 1.0 for any
    // robot that hasn't had this field set yet (a fresh settings.json, or
    // one saved before this field existed).
    val jogStep: Double
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
    isEnabled = isEnabled,
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
    recordedPointsCount = raw.optJSONArray("recordedPoints")?.length() ?: 0,
    jogStep = raw.optDouble("jogStep", 1.0)
)

/** How many lines of the history of sign-ins the screen asks for. */
private const val CONNECTION_EVENTS_SHOWN = 30

/** Why the audit record could not be read, for the sentence the screen shows. */
enum class AuditFailure { FORBIDDEN, UNAVAILABLE, SESSION, NETWORK, OTHER }

/**
 * Shared ViewModel responsible for robot orchestration, networking, and UI state management.
 */
class RobotViewModel(application: Application) : AndroidViewModel(application) {
    val robots = mutableStateOf<List<RobotState>>(emptyList())
    val jobs = mutableStateOf<List<JobState>>(emptyList())
    val metrics = mutableStateOf<SystemMetrics?>(null)
    val ecosystemProjects = mutableStateOf<List<EcosystemProject>>(emptyList())
    val ecosystemAvailable = mutableStateOf(value = false)
    val isLoadingEcosystemStatus = mutableStateOf(value = false)
    val selectedRobotId = mutableStateOf<Int?>(null)

    // Reservations and safety limits of the server (contract v2, block 1). The controller id is
    // sent with every claim, release, jog-arm and command, so a reservation belongs to this
    // installation and not to the whole account.
    /** Every active reservation, as of the last refresh (GET /api/claims). */
    val claims = mutableStateOf<List<ClaimInfo>>(emptyList())
    /** The limits of each robot model the server told this app about, by robot id. */
    val safetyByRobot = mutableStateOf<Map<Int, RobotSafety>>(emptyMap())
    /** Until when (this phone clock, epoch ms) the jog of each robot is armed from this app. */
    val jogArmedUntilMs = mutableStateOf<Map<Int, Long>>(emptyMap())
    /** True while a reservation or arming request is on the wire. */
    val accessBusy = mutableStateOf(false)

    // Conditional writes (contract v2, block 2): every write carries the revision of the robot this app last saw
    // (If-Match), and the server refuses it with 412 when the robot changed in between.
    /** Which writes are checked (chosen in the list of reservations); the standard mode by default. */
    val conditionalMode = mutableStateOf(ConditionalMode.STANDARD)
    @Volatile private var conditionalModeTouched = false

    /** Whether PLAY asks first (see [com.hydraumc.control.util.PlayConfirmation]); on until the person turns it off. */
    val confirmPlay = mutableStateOf(true)
    @Volatile private var confirmPlayTouched = false
    /** The last revision seen of each robot. */
    val revisions = RevisionTracker { conditionalMode.value }
    /** Whether the server was last seen to understand the `table` command (true until it says otherwise). */
    @Volatile private var tableCommandSupported = true
    /** When the server last pushed the reservations (this phone clock, epoch ms); 0 before the first push. */
    @Volatile var claimsPushedAtMs: Long = 0L
        private set

    // The audit record (contract v2, block 4), for administrators.
    val auditRead = mutableStateOf<AuditRead?>(null)
    /** The chain check of the lines in [auditRead] (only after the operator asked to verify); null before. */
    val auditChain = mutableStateOf<ChainCheck?>(null)
    val auditBusy = mutableStateOf(false)
    val auditFailure = mutableStateOf<AuditFailure?>(null)

    // Who connects to the server (the list of clients and the latest sign-ins), for every signed-in account.
    val connectionClients = mutableStateOf<List<ConnectionClient>?>(null)
    val connectionEvents = mutableStateOf<List<ConnectionEvent>?>(null)
    val connectionsBusy = mutableStateOf(false)
    val connectionsFailure = mutableStateOf<ConnectionsFailure?>(null)

    // The access options of the server (administrators only).
    val serverOptions = mutableStateOf<ServerSecurityOptions?>(null)
    val serverOptionsBusy = mutableStateOf(false)
    val serverOptionsFailure = mutableStateOf<ServerOptionsFailure?>(null)
    val serverOptionsSaved = mutableStateOf(false)
    val controllerId: String get() = SessionController.id

    val ipAddress = mutableStateOf("192.168.1.100")
    val port = mutableStateOf("3000")
    val connectionState = mutableStateOf(WsStatus.DISCONNECTED)

    val lastError = mutableStateOf<String?>(null)
    /** What the global STOP is doing or did; null before the first one. */
    val stopAllStatus = mutableStateOf<StopAllStatus?>(null)
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
    /** True once a state from the server (not the offline cache) has been applied in this process. */
    val stateIsLive = mutableStateOf(false)
    /** When the robot data on screen was last refreshed (epoch ms): the cache's save time until a live state arrives. */
    val stateAtMs = mutableStateOf<Long?>(null)

    /** True while the robots shown come from the offline copy or the link to the server is down. */
    val isShowingStaleData: Boolean
        get() = StaleData.isStale(
            connected = connectionState.value == WsStatus.CONNECTED,
            hasRobots = robots.value.isNotEmpty(),
        )
    var apiClient: HydraApiClient? = null
    // Real, Compose-observable mirror of apiClient?.authToken - apiClient
    // itself is a plain var and HydraApiClient.authToken is a plain var on
    // it too, so reassigning either (auto-login from a saved profile,
    // interactive login, or a host/port reconnect - all 3 real call sites
    // below) never triggers recomposition on its own. Any @Composable that
    // needs the CURRENT token (ThreeDScreen.kt building its embedded
    // WebView URL is the real one - a stale token there silently points
    // the embedded viewport at a dead/wrong session, which looks exactly
    // like "the 3D view never updates" even though the native app's own
    // REST/WS calls, which read apiClient directly rather than through
    // Compose state, keep working fine) must read THIS instead of
    // reaching into apiClient?.authToken directly.
    val authTokenState = mutableStateOf<String?>(null)
    private var ws: HydraWebSocket? = null
    private var bleClient: HydraBleClient? = null
    private val prefs = ConnectionPrefs(application)
    private val tlsPrefs = TlsPrefs(application)

    /** The TLS choices of the user (Settings > Security); the clients read them when they are created. */
    val tlsSettings = mutableStateOf(TlsSettings())
    /** Result of the last save of the TLS settings (saved, or what is wrong), in the language of the phone. */
    val tlsSettingsMessage = mutableStateOf<String?>(null)
    /** The host of the current session when it is not encrypted and not on the local network; null otherwise. The app shows a warning. */
    val plainRemoteHost = mutableStateOf<String?>(null)
    private val authPrefs = AuthPrefs(application)
    private val notificationPrefs = com.hydraumc.control.util.NotificationPrefs(application)
    private val stateCache = StateCache(application)
    private var isSwitchingServer = false

    // At most one session renewal runs at a time: a rejected token shows up
    // twice (the server's error message, then the 1008 close) and in every
    // command that is in flight, and each used to start its own login.
    private var reloginJob: kotlinx.coroutines.Job? = null

    // Renewals the app may do by itself are rationed (see RenewalBudget): past that it goes back to the sign-in instead of looping.
    private val renewalBudget = com.hydraumc.control.util.RenewalBudget()

    // The password saved with "remember me" was refused by the server once: it is not tried again until the person signs in by hand.
    private var savedCredentialsRefused = false

    // Jog commands currently on the wire, per robot. A press-and-hold repeats
    // every 150 ms; on a slow link the POSTs used to pile up and arrive after
    // the button was released (or after a stop). While one is in flight the
    // next tick is dropped instead of queued.
    private val jogsInFlight = mutableMapOf<Int, Int>()

    val isLoggedIn = mutableStateOf(value = false)
    // Real, live-reproduced bug this exists to fix: MainActivity's splash
    // screen dismisses on a FIXED timer (5s+2.5s, unrelated to this class's
    // own init{} below), so a slow authPrefs.loadAuth() + cached-session
    // check could still be running when LoginScreen first appears - the
    // user then sees a real login form, starts typing fresh credentials,
    // and the cached-session coroutine finishes moments later and flips
    // isLoggedIn straight to true underneath them (reusing the LAST
    // session's credentials, not whatever they just typed), reading
    // exactly like "typed the username, got kicked to the dashboard before
    // ever entering a password". Starts false; MainActivity now keeps the
    // splash up until this is true too, not just its own timer - see the
    // comment at the end of init{} below for exactly where this flips.
    val authCheckComplete = mutableStateOf(value = false)
    val loginUsername = mutableStateOf(value = "")
    val loginPassword = mutableStateOf(value = "")
    val loginEmail = mutableStateOf(value = "")
    val loginRememberMe = mutableStateOf(value = false)
    val isBiometricEnabled = mutableStateOf(value = false)
    // In-app notifications toggle - see NotificationPrefs.kt's own header
    // comment for why this exists alongside Android's own per-app system
    // toggle. Defaults to true (this app's behavior before this preference
    // existed); loaded for real in init{} below and updated via
    // setNotificationsEnabled().
    val notificationsEnabled = mutableStateOf(value = true)

    val selectedCameraId = mutableIntStateOf(1)
    // Read-only state for this app's own voice-assistant button
    // (VoiceAssistantDialog) - deliberately separate from robot state: a
    // voice reply must never be mistaken for a physical command or applied
    // optimistically. Also serves refreshWatchSystemStatus() below, kept
    // for the real Wear Data Layer relay (WatchVoiceRelayService).
    val latestVoiceAssistantReply = mutableStateOf<WatchAssistantReply?>(null)
    val voiceAssistantBusy = mutableStateOf(false)
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
                    (connectionState.value == WsStatus.CONNECTED)
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
        connectionState.value = WsStatus.DISCONNECTED
        NotificationHelper.createChannel(application)

        viewModelScope.launch {
            notificationsEnabled.value = notificationPrefs.isEnabled()
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)

        val btFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(btStateReceiver, btFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(btStateReceiver, btFilter)
        }

        // On the IO dispatcher: nothing here needs the main thread, and a test (or an app) that ends
        // first must not find this coroutine resuming on a main dispatcher that is already gone.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // The controller id of this installation: made once, kept, and adopted here.
            val storedControllerId = prefs.loadControllerId()
            if (SessionController.isValid(storedControllerId)) SessionController.adopt(storedControllerId)
            else prefs.saveControllerId(SessionController.id)
            // Unless the person already chose one while this was reading.
            val saved = prefs.loadConditionalMode()
            if (!conditionalModeTouched) conditionalMode.value = saved
            val askBeforePlay = prefs.loadConfirmPlay()
            if (!confirmPlayTouched) confirmPlay.value = askBeforePlay
        }

        viewModelScope.launch {
            // Before anything connects: the clients read these choices when they are created.
            tlsPrefs.load().also {
                TlsSettingsStore.current = it
                tlsSettings.value = it
            }
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
                client.refreshToken = profile.refreshToken.ifEmpty { null }
                apiClient = client
                authTokenState.value = profile.token
            }

            if (profile.rememberMe && profile.isLoggedIn) {
                // Real bug, live-reproduced, in what USED to be here:
                // isLoggedIn was set true immediately (before connect() had
                // confirmed anything), so if the cached ip/port turned out
                // unreachable, MainActivity showed the full Dashboard -
                // empty, since nothing had synced yet, reading as a
                // stuck/black screen against this app's dark theme - for
                // however long HydraApiClient's own connectTimeout (5s) +
                // the eventual onInitialConnectFailed round-trip took,
                // BEFORE finally bouncing back to LoginScreen. isLoggedIn
                // now only flips true once the connection is actually
                // confirmed (onInitialConnectSucceeded, right after a real
                // settings sync - see connect()'s own success path) - while
                // that's pending, MainActivity correctly shows LoginScreen
                // instead (fields already pre-filled from `profile` above),
                // not a misleading empty Dashboard. A normal, reachable
                // server still resolves this near-instantly (LAN latency,
                // not the 5s timeout), so the common case isn't slowed
                // down - only the "server unreachable" case now shows an
                // honest, usable login form the whole time instead of
                // something that looks broken.
                connect(
                    onInitialConnectSucceeded = { isLoggedIn.value = true },
                    // A cached session whose server is now unreachable/
                    // invalid (wrong ip/port, server moved, different
                    // network) also persists the correction
                    // (authPrefs.saveAuth with isLoggedIn=false), not just
                    // leaving isLoggedIn at its untouched `false` default -
                    // without this, profile.isLoggedIn on disk stayed true
                    // forever, so the very next cold start read the same
                    // stale value and repeated the same failed reconnect
                    // attempt on every single launch for as long as the
                    // cached ip/port stayed unreachable. username/password/
                    // rememberMe/token are kept as-is - only whether a
                    // session should be auto-resumed on the NEXT cold start
                    // changes.
                    onInitialConnectFailed = {
                        viewModelScope.launch {
                            authPrefs.saveAuth(profile.copy(isLoggedIn = false))
                        }
                    },
                )
            }

            // isLoggedIn.value now holds its correct resting value either
            // way (still `false` while a cached-session reconnect above is
            // pending/failed, or flipped true the moment it's confirmed) -
            // MainActivity can safely decide LoginScreen vs MainScreen from
            // here on without it changing out from under whichever one it
            // already picked based on a value that later turns out wrong.
            authCheckComplete.value = true

            stateCache.loadState()?.let { cached ->
                stateAtMs.value = stateCache.loadSavedAtMs()
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
                    val token = response.optStr("token")
                    client.authToken = token
                    client.refreshToken = response.optStr("refreshToken").ifEmpty { null }
                    authTokenState.value = token
                    isLoggedIn.value = true
                    renewalBudget.reset()
                    savedCredentialsRefused = false
                    loginUsername.value = user
                    loginPassword.value = pass
                    loginRememberMe.value = remember
                    
                    // The password is kept on the device only when the user asked
                    // for it ("remember me", or the fingerprint sign-in, which
                    // needs it); the refresh token renews the session without it.
                    authPrefs.saveAuth(
                        com.hydraumc.control.network.UserProfile(
                            username = user,
                            password = if (remember || isBiometricEnabled.value) pass else "",
                            email = loginEmail.value,
                            rememberMe = remember,
                            isLoggedIn = true,
                            isBiometricEnabled = isBiometricEnabled.value,
                            token = token,
                            refreshToken = client.refreshToken ?: "",
                        ),
                    )
                    logTelemetry("Login successful")
                    connect() // Proceed to full sync
                } else {
                    lastError.value = "Login failed: Server rejected credentials"
                }
            } catch (e: Exception) {
                logTelemetry("Login error: ${e.message}")
                lastError.value = (e as? HydraApiException)?.serverMessage ?: e.message
            }
        }
    }

    fun saveUserProfile(user: String, pass: String, email: String, biometric: Boolean = isBiometricEnabled.value) {
        loginUsername.value = user
        loginPassword.value = pass
        loginEmail.value = email
        isBiometricEnabled.value = biometric
        viewModelScope.launch {
            // Starts from what is stored: saving the profile used to write an
            // empty token, which ended the session at the next reconnect.
            authPrefs.saveAuth(
                authPrefs.loadAuth().copy(
                    username = user,
                    password = if (loginRememberMe.value || biometric) pass else "",
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
     * Sends a recognised (or typed) transcript from this app's own voice
     * assistant button (VoiceAssistantDialog) through the authenticated
     * Server boundary - the same POST /api/voice/turn route
     * WatchVoiceRelayService relays a paired Watch's own requests through,
     * but tagged as this phone's own ordinary traffic (postVoiceTurn(), not
     * postWatchVoiceTurn() - see that method's own comment on why the two
     * must stay independent). A reply only ever carries text/status; it
     * never invokes sendAtomicCommand or changes robot state directly.
     */
    fun sendVoiceTurn(transcript: String) {
        val client = apiClient ?: run {
            lastError.value = "Connect to HYDRA-UMC-SERVER before using the voice assistant."
            return
        }
        val requestId = "android-${System.currentTimeMillis()}"
        val locale = java.util.Locale.getDefault().toLanguageTag()
        voiceAssistantBusy.value = true
        viewModelScope.launch {
            try {
                val reply = client.postVoiceTurn(WatchVoiceTurn(requestId, transcript, locale))
                latestVoiceAssistantReply.value = reply
                logTelemetry("Voice assistant reply received: ${reply.level}; confirmation=${reply.requiresConfirmation}")
            } catch (error: Exception) {
                lastError.value = "Voice assistant request failed: ${error.message}"
                logTelemetry("Voice assistant request failed")
            } finally {
                voiceAssistantBusy.value = false
            }
        }
    }

    /** Clears the last assistant reply - called when VoiceAssistantDialog closes. */
    fun clearVoiceAssistantReply() {
        latestVoiceAssistantReply.value = null
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
        // The session ends for real: no live socket, no polling, no client
        // that the widget or the notification could still send commands with.
        reloginJob?.cancel()
        connectJob?.cancel()
        metricsJob?.cancel()
        ws?.disconnect()
        ws = null
        NotificationHelper.hideSafetyNotification(getApplication())
        connectionState.value = WsStatus.DISCONNECTED
        val client = apiClient
        apiClient = null
        authTokenState.value = null
        val keepPassword = isBiometricEnabled.value
        if (!keepPassword) loginPassword.value = ""
        viewModelScope.launch {
            // Revoke the refresh token on the server too (best effort).
            val refresh = client?.refreshToken ?: authPrefs.loadAuth().refreshToken.ifEmpty { null }
            if (client != null && refresh != null) client.logout(refresh)
            authPrefs.clearAuth(wipePassword = !keepPassword)
        }
    }

    // silent recovery from a WS 1008 close, using the SAME stored
    // (remember-me) username/password AuthPrefs already encrypts at rest
    // for this exact purpose (see AuthPrefs.kt's own header comment) - no
    // new server-side mechanism needed the way STUDIO's browser-only JWT
    // model required (its own refresh-token support, HYDRA-UMC-SERVER
    // 0.6.2): this app already has the real credential on hand, just
    // never used it for anything but pre-filling the login form. A real
    // logout()/clearAuth() call already wipes this password, so a session
    // the user (or an admin) actually ended never has anything to retry
    // with - this only ever recovers a real time-based token expiry on an
    // account that's still genuinely logged in. A revoked account (wrong
    // password now, deleted) fails the login() call below exactly like a
    // fresh manual attempt would, and this correctly falls through to the
    // existing forced-logout path in setupWebSocket()'s own onError.
    //
    // Honesty note: no automated test exercises this function's own real
    // recovery path end-to-end. AuthPrefs.kt's EncryptedSharedPreferences
    // needs a real AndroidKeyStore, unavailable under Robolectric's JVM
    // ("AndroidKeyStore not found" - confirmed by actually running a
    // MockWebServer-backed attempt at this test) - the same real
    // constraint every OTHER test in this package already works around by
    // never calling login()/touching authPrefs at all (they inject
    // viewModel.apiClient directly instead, bypassing this entire code
    // path). What IS verified: the fail-safe this function's own try/catch
    // guarantees - a failure for ANY reason (including authPrefs.loadAuth()
    // itself throwing, reproduced live in that same abandoned test)
    // degrades to today's real forced logout rather than a silently dead,
    // unrecoverable app - see the try/catch's own placement comment below.
    // Verifying the actual happy path needs either a real device/emulator
    // (a real AndroidKeyStore) or introducing dependency injection for
    // AuthPrefs across this ViewModel - out of scope for this change.
    private suspend fun attemptSilentRelogin(): Boolean {
        // The whole body sits in a try on purpose: authPrefs.loadAuth() touches
        // EncryptedSharedPreferences/AndroidKeyStore and can genuinely throw on
        // a real device, and a failure here must degrade to the forced logout
        // below the caller already has, never to a silently dead app.
        return try {
            val host = ipAddress.value.trim()
            val portInt = port.value.trim().toIntOrNull() ?: return false
            val client = HydraApiClient(host, portInt)
            if (!renewTokens(client)) return false
            apiClient = client
            logTelemetry("Session silently renewed after token expiry")
            // Reuses the exact same reconnect path a manual "Retry" would.
            connect()
            true
        } catch (e: Exception) {
            logTelemetry("Silent re-login failed: ${e.message}")
            false
        }
    }

    /**
     * Gets a new access token into [client]: the refresh token first (no
     * password needed), the stored "remember me" credentials as a fallback.
     * Stores the result. Returns false when neither works, which is the
     * caller's cue to send the user back to the login screen.
     */
    private suspend fun renewTokens(client: HydraApiClient): Boolean {
        return try {
            val profile = authPrefs.loadAuth()
            val refresh = client.refreshToken ?: profile.refreshToken.ifEmpty { null }
            var response: JSONObject? = null
            if (refresh != null) {
                response = try {
                    client.refresh(refresh).takeIf { it.optBoolean("success") }
                } catch (_: Exception) {
                    null
                }
            }
            if (response == null) {
                if (savedCredentialsRefused || !profile.rememberMe || profile.username.isBlank() || profile.password.isBlank()) return false
                val login = try {
                    client.login(profile.username, profile.password)
                } catch (e: Exception) {
                    // A 401/403/429 answer: do not insist with the same password.
                    if ((e as? HydraApiException)?.httpCode != null) savedCredentialsRefused = true
                    throw e
                }
                if (!login.optBoolean("success")) { savedCredentialsRefused = true; return false }
                response = login
            }
            val token = response.optStr("token")
            if (token.isEmpty()) return false
            client.authToken = token
            client.refreshToken = response.optStr("refreshToken").ifEmpty { null }
            authTokenState.value = token
            authPrefs.saveAuth(profile.copy(token = token, refreshToken = client.refreshToken ?: "", isLoggedIn = true))
            true
        } catch (e: Exception) {
            logTelemetry("Session renewal failed: ${e.message}")
            false
        }
    }

    /**
     * The one entry point for "the server says this session is no longer
     * valid": renews it once (however many callers notice at the same time)
     * and only if that fails sends the user back to the login screen.
     */
    private fun renewSessionOrLogout() {
        if (reloginJob?.isActive == true) return
        if (!renewalBudget.tryAcquire()) {
            logTelemetry("The session was rejected again right after being renewed: back to the sign-in")
            isLoggedIn.value = false
            connectionState.value = WsStatus.DISCONNECTED
            lastError.value = "The server keeps rejecting the session right after renewing it. Sign in again; if it repeats, check the server."
            ws?.disconnect()
            return
        }
        reloginJob = viewModelScope.launch {
            if (!attemptSilentRelogin()) {
                isLoggedIn.value = false
                connectionState.value = WsStatus.DISCONNECTED
                ws?.disconnect()
            }
        }
    }

    private fun logTelemetry(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        telemetryLogs.value = (listOf("[$timestamp] $message") + telemetryLogs.value).take(50)
    }

    /** Toggles the in-app notifications preference - see NotificationPrefs.kt's own header comment. */
    fun setNotificationsEnabled(enabled: Boolean) {
        notificationsEnabled.value = enabled
        viewModelScope.launch {
            notificationPrefs.setEnabled(enabled)
        }
        // Turning it off mid-session should hide the persistent safety
        // notification immediately too, not just gate the NEXT connect -
        // otherwise the owner turns this off expecting quiet and it stays
        // on screen until the next reconnect.
        if (!enabled) {
            NotificationHelper.hideSafetyNotification(getApplication())
        }
    }

    /**
     * Loads the server's own real V0 ecosystem-status scan - see
     * HydraApiClient.getEcosystemStatus()'s own doc comment. Safe to call
     * with no active connection (apiClient null): just no-ops, matching
     * every other on-demand fetch in this class rather than throwing.
     */
    fun fetchEcosystemStatus() {
        val client = apiClient ?: return
        isLoadingEcosystemStatus.value = true
        viewModelScope.launch {
            try {
                val response = client.getEcosystemStatus()
                ecosystemAvailable.value = response.optBoolean("available", false)
                val arr = response.optJSONArray("projects")
                val projects = mutableListOf<EcosystemProject>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val p = arr.optJSONObject(i) ?: continue
                        projects.add(
                            EcosystemProject(
                                name = p.optStr("name"),
                                role = p.optStr("role").takeIf { it.isNotBlank() && !p.isNull("role") },
                                stack = p.optStr("stack").takeIf { it.isNotBlank() && !p.isNull("stack") },
                                maturity = p.optStr("maturity").takeIf { it.isNotBlank() && !p.isNull("maturity") },
                                family = p.optStr("family").takeIf { it.isNotBlank() && !p.isNull("family") },
                                version = p.optStr("version").takeIf { it.isNotBlank() && !p.isNull("version") },
                                deploymentTarget = p.optStr("deploymentTarget").takeIf { it.isNotBlank() && !p.isNull("deploymentTarget") },
                                servicePort = if (p.isNull("servicePort")) null else p.optInt("servicePort"),
                                // Tri-state, not optBoolean's own false-when-absent default -
                                // "never probed" (no service declared) must stay distinct from
                                // a real, just-now "probed and it's down".
                                live = if (p.isNull("live")) null else p.optBoolean("live"),
                            )
                        )
                    }
                }
                ecosystemProjects.value = projects
            } catch (e: HydraApiException) {
                ecosystemAvailable.value = false
                lastError.value = e.message
            } finally {
                isLoadingEcosystemStatus.value = false
            }
        }
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
    fun connect(onInitialConnectSucceeded: (() -> Unit)? = null, onInitialConnectFailed: (() -> Unit)? = null) {
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

        connectionState.value = WsStatus.CONNECTING
        lastError.value = null
        plainRemoteHost.value = null
        isSwitchingServer = true

        ws?.disconnect()
        bleClient?.disconnect()
        bleClient = null

        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            // Ensure token is carried over or reloaded from persistence
            val profile = authPrefs.loadAuth()
            val token = apiClient?.authToken ?: profile.token
            val client = HydraApiClient(host, portInt)
            client.authToken = token
            client.refreshToken = apiClient?.refreshToken ?: profile.refreshToken.ifEmpty { null }
            apiClient = client
            authTokenState.value = token
            var restSynced = false

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
                
                plainRemoteHost.value = host.takeIf { TlsPolicy.isPlainRemote(it, client.usesTls) }
                if (plainRemoteHost.value != null) logTelemetry("Warning: unencrypted connection to a host outside the local network")
                val settings = try {
                    client.getSettings()
                } catch (e: HydraApiException) {
                    // An expired token at start-up: renew it once and read again
                    // instead of bouncing the user to the login screen.
                    if (e.isSessionProblem && renewTokens(client)) client.getSettings() else throw e
                }
                logTelemetry("Initial state synchronized via REST")
                applyState(HydraState(settings))
                restSynced = true
                onInitialConnectSucceeded?.invoke()
            } catch (e: HydraApiException) {
                logTelemetry("REST Sync Error: ${e.message}")
                lastError.value = e.message
                onInitialConnectFailed?.invoke()
            } finally {
                isSwitchingServer = false
            }

            // No socket against a server the REST sync could not reach: it would
            // only retry forever in the background. The metrics poll starts when
            // the socket reports CONNECTED (see setupWebSocket).
            if (restSynced) setupWebSocket(host, portInt, client.usesTls)
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
            while(connectionState.value == WsStatus.CONNECTED && isAppInForeground) {
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

    private fun setupWebSocket(host: String, port: Int, useTls: Boolean = false) {
        // Runs inside connect()'s own job, with the token that job just
        // confirmed against the REST sync: it used to be a separate coroutine
        // that read the token from disk, so two quick connects could leave an
        // orphan socket that kept reconnecting on its own.
        ws?.disconnect()
        run {
            val token = apiClient?.authToken.orEmpty()
            ws = HydraWebSocket(
                host = host,
                port = port,
                useTls = useTls,
                token = if (token.isNotEmpty()) token else null,
                onStatus = { status ->
                    connectionState.value = status
                    when (status) {
                        WsStatus.CONNECTING -> {
                            logTelemetry("WebSocket connecting...")
                        }
                        WsStatus.CONNECTED -> {
                            logTelemetry("WebSocket CONNECTED")
                            if (notificationsEnabled.value) {
                                NotificationHelper.showSafetyNotification(getApplication())
                            }
                        }
                        WsStatus.DISCONNECTED -> {
                            logTelemetry("WebSocket DISCONNECTED")
                            NotificationHelper.hideSafetyNotification(getApplication())
                            if (!isSwitchingServer) {
                                discoveredServers.value = emptyList()
                            }
                        }
                    }
                    if (status == WsStatus.CONNECTED) {
                        // Started here, after the status string above is set: the
                        // loop only runs while it reads "connected", and starting
                        // it after the REST sync (before the socket opened) made it
                        // end on its first check, so CPU/MEM/TEMP never appeared.
                        apiClient?.let { startMetricsLoop(it) }
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
                onClaims = { msg -> applyClaimsPush(msg) },
                onUnauthorized = {
                    // The server closed the socket with 1008 (expired or invalid
                    // token). One renewal, however many signals arrive; only a
                    // failed renewal sends the user back to the login screen.
                    if (!isSwitchingServer) renewSessionOrLogout()
                },
            ) { message ->
                if (!isSwitchingServer) {
                    lastError.value = message
                }
            }.also { it.connect() }
        }
    }

    /**
     * [changedRobotIds] lists the robots whose raw state was mutated in place
     * since the last call; only their display rows are rebuilt and every other
     * row is reused. null (the default) means a full replace: rebuild all.
     */
    private fun applyState(newState: HydraState, isFromCache: Boolean = false, changedRobotIds: Set<Int>? = null) {
        val oldState = state
        state = newState
        if (!isFromCache) {
            stateIsLive.value = true
            stateAtMs.value = System.currentTimeMillis()
        }
        robots.value = reuseUnchangedRows(
            previous = if (newState === oldState) robots.value else emptyList(),
            views = newState.allRobots,
            changedIds = if (newState === oldState) changedRobotIds else null,
            viewId = { it.id },
            rowId = { it.id },
            convert = { it.toDisplay() },
        )
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
                // Serialized here, on the main thread that also mutates the tree,
                // not inside DataStore's own thread.
                try {
                    stateCache.saveState(newState.toJson().toString())
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    logTelemetry("State cache not saved: ${e.message}")
                }
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
                if ((oldRobot != null) && oldRobot.isPlaying && !robot.isPlaying && robot.isFinished && notificationsEnabled.value) {
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
     * partial patch - the delta-sync design mitigation (b),
     * non-optional. This app has no CameraView (cameras aren't modeled as
     * their own class here, only cameraPatch's raw JSON is merged) - a
     * cameraPatch is still applied to the underlying tree for correctness
     * even though nothing in this app currently reads it back out.
     */
    private fun applyRobotDelta(msg: JSONObject) {
        val controllerId = msg.optStr("controllerId", "")
        val robotId = if (msg.has("robotId")) msg.optInt("robotId", Int.MIN_VALUE) else Int.MIN_VALUE
        val patch = msg.optJSONObject("patch")
        if (controllerId.isEmpty() || patch == null) return

        var targetRobotRaw: JSONObject? = null
        var targetCameraRaw: JSONObject? = null
        val controllersArr = state.raw.optJSONArray("controllers")
        if (controllersArr != null) {
            for (i in 0 until controllersArr.length()) {
                val c = controllersArr.optJSONObject(i) ?: continue
                if (c.optStr("id", "") != controllerId) continue
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
        applyState(state, changedRobotIds = setOf(robotId))
    }

    // Every write in this app goes through the real atomic
    // POST /api/robot/:id/command (server.ts:210-298) instead of pushing the
    // full settings tree for a single jog tick: the server computes
    // affectedIds itself (self + combinedWith), persists to disk, AND
    // broadcasts a "delta" to every connected client (this one included) on
    // its own, so this app doesn't also need to push the full tree over its
    // own WebSocket. Every command shares the same affectedIds computation below
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

        // A jog above the limit the server holds for this robot model is not sent at all (the
        // server checks it again). Checked before the optimistic change, like the client above.
        if (command == "jog" || command == "table") {
            if (jogNeedsArming(robotId)) {
                lastError.value = getApplication<Application>().getString(R.string.error_jog_not_armed)
                return
            }
            val limits = safetyByRobot.value[robotId]?.jog
            // A table move is checked as a whole, and a relative one also by where it ends.
            val exceeded = if (command == "table") {
                params?.let { tableLimitExceeded(it, limits, targetRobot.xyTablePos.optDouble("x", 0.0), targetRobot.xyTablePos.optDouble("y", 0.0)) }
            } else {
                jogLimitExceeded(
                    amount = params?.optDouble("amount", 0.0) ?: 0.0,
                    limits = limits,
                    absolute = params?.optBoolean("absolute", false) ?: false,
                )
            }
            if (exceeded != null) {
                lastError.value = getApplication<Application>().getString(R.string.error_jog_over_limit, formatLimit(exceeded))
                return
            }
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
        val snapshots = affectedIds.mapNotNull { id -> state.robotById(id)?.let { id to JSONObject(it.raw.toString()) } }
        affectedIds.forEach { id -> state.robotById(id)?.let(localMutate) }
        // Only the fields this command changed are undone on failure (and only
        // while they still hold the value it wrote), so deltas that arrived in
        // the meantime and jogs that succeeded keep their newer values.
        val edits = snapshots.map { (id, before) ->
            id to OptimisticRollback.diff(before, JSONObject(state.robotById(id)?.raw?.toString() ?: "{}"))
        }
        applyState(state, changedRobotIds = affectedIds.toSet())

        val payload = JSONObject().put("command", command).apply { if (params != null) put("params", params) }

        fun rollback() {
            for ((id, changes) in edits) {
                val raw = state.robotById(id)?.raw ?: continue
                OptimisticRollback.restore(raw, changes)
            }
            applyState(state, changedRobotIds = edits.map { it.first }.toSet())
        }

        val isJog = command == "jog" || command == "table"
        val send: () -> Unit = {
            if (isJog) jogsInFlight[robotId] = (jogsInFlight[robotId] ?: 0) + 1
            viewModelScope.launch {
                try {
                    val answer = postCommandConditionally(client, robotId, command, payload)
                    val warnings = commandWarnings(answer)
                    if (warnings != null) {
                        // The server answered 200 but applied nothing (a speed
                        // outside its range, a malformed jog): same as a failure.
                        logTelemetry("TX not applied [$command]: $warnings")
                        lastError.value = getApplication<Application>().getString(R.string.error_command_not_applied, command, warnings)
                        rollback()
                        return@launch
                    }
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
                    // The server disarms the jog on a stop.
                    if (command == "stop") jogArmedUntilMs.value = jogArmedUntilMs.value - robotId
                    lastError.value = null
                } catch (e: HydraApiException) {
                    logTelemetry("TX Error [$command]: ${e.message}")
                    lastError.value = describeCommandError(command, e)
                    // The robot changed since this app read it: read it again so the screen shows what it is now.
                    if (e.isRevisionMismatch) refreshRobot(robotId)
                    // The arming ended (it times out, or a stop cleared it): the screen offers to arm again.
                    if (e.isJogNotArmed) jogArmedUntilMs.value = jogArmedUntilMs.value - robotId
                    // An expired session is renewed (once, whoever notices first);
                    // only a failed renewal returns to the login screen.
                    if (e.isSessionProblem) renewSessionOrLogout()
                    rollback()
                } catch (e: Exception) {
                    // Was `catch (e: HydraApiException)` only - a plain
                    // timeout/SocketException from OkHttp isn't wrapped in
                    // HydraApiException, so it escaped uncaught, skipping
                    // both the error message AND the rollback below.
                    logTelemetry("TX Error [$command]: ${e.message}")
                    lastError.value = getApplication<Application>().getString(
                        if (command == "stop") R.string.error_stop_not_confirmed else R.string.error_command_failed,
                        command,
                    )
                    rollback()
                } finally {
                    if (isJog) jogsInFlight[robotId] = ((jogsInFlight[robotId] ?: 1) - 1).coerceAtLeast(0)
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

    /**
     * A stop is worth retrying: it is posted with short timeouts, and a
     * network failure (no answer at all) is tried again up to 3 times in
     * about a second. An answer from the server, of any kind, is final.
     */
    private suspend fun postCommandWithRetry(
        client: HydraApiClient,
        robotId: Int,
        payload: JSONObject,
        urgent: Boolean,
        ifMatch: String? = null,
    ): JSONObject {
        var attempt = 0
        while (true) {
            try {
                return client.postRobotCommand(robotId, payload, urgent, ifMatch)
            } catch (e: HydraApiException) {
                if (!urgent || e.httpCode != null || attempt >= 2) throw e
            } catch (e: java.io.IOException) {
                if (!urgent || attempt >= 2) throw e
            }
            attempt++
            // Waited on a real dispatcher (not the caller's), so the pause is real
            // time even where the main dispatcher is a virtual-time test one.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { kotlinx.coroutines.delay(250) }
        }
    }

    /**
     * Runs one write with the revision this app last saw of the robot as If-Match (never for STOP, and only when the mode
     * and the tracker allow it), keeps the revision of the answer for the next write, and on a 412 keeps the one the
     * server says the robot is at now. The failure is rethrown for the caller to report.
     */
    private suspend fun withRevision(robotId: Int, command: String, call: suspend (String?) -> JSONObject): JSONObject {
        val ticket = revisions.begin(robotId, command)
        try {
            val answer = call(ticket.ifMatch)
            revisions.finish(robotId, ticket.seq, revisionOf(answer))
            return answer
        } catch (e: HydraApiException) {
            revisions.finish(robotId, ticket.seq, null)
            if (e.isRevisionMismatch) revisions.learn(robotId, e.revision)
            throw e
        } catch (e: Exception) {
            revisions.finish(robotId, ticket.seq, null)
            throw e
        }
    }

    /** Posts a command conditionally; a `table` move goes out as the jogs that did the same on a server that has no such command. */
    private suspend fun postCommandConditionally(client: HydraApiClient, robotId: Int, command: String, payload: JSONObject): JSONObject {
        val params = payload.optJSONObject("params")
        if (command == "table" && !tableCommandSupported && params != null) return postTableAsJogs(client, robotId, params)
        return try {
            withRevision(robotId, command) { ifMatch -> postCommandWithRetry(client, robotId, payload, urgent = command == "stop", ifMatch = ifMatch) }
        } catch (e: HydraApiException) {
            if (command == "table" && params != null && isUnknownCommand(e)) {
                tableCommandSupported = false
                postTableAsJogs(client, robotId, params)
            } else {
                throw e
            }
        }
    }

    private suspend fun postTableAsJogs(client: HydraApiClient, robotId: Int, params: JSONObject): JSONObject {
        var last = JSONObject().put("success", true)
        for (jog in tableAsJogs(params)) {
            last = postCommandWithRetry(client, robotId, JSONObject().put("command", "jog").put("params", jog), urgent = false)
        }
        return last
    }

    /** The id of the controller that holds [robotId] in the settings tree, or null. */
    private fun controllerIdOf(robotId: Int): String? {
        val controllers = state.raw.optJSONArray("controllers") ?: return null
        for (i in 0 until controllers.length()) {
            val controller = controllers.optJSONObject(i) ?: continue
            val robotsArray = controller.optJSONArray("robots") ?: continue
            for (j in 0 until robotsArray.length()) {
                if (robotsArray.optJSONObject(j)?.optInt("id", Int.MIN_VALUE) == robotId) return controller.optStr("id")
            }
        }
        return null
    }

    /**
     * Reads one robot again (GET /api/robot/:id, which also gives the revision to use next) and puts it on screen.
     * Called after a 412: the robot changed under this app, and what is shown may be behind. Never raises an error.
     */
    private fun refreshRobot(robotId: Int) {
        val client = apiClient ?: return
        viewModelScope.launch {
            try {
                val read = client.getRobot(robotId) ?: return@launch
                revisions.learn(robotId, read.revision)
                val controllerId = controllerIdOf(robotId) ?: return@launch
                applyRobotDelta(JSONObject().put("controllerId", controllerId).put("robotId", robotId).put("patch", read.robot))
            } catch (e: HydraApiException) {
                if (e.isSessionProblem) renewSessionOrLogout()
            } catch (_: Exception) {
            }
        }
    }

    /** The reservations the server pushed (WebSocket message `claims`): replaces the list and the arming of this app's own reservations. */
    private fun applyClaimsPush(msg: JSONObject) {
        val list = ClaimInfo.listFromJson(msg)
        claims.value = list
        val now = System.currentTimeMillis()
        claimsPushedAtMs = now
        for (claim in list) {
            if (claim.controllerId != SessionController.id) continue
            val until = claim.jogArmedUntil
            val ttl = safetyByRobot.value[claim.robotId]?.armTtlMs ?: 60_000L
            // The server clock and this phone clock may differ: the arming is shown as ending no later than its length allows.
            jogArmedUntilMs.value = if (until != null && until > now) {
                jogArmedUntilMs.value + (claim.robotId to minOf(until, now + ttl))
            } else {
                jogArmedUntilMs.value - claim.robotId
            }
        }
    }

    /** Whether the server pushed the reservations recently, so the poll of GET /api/claims can rest. */
    fun claimsArePushed(nowMs: Long = System.currentTimeMillis(), withinMs: Long = 30_000L): Boolean =
        claimsPushedAtMs > 0 && nowMs - claimsPushedAtMs < withinMs

    /** Chooses which writes are checked, and keeps the choice. */
    fun setConditionalMode(mode: ConditionalMode) {
        conditionalModeTouched = true
        conditionalMode.value = mode
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { prefs.saveConditionalMode(mode) }
    }

    /** Chooses whether PLAY asks first, and keeps the choice. */
    fun setConfirmPlay(ask: Boolean) {
        confirmPlayTouched = true
        confirmPlay.value = ask
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { prefs.saveConfirmPlay(ask) }
    }

    /** The server's `warnings` of a 200 answer (it applied nothing), joined; null when there are none. */
    private fun commandWarnings(answer: JSONObject): String? {
        val warnings = answer.optJSONArray("warnings") ?: return null
        if (warnings.length() == 0) return null
        return (0 until warnings.length()).joinToString("; ") { i ->
            val item = warnings.opt(i)
            if (item is JSONObject) item.optString("message", item.toString()) else item.toString()
        }
    }

    /** The message for a command the server answered with an error, by what the answer says. */
    private fun describeCommandError(command: String, e: HydraApiException): String {
        val app = getApplication<Application>()
        return when {
            e.isSessionProblem -> app.getString(R.string.error_session_expired)
            e.isRobotDisabled -> app.getString(R.string.error_robot_disabled, command)
            e.isRevisionMismatch -> app.getString(R.string.error_revision_changed, command)
            e.isJogNotArmed -> app.getString(R.string.error_jog_not_armed)
            e.isStaleSequence -> app.getString(R.string.error_stale_sequence, command)
            e.isOtherController -> app.getString(R.string.error_claim_other_controller, e.serverMessage ?: "")
            e.isControllerIdRequired -> app.getString(R.string.error_claim_controller_required, e.serverMessage ?: "")
            e.isReservedByAnother -> app.getString(R.string.error_robot_reserved, e.serverMessage ?: "")
            e.isPermissionDenied -> app.getString(R.string.error_permission_denied)
            e.isRejectedCommand -> app.getString(R.string.error_command_rejected, command, e.serverMessage ?: "")
            command == "stop" -> app.getString(R.string.error_stop_not_confirmed)
            else -> app.getString(R.string.error_command_failed, command)
        }
    }

    /** `1000.0` is shown as `1000`, `12.5` as `12.5`. */
    private fun formatLimit(limit: Double): String = limit.toString().removeSuffix(".0")

    /** Where [robotId] stands for this installation, from the last list of reservations. */
    fun claimStandingOf(robotId: Int?): ClaimStanding =
        claimStanding(claims.value.firstOrNull { it.robotId == robotId }, loginUsername.value, SessionController.id)

    /** The reservation of [robotId], if any. */
    fun claimOf(robotId: Int?): ClaimInfo? = claims.value.firstOrNull { it.robotId == robotId }

    /** True when the server requires the jog of [robotId] to be armed and it is not (this app's view of it). */
    fun jogNeedsArming(robotId: Int?, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (robotId == null) return false
        val safety = safetyByRobot.value[robotId] ?: return false
        if (!safety.armRequired) return false
        return (jogArmedUntilMs.value[robotId] ?: 0L) <= nowMs
    }

    /**
     * Reads the reservations and the limits of the selected robot's model from the server.
     * Called when the control screen opens, when the robot changes and now and then while it is
     * open; it never raises an error on the screen (the server may simply be an older one).
     */
    fun refreshAccess(fromPoll: Boolean = false) {
        val client = apiClient ?: return
        val robotId = selectedRobotId.value
        viewModelScope.launch {
            // The server pushes the reservations when they change: while a push is fresh the poll does not ask for them.
            if (!(fromPoll && claimsArePushed())) {
                try {
                    claims.value = client.getClaims()
                } catch (e: HydraApiException) {
                    if (e.isSessionProblem) renewSessionOrLogout()
                } catch (_: Exception) {
                }
            }
            if (robotId == null) return@launch
            try {
                val safety = client.getRobotSafety(robotId) ?: return@launch
                safetyByRobot.value = safetyByRobot.value + (robotId to safety)
                val until = safety.jogArmedUntil
                val now = System.currentTimeMillis()
                // The server clock and this phone clock may differ: the arming is shown as ending no later than its length allows.
                jogArmedUntilMs.value = if (until != null && until > now) {
                    jogArmedUntilMs.value + (robotId to minOf(until, now + safety.armTtlMs))
                } else {
                    jogArmedUntilMs.value - robotId
                }
            } catch (e: HydraApiException) {
                if (e.isSessionProblem) renewSessionOrLogout()
            } catch (_: Exception) {
            }
        }
    }

    /** Reserves the selected robot for this account on this installation. */
    fun claimSelectedRobot() = runAccessRequest("claim") { client, robotId ->
        withRevision(robotId, "claim") { ifMatch -> client.postClaim(robotId, ifMatch = ifMatch) }
    }

    /** Whether this session is an administrator's (the role in its token; the server checks it again). */
    fun isAdmin(): Boolean = jwtRole(authTokenState.value) == "admin"

    /** Whether the take-over button applies to [robotId]: an administrator, and someone else holds it. */
    fun canTakeOver(robotId: Int?): Boolean = com.hydraumc.control.network.canTakeOver(jwtRole(authTokenState.value), claimStandingOf(robotId))

    /**
     * Takes the selected robot over from whoever holds it (`force: true`; the server accepts it from administrators
     * only). The screen asks the operator to confirm first.
     */
    fun takeOverSelectedRobot() = runAccessRequest("claim") { client, robotId ->
        if (!canTakeOver(robotId)) throw IllegalStateException("take-over is for administrators, on a robot someone else holds")
        client.postClaim(robotId, force = true)
    }

    /** Releases the selected robot reservation. Also ends the arming of its jog, as the server does. */
    fun releaseSelectedRobot() = runAccessRequest("release") { client, robotId ->
        withRevision(robotId, "release") { ifMatch -> client.postRelease(robotId, ifMatch = ifMatch) }
            .also { jogArmedUntilMs.value = jogArmedUntilMs.value - robotId }
    }

    /** Arms or disarms the jog of the selected robot (only needed when the server requires it). */
    fun setJogArmed(armed: Boolean) = runAccessRequest("jog-arm") { client, robotId ->
        val answer = client.postJogArm(robotId, armed)
        if (armed) {
            val ttl = answer.optLong("ttlMs", safetyByRobot.value[robotId]?.armTtlMs ?: 60_000L)
            jogArmedUntilMs.value = jogArmedUntilMs.value + (robotId to System.currentTimeMillis() + ttl)
        } else {
            jogArmedUntilMs.value = jogArmedUntilMs.value - robotId
        }
        answer
    }

    private fun runAccessRequest(what: String, call: suspend (HydraApiClient, Int) -> JSONObject) {
        val app = getApplication<Application>()
        val robotId = selectedRobotId.value ?: return
        val client = apiClient ?: run {
            lastError.value = app.getString(R.string.error_not_connected)
            return
        }
        accessBusy.value = true
        viewModelScope.launch {
            try {
                call(client, robotId)
                logTelemetry("TX OK [$what] -> robot $robotId")
                lastError.value = null
            } catch (e: HydraApiException) {
                logTelemetry("TX Error [$what]: ${e.message}")
                lastError.value = describeCommandError(what, e)
                if (e.isRevisionMismatch) refreshRobot(robotId)
                if (e.isSessionProblem) renewSessionOrLogout()
            } catch (e: Exception) {
                logTelemetry("TX Error [$what]: ${e.message}")
                lastError.value = app.getString(R.string.error_command_failed, what)
            } finally {
                accessBusy.value = false
            }
            refreshAccess()
        }
    }

    /**
     * Reads the audit record (administrators only): the newest lines for a filter, and with [verify] the server checks the
     * whole record while this app checks the chain of the lines it was given. Never raises an error on the screen:
     * the reason is left in [auditFailure].
     */
    fun refreshAudit(verify: Boolean, type: String?, robotId: Int?, limit: Int) {
        val client = apiClient ?: run {
            auditFailure.value = AuditFailure.NETWORK
            return
        }
        auditBusy.value = true
        viewModelScope.launch {
            try {
                val read = AuditRecord.parse(client.getAuditBody(AuditRecord.query(limit, type, robotId, verify)))
                auditRead.value = read
                auditChain.value = if (verify) AuditRecord.checkChain(read.lines) else null
                auditFailure.value = null
            } catch (e: HydraApiException) {
                auditRead.value = null
                auditChain.value = null
                auditFailure.value = when {
                    e.isSessionProblem -> AuditFailure.SESSION
                    e.httpCode == 403 -> AuditFailure.FORBIDDEN
                    e.isNotFound -> AuditFailure.UNAVAILABLE
                    e.httpCode == null -> AuditFailure.NETWORK
                    else -> AuditFailure.OTHER
                }
                if (e.isSessionProblem) renewSessionOrLogout()
            } catch (_: Exception) {
                auditRead.value = null
                auditChain.value = null
                auditFailure.value = AuditFailure.NETWORK
            } finally {
                auditBusy.value = false
            }
        }
    }

    /** Reads the list of clients and the latest sign-ins; never raises an error on the screen: the reason is left in [connectionsFailure]. */
    fun refreshConnections() {
        val client = apiClient ?: run {
            connectionsFailure.value = ConnectionsFailure.NETWORK
            return
        }
        connectionsBusy.value = true
        viewModelScope.launch {
            try {
                val clients = ConnectionHistory.parseClients(client.getConnectionClientsBody())
                val events = ConnectionHistory.parseEvents(client.getConnectionEventsBody(CONNECTION_EVENTS_SHOWN))
                connectionClients.value = clients
                connectionEvents.value = events
                connectionsFailure.value = null
            } catch (e: HydraApiException) {
                connectionClients.value = null
                connectionEvents.value = null
                connectionsFailure.value = when {
                    e.isSessionProblem -> ConnectionsFailure.SESSION
                    e.isNotFound -> ConnectionsFailure.UNAVAILABLE
                    e.httpCode == null -> ConnectionsFailure.NETWORK
                    else -> ConnectionsFailure.OTHER
                }
                if (e.isSessionProblem) renewSessionOrLogout()
            } catch (_: Exception) {
                connectionClients.value = null
                connectionEvents.value = null
                connectionsFailure.value = ConnectionsFailure.NETWORK
            } finally {
                connectionsBusy.value = false
            }
        }
    }

    private fun serverOptionsFailureOf(e: HydraApiException): ServerOptionsFailure = when {
        e.isSessionProblem -> ServerOptionsFailure.SESSION
        e.isPermissionDenied -> ServerOptionsFailure.FORBIDDEN
        e.isNotFound -> ServerOptionsFailure.UNAVAILABLE
        e.httpCode == null -> ServerOptionsFailure.NETWORK
        else -> ServerOptionsFailure.OTHER
    }

    /** Reads the access options of the server; the reason it could not is left in [serverOptionsFailure], never raised on the screen. */
    fun refreshServerOptions() {
        val client = apiClient ?: run {
            serverOptionsFailure.value = ServerOptionsFailure.NETWORK
            return
        }
        serverOptionsBusy.value = true
        viewModelScope.launch {
            try {
                val options = ServerSecurity.parse(client.getServerConfigBody())
                serverOptions.value = options
                serverOptionsFailure.value = if (options == null) ServerOptionsFailure.OTHER else null
            } catch (e: HydraApiException) {
                serverOptions.value = null
                serverOptionsFailure.value = serverOptionsFailureOf(e)
                if (e.isSessionProblem) renewSessionOrLogout()
            } catch (_: Exception) {
                serverOptions.value = null
                serverOptionsFailure.value = ServerOptionsFailure.NETWORK
            } finally {
                serverOptionsBusy.value = false
            }
        }
    }

    /** Saves what changed in [values] (the screen has already asked about every change that lowers the protection) and shows the options as the server now holds them. */
    fun saveServerOptions(values: ServerSecurityValues) {
        val client = apiClient ?: return
        val current = serverOptions.value ?: return
        val body = ServerSecurity.patch(current, values) ?: return
        serverOptionsBusy.value = true
        serverOptionsSaved.value = false
        viewModelScope.launch {
            try {
                val answer = client.putServerConfigBody(body.toString())
                serverOptions.value = ServerSecurity.parse(answer) ?: current
                serverOptionsFailure.value = null
                serverOptionsSaved.value = true
            } catch (e: HydraApiException) {
                serverOptionsFailure.value = serverOptionsFailureOf(e)
                if (e.isSessionProblem) renewSessionOrLogout()
            } catch (_: Exception) {
                serverOptionsFailure.value = ServerOptionsFailure.NETWORK
            } finally {
                serverOptionsBusy.value = false
            }
        }
    }

    /** The lines of the audit record as the server stores them (`.jsonl`), for sharing; [onReady] gets null when they could not be read. */
    fun exportAudit(type: String?, robotId: Int?, limit: Int, onReady: (String?) -> Unit) {
        val client = apiClient ?: run {
            onReady(null)
            return
        }
        viewModelScope.launch {
            val text = try {
                client.getAuditBody(AuditRecord.query(limit, type, robotId, format = "jsonl"))
            } catch (_: Exception) {
                null
            }
            onReady(text)
        }
    }

    /** True while a jog of [robotId] is still on the wire, or the robot is running a trajectory (two writers on one setpoint). */
    private fun jogBlocked(robotId: Int?): Boolean {
        if (robotId == null) return true
        if ((jogsInFlight[robotId] ?: 0) > 0) return true
        val robot = state.robotById(robotId) ?: return true
        return robot.isPlaying && !robot.isPaused
    }

    /**
     * The global STOP: one call to the server's stop-all route that stops every
     * robot, whoever reserved it. It goes out at once (it never waits for the
     * roster or a refresh), is retried once when nobody answers, falls back to
     * stopping the known robots one by one when the server rate limits the
     * global call, and - when nothing confirms it - leaves
     * [stopAllStatus] at [StopAllStatus.NotConfirmed] until a later stop works.
     * The widget, the notification action and every STOP button end up here.
     */
    fun globalStop() {
        val app = getApplication<Application>()
        val first = apiClient
        if (first == null) {
            val reason = app.getString(R.string.error_not_connected)
            stopAllStatus.value = StopAllStatus.NotConfirmed(reason)
            lastError.value = app.getString(R.string.stop_all_not_confirmed)
            return
        }
        stopAllStatus.value = StopAllStatus.Sending
        viewModelScope.launch {
            val outcome = runStopAll(first)
            stopAllStatus.value = outcome
            if (outcome is StopAllStatus.Done) {
                // Show the stop at once; the next state broadcast confirms or corrects it.
                outcome.result.robots.filter { it.stopped }.forEach { r -> state.robotById(r.robotId)?.stop() }
                jogArmedUntilMs.value = emptyMap() // the server disarms every jog on a global stop
                applyState(state, changedRobotIds = outcome.result.robots.filter { it.stopped }.map { it.robotId }.toSet())
                logTelemetry("TX OK [stop-all] -> ${outcome.result.stoppedCount} stopped, ${outcome.result.failedCount} failed")
                lastError.value = null
            } else if (outcome is StopAllStatus.NotConfirmed) {
                logTelemetry("TX Error [stop-all]: ${outcome.reason}")
                lastError.value = app.getString(R.string.stop_all_not_confirmed)
            }
        }
    }

    /** Hides the result banner of the global STOP; a STOP that is not confirmed cannot be hidden. */
    fun dismissStopAllStatus() {
        if (stopAllStatus.value is StopAllStatus.Done) stopAllStatus.value = null
    }

    private suspend fun runStopAll(start: HydraApiClient): StopAllStatus {
        var client = start
        var networkRetried = false
        var sessionRenewed = false
        while (true) {
            try {
                return StopAllStatus.Done(client.postStopAll())
            } catch (e: HydraApiException) {
                when {
                    // 429: over the server's rate limit. 404: a server that does not have the route yet.
                    e.isRateLimited || e.isNotFound -> return stopRobotsOneByOne(client)
                    e.isSessionProblem && !sessionRenewed -> {
                        sessionRenewed = true
                        if (!attemptSilentRelogin()) {
                            renewSessionOrLogout()
                            return StopAllStatus.NotConfirmed(getApplication<Application>().getString(R.string.error_session_expired))
                        }
                        client = apiClient ?: return StopAllStatus.NotConfirmed(null)
                    }
                    e.httpCode == null && !networkRetried -> {
                        networkRetried = true
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { kotlinx.coroutines.delay(300) }
                    }
                    else -> return StopAllStatus.NotConfirmed(e.serverMessage ?: e.message)
                }
            } catch (e: java.io.IOException) {
                if (networkRetried) return StopAllStatus.NotConfirmed(e.message)
                networkRetried = true
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { kotlinx.coroutines.delay(300) }
            } catch (e: Exception) {
                return StopAllStatus.NotConfirmed(e.message)
            }
        }
    }

    /** The fallback when the global call is rate limited: the per-robot stop for every known robot. */
    private suspend fun stopRobotsOneByOne(client: HydraApiClient): StopAllStatus {
        val known = state.allRobots
        if (known.isEmpty()) return StopAllStatus.NotConfirmed(getApplication<Application>().getString(R.string.error_robot_not_found))
        val payload = { JSONObject().put("command", "stop") }
        val entries = known.map { robot ->
            val error = try {
                postCommandWithRetry(client, robot.id, payload(), urgent = true)
                null
            } catch (e: Exception) {
                (e as? HydraApiException)?.serverMessage ?: e.message ?: "error"
            }
            StopAllRobot(
                robotId = robot.id, controllerId = "", name = robot.name,
                stopped = error == null, valvesOff = false, pumpsOff = false,
                reservedBy = null, error = error,
            )
        }
        val failed = entries.count { !it.stopped }
        return StopAllStatus.Done(
            StopAllResult(
                success = failed == 0, stoppedCount = entries.size - failed, failedCount = failed,
                persisted = true, robots = entries, viaFallback = true,
            ),
        )
    }

    fun sendCommand(command: String) {
        when (command) {
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
            // Safe mode of the selected robot (only this robot, never its combined group). `disable` stops it and the
            // server then refuses everything but STOP; `enable` brings it back and the server only allows it to an
            // administrator (it answers 403 to anyone else).
            "disable" -> sendAtomicCommand(command) { it.stop(); it.setEnabled(false) }
            "enable" -> sendAtomicCommand(command) { it.setEnabled(true) }
            else -> lastError.value = getApplication<Application>().getString(R.string.error_unknown_command, command)
        }
    }

    fun jog(target: String, axis: String, amount: Double) {
        if (jogBlocked(selectedRobotId.value)) return
        jogAxis(target, axis, amount)
    }

    private fun jogAxis(target: String, axis: String, amount: Double) {
        if (target == "xytable" && (axis == "x" || axis == "y")) {
            // One atomic "table" move relative to where the table is now: the server checks the whole move against
            // the limits of the model and applies all of it or none of it.
            val move = tableParams(if (axis == "x") amount else 0.0, if (axis == "y") amount else 0.0, "relative") ?: return
            sendAtomicCommand("table", move) { r -> r.setXyTableAxis(axis, r.xyTablePos.optDouble(axis, 0.0) + amount) }
            return
        }
        val params = JSONObject().put("axis", axis).put("amount", amount).put("target", target)
        sendAtomicCommand("jog", params) { r ->
            if (target == "robot") {
                r.setPosAxis(axis, r.posAxis(axis) + amount)
            } else if (target == "xytable") {
                r.setXyTableAxis(axis, r.xyTablePos.optDouble(axis, 0.0) + amount)
            }
        }
    }

    /**
     * XYZ jog for the joystick D-pad - mirrors HYDRA-UMC-STUDIO's own
     * handleXYZJog() in RobotDetail.tsx: resolves the combined dx/dy/dz
     * delta against this robot's own real per-model kinematics ONCE, then
     * fires up to 3 atomic 'jog' commands (one per non-zero axis, same as
     * server.ts only ever moves one pos axis per command) all carrying
     * that SAME resolved joints override - instead of jogging pos.x/y/z
     * blindly and letting server.ts's own calculateJoints() (a single
     * generic IK formula) silently diverge from this robot's real
     * kinematics chain. See server.ts's own jog case comment and
     * Parol6Kinematics.kt's own header for the full rationale.
     *
     * Only Parol6 has a real Kotlin port of that per-model IK today - the
     * same gap every non-STUDIO client (iOS, DSI, SUITE) still has for
     * every other model, not a regression this introduces. Every other
     * model/target keeps jogging pos.x/y/z exactly as this app always
     * has, via the plain jog() calls below - no worse than before.
     */
    fun jogXYZ(target: String, dx: Int, dy: Int, dz: Int, jogStep: Double) {
        val robotId = selectedRobotId.value
        if (jogBlocked(robotId)) return // the previous tick is still on the wire, or a trajectory is running
        val robot = robotId?.let { state.robotById(it) }
        if (target == "robot" && robot != null && robot.model == "Parol6 (6-DOF)") {
            val x = robot.posAxis("x") + dx * jogStep
            val y = robot.posAxis("y") + dy * jogStep
            val z = robot.posAxis("z") + dz * jogStep
            val a = robot.posAxis("a")
            val b = robot.posAxis("b")
            val c = robot.posAxis("c")
            val newJoints = parol6CartesianToJointsOrNull(x, y, z, a, b, c)
            if (newJoints == null) {
                // Out of reach: sending clamped joints next to a position that does not match them
                // would store an incoherent pose on the server, so the jog is refused instead.
                lastError.value = getApplication<Application>().getString(R.string.error_jog_out_of_reach)
                return
            }
            val axes = listOf(
                Triple("x", dx * jogStep, x),
                Triple("y", dy * jogStep, y),
                Triple("z", dz * jogStep, z),
            ).filter { it.second != 0.0 }
            for ((axis, amount, value) in axes) {
                val jointsParam = JSONObject()
                JOINT_NAMES.forEachIndexed { i, name -> jointsParam.put(name, newJoints[i]) }
                val params = JSONObject().put("axis", axis).put("amount", amount).put("target", target).put("joints", jointsParam)
                sendAtomicCommand("jog", params) { r ->
                    r.setPosAxis(axis, value)
                    JOINT_NAMES.forEachIndexed { i, name -> r.setJoint(name, newJoints[i]) }
                }
            }
            return
        }

        if (dx != 0) jogAxis(target, "x", dx * jogStep)
        if (dy != 0) jogAxis(target, "y", dy * jogStep)
        if (dz != 0) jogAxis(target, "z", dz * jogStep)
    }

    /**
     * Dedicated base-rotation (J1) jog - mirrors HYDRA-UMC-STUDIO's own
     * new handleJ1Jog() in RobotDetail.tsx. Deliberately pure joint-space,
     * not routed through jogXYZ()'s Cartesian/IK path above: rotating J1
     * alone needs no inverse kinematics at all (unlike dx/dy/dz), so this
     * is both simpler and model-agnostic - every model can be jogged this
     * way, not just Parol6 (the only one with a real IK port here today).
     * Real per-model limits only exist for Parol6 in this app so far
     * (PAROL6_JOINT_LIMITS_DEG - same gap jogXYZ's own IK path already
     * has for every other model); every other model uses the same
     * [-180, 180] generic fallback STUDIO's own jointLimitsFor() uses.
     *
     * Does NOT attempt to keep [RobotState.pos]'s Cartesian fields
     * consistent locally the way STUDIO's own fix does (that needs a
     * per-model forward-kinematics port this app doesn't have for most
     * models) - `joints` is what's actually authoritative server-side and
     * what every other real cartesian computation in this app already
     * re-derives fresh from, so a stale local `pos` display value here is
     * cosmetic, not a correctness bug.
     */
    fun jogJ1(direction: Int, jogStep: Double) {
        val robotId = selectedRobotId.value
        if (jogBlocked(robotId)) return
        val robot = robotId?.let { state.robotById(it) } ?: return
        val (j1Min, j1Max) = if (robot.model == "Parol6 (6-DOF)") {
            PAROL6_JOINT_LIMITS_DEG.getValue("j1")
        } else {
            -180.0 to 180.0
        }
        val currentJ1 = robot.joints.optDouble("j1", 0.0)
        val newJ1 = (currentJ1 + direction * jogStep).coerceIn(j1Min, j1Max)
        if (newJ1 == currentJ1) return // already at a real limit - no-op, not a clamped-but-sent command

        val newJoints = JOINT_NAMES.associateWith { name -> if (name == "j1") newJ1 else robot.joints.optDouble(name, 0.0) }
        val jointsParam = JSONObject()
        JOINT_NAMES.forEach { name -> jointsParam.put(name, newJoints.getValue(name)) }
        // axis:'x'/amount:0 is a real no-op on the server's own robot.pos.x -
        // the real desired state is the `joints` override, same sanctioned
        // mechanism jogXYZ() already uses above.
        val params = JSONObject().put("axis", "x").put("amount", 0.0).put("target", "robot").put("joints", jointsParam)
        sendAtomicCommand("jog", params) { r -> r.setJoint("j1", newJ1) }
    }

    /**
     * Real, server-synced jog step - was a local `var jogStep by
     * remember {...}` in ThreeDScreen.kt / `stepSize` in ControlScreen.kt,
     * so the step size shown/used here never matched STUDIO's own or
     * another Android session's. Routed through the same atomic command
     * channel every other write in this app already uses (see
     * sendAtomicCommand's own header comment), so a step chosen here is
     * both persisted AND broadcast live to every other connected client -
     * real request from live testing ("Házlo un ajuste real sincronizado").
     */
    fun setJogStep(value: Double) {
        val params = JSONObject().put("value", value)
        sendAtomicCommand("jogStep", params) { r -> r.setJogStep(value) }
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
                    if (connectionState.value == WsStatus.DISCONNECTED && discoveredServers.value.size == 1) {
                        logTelemetry("Auto-connecting to first discovered server: ${server.displayName}")
                        connectToDiscovered(server)
                    }
                }
            } finally {
                isScanning.value = false
            }
        }
    }

    /** True when the Bluetooth tab also lists devices that do not advertise the HYDRA service. */
    val bleShowAllDevices = mutableStateOf(false)

    /** Shows a Bluetooth error in the language of the phone, in the Bluetooth tab and as the last error. */
    private fun reportBleError(error: BleError) {
        val text = bleErrorMessage(getApplication(), error)
        lastBtError.value = text
        lastError.value = text
        logTelemetry("BLE error: ${error.kind}${error.status?.let { " ($it)" } ?: ""}")
    }

    private fun missingBlePermissions(needed: List<String>): List<String> {
        val app = getApplication<Application>()
        return BlePermissions.missing(needed) { ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED }
    }

    @SuppressLint("MissingPermission")
    fun scanBluetooth() {
        if (isBtScanning.value) return

        val app = getApplication<Application>()
        val adapter = (app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null) {
            isBtEnabled.value = false
            reportBleError(BleError(BleErrorKind.BLUETOOTH_UNAVAILABLE))
            return
        }
        if (!adapter.isEnabled) {
            isBtEnabled.value = false
            return
        }
        isBtEnabled.value = true

        if (missingBlePermissions(BlePermissions.scanPermissions(Build.VERSION.SDK_INT)).isNotEmpty()) {
            reportBleError(BleError(BleErrorKind.PERMISSION_DENIED))
            return
        }

        // On API 24-30, BLE scan results are silently withheld system-wide
        // if Location Services (GPS) is off at the OS level, even with the
        // runtime permission already granted - startScan() itself reports
        // no error, it just never calls onScanResult(). API 31+ (S) dropped
        // this requirement once BLUETOOTH_SCAN(neverForLocation) replaced
        // the old location-permission-based BLE scan model.
        if (BlePermissions.needsLocationServices(Build.VERSION.SDK_INT)) {
            val locationManager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager == null || !androidx.core.location.LocationManagerCompat.isLocationEnabled(locationManager)) {
                reportBleError(BleError(BleErrorKind.LOCATION_OFF))
                return
            }
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            reportBleError(BleError(BleErrorKind.SCANNER_UNAVAILABLE))
            return
        }

        lastBtError.value = null
        isBtScanning.value = true
        discoveredBtDevices.value = emptyList()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // The advertised name needs no permission; the cached device name needs
                // BLUETOOTH_CONNECT on Android 12+ and may have been revoked meanwhile.
                val name = result.scanRecord?.deviceName ?: try { result.device.name } catch (_: SecurityException) { null }
                discoveredBtDevices.value = BleDeviceList.upsert(
                    discoveredBtDevices.value,
                    BleDevice(name, result.device.address, result.rssi),
                )
            }

            override fun onScanFailed(errorCode: Int) {
                bleScanCallback = null
                isBtScanning.value = false
                reportBleError(BleError(BleErrorKind.SCAN_FAILED, errorCode))
            }
        }
        bleScanCallback = callback

        // Only devices advertising the HYDRA service, unless the user asked for everything.
        val filters = if (bleShowAllDevices.value) emptyList() else listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(BleContract.SERVICE_UUID)).build(),
        )
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        viewModelScope.launch {
            try {
                scanner.startScan(filters, settings, callback)
            } catch (e: SecurityException) {
                // BLUETOOTH_SCAN was revoked from Settings after the screen checked it.
                bleScanCallback = null
                isBtScanning.value = false
                reportBleError(BleError(BleErrorKind.PERMISSION_DENIED))
                return@launch
            }
            kotlinx.coroutines.delay(5_000L)
            stopBtScan()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBtScan() {
        if (!isBtScanning.value) return
        val bluetoothManager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner
        try {
            bleScanCallback?.let { scanner?.stopScan(it) }
        } catch (_: SecurityException) {
            // Permission revoked while scanning: the scan stops with the app anyway.
        }
        bleScanCallback = null
        isBtScanning.value = false
    }

    fun connectBle(device: BleDevice) {
        if (missingBlePermissions(BlePermissions.connectPermissions(Build.VERSION.SDK_INT)).isNotEmpty()) {
            reportBleError(BleError(BleErrorKind.PERMISSION_DENIED))
            return
        }
        stopBtScan()
        ws?.disconnect()
        bleClient?.disconnect()
        apiClient = null
        authTokenState.value = null

        lastError.value = null
        lastBtError.value = null
        connectionState.value = WsStatus.CONNECTING
        bleClient = HydraBleClient.forAndroid(
            context = getApplication(),
            deviceAddress = device.address,
            onStatus = { status ->
                // BLE reports the same three states
                connectionState.value = status
            },
            onSettings = { payload ->
                try {
                    applyState(HydraState(org.json.JSONObject(payload)))
                } catch (e: Exception) {
                    lastError.value = e.message
                }
            },
            onError = { error -> reportBleError(error) },
        ).also { it.connect() }
    }

    /** Saves the TLS choices if they are valid; otherwise says what is wrong. They apply to the next connection. */
    fun saveTlsSettings(input: TlsSettings) {
        val app = getApplication<Application>()
        when (input.validate()) {
            TlsProblem.FINGERPRINT_INVALID -> tlsSettingsMessage.value = app.getString(R.string.tls_error_fingerprint)
            TlsProblem.CA_INVALID -> tlsSettingsMessage.value = app.getString(R.string.tls_error_ca)
            null -> {
                val clean = input.normalized()
                TlsSettingsStore.current = clean
                tlsSettings.value = clean
                tlsSettingsMessage.value = app.getString(R.string.tls_saved)
                viewModelScope.launch { tlsPrefs.save(clean) }
            }
        }
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
