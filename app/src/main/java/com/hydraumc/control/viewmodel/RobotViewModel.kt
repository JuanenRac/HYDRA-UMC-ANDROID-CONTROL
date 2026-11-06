// =============================================================================
// HYDRA-UMC CONTROL - Primary ViewModel managing robot state and connectivity
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.common.optStr
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
import com.hydraumc.control.network.AuthManager
import com.hydraumc.control.network.AuthPrefs
import com.hydraumc.control.network.LoginCheck
import com.hydraumc.control.network.SignInResult
import com.hydraumc.control.network.SessionRenewer
import com.hydraumc.control.network.UserProfile
import com.hydraumc.control.network.ClaimInfo
import com.hydraumc.control.network.RobotCommandContract
import com.hydraumc.control.network.RobotCommandQueue
import com.hydraumc.control.network.RobotCommandRepository
import com.hydraumc.control.network.StopAllEndpoint
import com.hydraumc.control.network.StopAllRunner
import com.hydraumc.control.network.StopAllTexts
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
import com.hydraumc.control.util.HomeReset
import com.hydraumc.control.util.DeadManJog
import com.hydraumc.control.util.PairingLink
import com.hydraumc.control.util.WorkFiles
import com.hydraumc.control.network.CommandPoster
import com.hydraumc.control.network.TlsMode
import com.hydraumc.control.wear.WatchAlert
import com.hydraumc.control.wear.WatchAlertEvent
import com.hydraumc.control.wear.WatchAlertInput
import com.hydraumc.control.wear.WatchAlertKind
import com.hydraumc.control.wear.WatchAlertRobot
import com.hydraumc.control.wear.WatchAlertRules
import com.hydraumc.control.wear.WatchAlertSink
import com.hydraumc.control.wear.WatchAlertStop
import com.hydraumc.control.wear.WearAlertSink
import com.hydraumc.control.util.NotificationHelper
import com.hydraumc.control.util.OptimisticRollback
import com.hydraumc.control.util.StaleData
import com.hydraumc.control.util.reuseUnchangedRows
import kotlinx.coroutines.launch
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

    /** HOME/RESET switch (see [HomeReset]); off until the person turns it on, the default comes from the contract. */
    val homeResetEnabled = mutableStateOf(RobotCommandContract.HOME_RESET_ENABLED_BY_DEFAULT)
    @Volatile private var homeResetTouched = false
    /** False once the server answered that it does not know `reset`; the button then explains instead of sending. */
    val homeResetServerSupports = mutableStateOf(true)
    /** The confirmation in steps of the reset; shared so the second step must follow the first. */
    val homeResetConfirmation = HomeReset.Confirmation(RobotCommandContract.HOME_RESET_CONFIRM_STEPS, RobotCommandContract.HOME_RESET_CONFIRM_WINDOW_MS)
    /**
     * The continuous jog held by a dead-man and the health of the link to the server (see [DeadManJogController]).
     * Both start from the contract: the jog off, the link indicator on, a bad link blocking the jog.
     */
    val deadMan = DeadManJogController(
        scope = viewModelScope,
        poster = CommandPoster { id, body, urgent, ifMatch ->
            (apiClient ?: throw HydraApiException("not connected")).postRobotCommand(id, body, urgent, ifMatch)
        },
        pinger = { (apiClient ?: throw HydraApiException("not connected")).pingRoundTripMs(RobotCommandContract.LINK_HEALTH_PROBE_TIMEOUT_MS) },
        situation = { robotId, switchedOn, running, linkBlocks, supports ->
            val robot = state.robotById(robotId)
            val stopStatus = stopAllStatus.value
            DeadManJog.Situation(
                switchedOn = switchedOn,
                connected = apiClient != null,
                robotOnline = robot?.online == true,
                robotEnabled = robot?.isEnabled != false,
                robotPlaying = robot?.isPlaying == true,
                stopInProgress = stopStatus is StopAllStatus.Sending || stopStatus is StopAllStatus.NotConfirmed,
                needsArming = jogNeedsArming(robotId),
                role = jwtRole(authTokenState.value),
                linkBlocks = linkBlocks,
                alreadyRunning = running,
                serverSupports = supports,
            )
        },
        say = { lastError.value = it },
        blockText = { deadManBlockText(it) },
        endText = { reason, confirmed -> deadManEndText(reason, confirmed) },
        errorText = { e ->
            val app = getApplication<Application>()
            if (e is HydraApiException) CommandErrorText.describe(app, RobotCommandContract.JOG_START, e)
            else app.getString(R.string.error_command_failed, RobotCommandContract.JOG_START)
        },
    )

    /** Pairing by QR code or link: off until the person turns it on (see [PairingLink]); [pairingOffer] waits for the person's answer. */
    val pairingEnabled = mutableStateOf(RobotCommandContract.PAIRING_ENABLED_BY_DEFAULT)
    val pairingOffer = mutableStateOf<PairingLink.Offer?>(null)
    @Volatile private var pairingFlagsLoaded = false
    private var pairingTextWaiting: String? = null

    /** Alerts pushed to the paired watch (on by default, rationed); the relays of presence and confirmation are off by default. */
    val watchAlertsEnabled = mutableStateOf(RobotCommandContract.WATCH_ALERTS_ENABLED_BY_DEFAULT)
    val presenceRelayEnabled = mutableStateOf(RobotCommandContract.PRESENCE_RELAY_ENABLED_BY_DEFAULT)
    val voiceConfirmRelayEnabled = mutableStateOf(RobotCommandContract.VOICE_CONFIRM_ENABLED_BY_DEFAULT)
    /** Where the alerts go; the Wear Data Layer in the app, replaced by a fake in the tests. */
    var watchAlertSink: WatchAlertSink = WearAlertSink(application)
    private val watchAlertRules = WatchAlertRules()
    private var watchAlertJob: kotlinx.coroutines.Job? = null

    /** The work files (trajectories) of the selected robot's folder; null before the list was read. */
    val workFiles = mutableStateOf<List<String>?>(null)
    val workBusy = mutableStateOf(false)
    /** False once the server answered that it does not know `trajectory`. */
    val workServerSupports = mutableStateOf(true)

    /** Sends every robot command: revisions, retry of a stop, `table` fallback, jogs on the wire, debounce and timings. */
    val commands = RobotCommandRepository(conditionalMode = { conditionalMode.value })

    /** The writes of each robot go out in order; a STOP skips the line and drops the jogs that wait. */
    private val commandQueue = RobotCommandQueue()
    /** The last revision seen of each robot. */
    val revisions get() = commands.revisions
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
    private val liveSocket = LiveSocket(isSwitching = { connection.isSwitching })
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

    // The connect sequence, the "switching server" flag and the metrics poll live in ConnectionManager: a new connect
    // cancels the previous one, and starting the poll again cancels the previous loop, so a reconnect (server switch,
    // manual reconnect) never leaves an old loop polling a server that is no longer the active one. Declared before the
    // init block, which may connect at once.
    private val connection = ConnectionManager(
        scope = viewModelScope,
        shouldPoll = { connectionState.value == WsStatus.CONNECTED && isAppInForeground },
        onMetrics = { metrics.value = it },
    )

    // At most one session renewal runs at a time: a rejected token shows up
    // twice (the server's error message, then the 1008 close) and in every
    // command that is in flight, and each used to start its own login.
    private var reloginJob: kotlinx.coroutines.Job? = null

    // Renewals the app may do by itself are rationed (see RenewalBudget): past that it goes back to the sign-in instead of looping.
    private val renewalBudget = com.hydraumc.control.util.RenewalBudget()

    // Sign-in, stored profile and sign-out (see AuthManager); the store is injected, so it is tested without a keystore.
    private val authManager = AuthManager(authPrefs)

    // Gets a new token by refresh token or by the saved "remember me" login; a password the server refused once is not tried again until the person signs in by hand.
    private val sessionRenewer = SessionRenewer(
        store = authPrefs,
        onToken = { authTokenState.value = it },
        log = { logTelemetry(it) },
    )

    // Jog commands currently on the wire, per robot. A press-and-hold repeats
    // every 150 ms; on a slow link the POSTs used to pile up and arrive after
    // the button was released (or after a stop). While one is in flight the
    // next tick is dropped instead of queued.

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
    private val voice = VoiceManager(
        scope = viewModelScope,
        client = { apiClient },
        reportError = { lastError.value = it },
        log = { logTelemetry(it) },
    )
    val latestVoiceAssistantReply get() = voice.latestReply
    val voiceAssistantBusy get() = voice.busy
    val latestWatchSystemStatus get() = voice.latestWatchSystemStatus

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
                if ((client != null) && !connection.isPolling &&
                    (connectionState.value == WsStatus.CONNECTED)
                ) {
                    startMetricsLoop(client)
                }
                deadMan.ensureProbe { linkProbeActive() }
            }
            Lifecycle.Event.ON_STOP -> {
                isAppInForeground = false
                // Leaving the screen ends a held continuous jog.
                deadMan.interrupt()
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
            val homeResetSaved = prefs.loadHomeReset(RobotCommandContract.HOME_RESET_ENABLED_BY_DEFAULT)
            if (!homeResetTouched) homeResetEnabled.value = homeResetSaved
            deadMan.enabled.value = prefs.loadFlag(ConnectionPrefs.Flags.DEAD_MAN_JOG, RobotCommandContract.DEAD_MAN_JOG_ENABLED_BY_DEFAULT)
            deadMan.blockOnBadLink.value = prefs.loadFlag(ConnectionPrefs.Flags.LINK_BLOCK_JOG, RobotCommandContract.LINK_HEALTH_BLOCK_JOG_ON_BAD_LINK_DEFAULT)
            deadMan.indicator.value = prefs.loadFlag(ConnectionPrefs.Flags.LINK_INDICATOR, RobotCommandContract.LINK_HEALTH_INDICATOR_ENABLED_BY_DEFAULT)
            pairingEnabled.value = prefs.loadFlag(ConnectionPrefs.Flags.PAIRING, RobotCommandContract.PAIRING_ENABLED_BY_DEFAULT)
            watchAlertsEnabled.value = prefs.loadFlag(ConnectionPrefs.Flags.WATCH_ALERTS, RobotCommandContract.WATCH_ALERTS_ENABLED_BY_DEFAULT)
            presenceRelayEnabled.value = prefs.loadFlag(ConnectionPrefs.Flags.PRESENCE_RELAY, RobotCommandContract.PRESENCE_RELAY_ENABLED_BY_DEFAULT)
            voiceConfirmRelayEnabled.value = prefs.loadFlag(ConnectionPrefs.Flags.VOICE_CONFIRM_RELAY, RobotCommandContract.VOICE_CONFIRM_ENABLED_BY_DEFAULT)
            pairingFlagsLoaded = true
            pairingTextWaiting?.let { waiting ->
                pairingTextWaiting = null
                viewModelScope.launch { receivePairing(waiting) }
            }
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

            // A stored login that cannot be read (no usable keystore) starts the app logged out instead of failing the start-up.
            val profile = try {
                authPrefs.loadAuth()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                logTelemetry("Stored login unreadable: ${e.javaClass.simpleName}")
                UserProfile(username = "", password = "", email = "", rememberMe = false, isLoggedIn = false)
            }
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
        // The same validation connect() applies: without it an empty host/port fell through silently, an invalid port
        // never surfaced error_invalid_port, and an empty credential went to the server as an empty string.
        val check = authManager.check(ipAddress.value, port.value, user, pass)
        if (check is LoginCheck.Missing) {
            lastError.value = getApplication<Application>().getString(R.string.error_enter_ip_port)
            return
        }
        if (check is LoginCheck.BadPort) {
            lastError.value = getApplication<Application>().getString(R.string.error_invalid_port, check.text)
            return
        }
        check as LoginCheck.Valid

        val client = HydraApiClient(check.host, check.port)
        apiClient = client

        viewModelScope.launch {
            logTelemetry("Logging in to ${check.host}:${check.port}...")
            when (val result = authManager.signIn(client, user, pass, remember, loginEmail.value, isBiometricEnabled.value)) {
                is SignInResult.Success -> {
                    authTokenState.value = result.token
                    isLoggedIn.value = true
                    renewalBudget.reset()
                    sessionRenewer.forgetRefusal()
                    loginUsername.value = user
                    loginPassword.value = pass
                    loginRememberMe.value = remember
                    logTelemetry("Login successful")
                    connect() // Proceed to full sync
                }
                SignInResult.Rejected -> lastError.value = "Login failed: Server rejected credentials"
                is SignInResult.Failed -> {
                    logTelemetry("Login error: ${result.log}")
                    lastError.value = result.message
                }
            }
        }
    }

    fun saveUserProfile(user: String, pass: String, email: String, biometric: Boolean = isBiometricEnabled.value) {
        loginUsername.value = user
        loginPassword.value = pass
        loginEmail.value = email
        isBiometricEnabled.value = biometric
        viewModelScope.launch {
            authManager.saveProfile(user, pass, email, biometric, loginRememberMe.value, isLoggedIn.value)
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
    fun sendVoiceTurn(transcript: String) = voice.sendTurn(transcript)

    /** Clears the last assistant reply - called when VoiceAssistantDialog closes. */
    fun clearVoiceAssistantReply() = voice.clearReply()

    /** Retrieves the safe health-card shape for the paired Wear transport. */
    fun refreshWatchSystemStatus() = voice.refreshWatchSystemStatus()

    fun logout() {
        isLoggedIn.value = false
        // The session ends for real: no live socket, no polling, no client
        // that the widget or the notification could still send commands with.
        reloginJob?.cancel()
        connection.cancelConnect()
        commandQueue.cancelAllWaiting()
        connection.stopMetrics()
        liveSocket.close()
        NotificationHelper.hideSafetyNotification(getApplication())
        connectionState.value = WsStatus.DISCONNECTED
        val client = apiClient
        apiClient = null
        authTokenState.value = null
        val keepPassword = isBiometricEnabled.value
        if (!keepPassword) loginPassword.value = ""
        viewModelScope.launch {
            // Revokes the refresh token on the server too (best effort) and wipes the stored login.
            authManager.signOut(client, keepPassword)
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
            if (!sessionRenewer.renew(client)) return false
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
            liveSocket.close()
            return
        }
        reloginJob = viewModelScope.launch {
            if (!attemptSilentRelogin()) {
                isLoggedIn.value = false
                connectionState.value = WsStatus.DISCONNECTED
                liveSocket.close()
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

    // Two connect() calls never race (a tap on a discovered server right as the scan connects by itself, a double tap):
    // ConnectionManager cancels the previous sequence, which stops at its next suspension point.

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
        // Set before the old socket closes: its close callback reads the flag.
        connection.isSwitching = true

        liveSocket.close()
        bleClient?.disconnect()
        bleClient = null

        lateinit var client: HydraApiClient
        connection.connect(
            prepare = {
                // Ensure token is carried over or reloaded from persistence
                val profile = authPrefs.loadAuth()
                val token = apiClient?.authToken ?: profile.token
                client = HydraApiClient(host, portInt)
                client.authToken = token
                client.refreshToken = apiClient?.refreshToken ?: profile.refreshToken.ifEmpty { null }
                apiClient = client
                authTokenState.value = token
                prefs.save(host, portValue)
                logTelemetry("Connecting to $host:$portInt...")
                object : ConnectSource {
                    override val usesTls: Boolean get() = client.usesTls
                    override suspend fun hydraInfo() = client.getHydraInfo()
                    override suspend fun settings() = client.getSettings()
                    override suspend fun renewSession() = sessionRenewer.renew(client)
                }
            },
            listener = object : ConnectListener {
                override fun log(message: String) = logTelemetry(message)

                override fun onInfo(info: JSONObject) {
                    val server = ServerInfo.fromHydraInfo(host, portInt, info)
                    activeServer.value = server
                    logTelemetry("Server verified: ${server.displayName}")
                    if (discoveredServers.value.none { (it.host == host) && (it.port == portInt) }) {
                        discoveredServers.value += server
                    }
                }

                override fun onReachable(usesTls: Boolean) {
                    plainRemoteHost.value = host.takeIf { TlsPolicy.isPlainRemote(it, usesTls) }
                    if (plainRemoteHost.value != null) logTelemetry("Warning: unencrypted connection to a host outside the local network")
                }

                override fun onSettings(settings: JSONObject) = applyState(HydraState(settings))

                override fun onSynced() {
                    onInitialConnectSucceeded?.invoke()
                }

                override fun onSyncFailed(error: HydraApiException) {
                    lastError.value = error.message
                    onInitialConnectFailed?.invoke()
                }

                // No socket against a server the REST sync could not reach: it would only retry forever in the
                // background. The metrics poll starts when the socket reports CONNECTED (see setupWebSocket).
                override fun openSocket() = setupWebSocket(host, portInt, client.usesTls)
            },
        )
    }

    // Debounce job for the offline-viewing disk cache write in applyState()
    // below - see that call site's own comment for why this exists.
    private var stateCacheJob: kotlinx.coroutines.Job? = null

    private fun startMetricsLoop(client: HydraApiClient) = connection.startMetrics { client.getSystemMetrics() }

    private fun setupWebSocket(host: String, port: Int, useTls: Boolean = false) {
        // Runs inside connect()'s own job, with the token that job just confirmed against the REST sync: it used to be a
        // separate coroutine that read the token from disk, so two quick connects could leave an orphan socket that kept
        // reconnecting on its own. LiveSocket closes the previous socket before it opens this one.
        val token = apiClient?.authToken.orEmpty()
        liveSocket.open(SocketTarget(host, port, useTls, if (token.isNotEmpty()) token else null), socketListener)
    }

    private val socketListener = object : SocketListener {
        override fun onStatus(status: WsStatus) {
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
                }
            }
            if (status == WsStatus.CONNECTED) {
                // Started here, after the status string above is set: the loop only runs while it reads "connected",
                // and starting it after the REST sync (before the socket opened) made it end on its first check, so
                // CPU/MEM/TEMP never appeared.
                apiClient?.let { startMetricsLoop(it) }
                deadMan.ensureProbe { linkProbeActive() }
                ensureWatchAlertLoop()
            } else if (status == WsStatus.DISCONNECTED) {
                // A held jog must not outlive the connection: its axis is stopped (the server also stops it by itself).
                deadMan.interrupt()
                deadMan.stopProbe()
            }
        }

        override fun onClosedIdle() {
            discoveredServers.value = emptyList()
        }

        // Full replace, not a merge: it reaches here for "settings" (the first message on connect and any full POST
        // /api/settings write) and for a schema-1 "delta" (a client that has not declared schema 2). A real schema-2
        // delta goes to onDelta instead. A merge would only ever append primitive-array fields (valves/pumps/
        // combinedWith), so a robot removed from a combinedWith group would keep receiving that group's commands.
        override fun onSettings(payload: JSONObject) {
            applyState(HydraState(payload))
        }

        override fun onDelta(message: JSONObject) {
            applyRobotDelta(message)
        }

        override fun onClaims(message: JSONObject) {
            applyClaimsPush(message)
        }

        // The server closed the socket with 1008 (expired or invalid token). One renewal, however many signals arrive;
        // only a failed renewal sends the user back to the login screen.
        override fun onUnauthorized() {
            renewSessionOrLogout()
        }

        override fun onError(message: String) {
            lastError.value = message
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
            // cancel-and-relaunch Job pattern as metricsJob/the connect job/
            // commands.debounce) coalesces a burst of
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
        // A stop (or safe mode) of the robot ends a continuous jog that is held.
        if (command == RobotCommandContract.STOP || command == RobotCommandContract.DISABLE) deadMan.interrupt()

        // A jog above the limit the server holds for this robot model is not sent at all (the
        // server checks it again). Checked before the optimistic change, like the client above.
        if (command == RobotCommandContract.JOG || command == RobotCommandContract.TABLE) {
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
                lastError.value = getApplication<Application>().getString(R.string.error_jog_over_limit, CommandErrorText.formatLimit(exceeded))
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

        val isJog = command == RobotCommandContract.JOG || command == RobotCommandContract.TABLE
        val send: () -> Unit = {
            if (isJog) commands.jogStarted(robotId)
            val skipsLine = command == RobotCommandContract.STOP || command == RobotCommandContract.DISABLE
            commandQueue.submit(
                viewModelScope, robotId, urgent = skipsLine, cancellable = isJog,
                // A jog that waited and was dropped by a stop: the screen goes back to what the robot really is.
                onDropped = { viewModelScope.launch { rollback(); if (isJog) commands.jogEnded(robotId) } },
            ) {
                try {
                    val answer = commands.send(robotId, command, payload) { id, body, urgent, ifMatch -> client.postRobotCommand(id, body, urgent, ifMatch) }
                    val warnings = RobotCommandRepository.warningsOf(answer)
                    if (warnings != null) {
                        // The server answered 200 but applied nothing (a speed
                        // outside its range, a malformed jog): same as a failure.
                        logTelemetry("TX not applied [$command]: $warnings")
                        lastError.value = getApplication<Application>().getString(R.string.error_command_not_applied, command, warnings)
                        rollback()
                        return@submit
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
                    if (command == RobotCommandContract.STOP) jogArmedUntilMs.value = jogArmedUntilMs.value - robotId
                    lastError.value = null
                } catch (e: HydraApiException) {
                    logTelemetry("TX Error [$command]: ${e.message}")
                    lastError.value = CommandErrorText.describe(getApplication(), command, e)
                    // The robot changed since this app read it: read it again so the screen shows what it is now.
                    if (e.isRevisionMismatch) refreshRobot(robotId)
                    // An older server does not have `reset`: remember it, so the button explains instead of sending again.
                    if (command == RobotCommandContract.RESET && isUnknownCommand(e)) homeResetServerSupports.value = false
                    if (command == RobotCommandContract.TRAJECTORY && isUnknownCommand(e)) workServerSupports.value = false
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
                    if (isJog) commands.jogEnded(robotId)
                }
            }
        }

        commands.debounce(viewModelScope, command, debounceMs, send)
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

    /** Chooses whether HOME/RESET is offered, and keeps the choice. Switching it off also drops a confirmation under way. */
    fun setHomeResetEnabled(on: Boolean) {
        homeResetTouched = true
        homeResetEnabled.value = on
        if (!on) homeResetConfirmation.cancel()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { prefs.saveHomeReset(on) }
    }

    private fun linkProbeActive(): Boolean = connectionState.value == WsStatus.CONNECTED && isAppInForeground

    fun setDeadManEnabled(on: Boolean) {
        deadMan.enabled.value = on
        if (!on) deadMan.interrupt()
        deadMan.ensureProbe { linkProbeActive() }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { prefs.saveFlag(ConnectionPrefs.Flags.DEAD_MAN_JOG, on) }
    }

    fun setLinkBlockOnBadLink(on: Boolean) {
        deadMan.blockOnBadLink.value = on
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { prefs.saveFlag(ConnectionPrefs.Flags.LINK_BLOCK_JOG, on) }
    }

    fun setLinkIndicator(on: Boolean) {
        deadMan.indicator.value = on
        deadMan.ensureProbe { linkProbeActive() }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { prefs.saveFlag(ConnectionPrefs.Flags.LINK_INDICATOR, on) }
    }

    /** The phrase for a reason the continuous jog does not start, in the language of the phone. */
    fun deadManBlockText(block: DeadManJog.Block): String = getApplication<Application>().getString(
        when (block) {
            DeadManJog.Block.NONE -> R.string.home_reset_blocked_none
            DeadManJog.Block.OFF -> R.string.dead_man_blocked_off
            DeadManJog.Block.NOT_CONNECTED -> R.string.error_not_connected
            DeadManJog.Block.OFFLINE -> R.string.dead_man_blocked_offline
            DeadManJog.Block.SAFE_MODE -> R.string.dead_man_blocked_safe_mode
            DeadManJog.Block.MOVING -> R.string.dead_man_blocked_moving
            DeadManJog.Block.STOP_PENDING -> R.string.dead_man_blocked_stop
            DeadManJog.Block.NOT_ARMED -> R.string.error_jog_not_armed
            DeadManJog.Block.ROLE -> R.string.dead_man_blocked_role
            DeadManJog.Block.BAD_LINK -> R.string.dead_man_blocked_bad_link
            DeadManJog.Block.ALREADY_RUNNING -> R.string.dead_man_blocked_already
            DeadManJog.Block.NOT_SUPPORTED -> R.string.dead_man_blocked_not_supported
        },
    )

    /** What to tell the person when a continuous jog ended on its own; null when nothing needs saying. */
    fun deadManEndText(reason: DeadManJog.EndReason, stopConfirmed: Boolean): String? {
        val app = getApplication<Application>()
        if (!stopConfirmed) return app.getString(R.string.dead_man_stop_not_confirmed)
        return when (reason) {
            DeadManJog.EndReason.RELEASED, DeadManJog.EndReason.INTERRUPTED -> null
            DeadManJog.EndReason.LINK_LOST -> app.getString(R.string.dead_man_ended_link_lost)
            DeadManJog.EndReason.TOO_LONG -> app.getString(R.string.dead_man_ended_too_long)
            DeadManJog.EndReason.BAD_LINK -> app.getString(R.string.dead_man_ended_bad_link)
            DeadManJog.EndReason.SERVER_REFUSED -> app.getString(R.string.dead_man_ended_refused)
        }
    }

    // ---- pairing by QR code or link ----------------------------------------------------------------------------

    fun setPairingEnabled(on: Boolean) {
        pairingEnabled.value = on
        if (!on) pairingOffer.value = null
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { prefs.saveFlag(ConnectionPrefs.Flags.PAIRING, on) }
    }

    /**
     * A text that may be a pairing link (the phone camera opened one, or the person pasted it). Returns false when the text is not a
     * pairing link at all. While pairing is off nothing is read: the person is told. A valid offer is only SHOWN ([pairingOffer]);
     * nothing is kept before [confirmPairing].
     */
    suspend fun receivePairing(text: String?): Boolean {
        if (!PairingLink.looksLikePairing(text)) return false
        val app = getApplication<Application>()
        if (!pairingFlagsLoaded) {
            // The switch is still being read from storage: look again when it is.
            pairingTextWaiting = text
            return true
        }
        if (!pairingEnabled.value) {
            lastError.value = app.getString(R.string.pairing_off)
            return true
        }
        when (val result = PairingLink.parse(text)) {
            is PairingLink.Result.Ok -> pairingOffer.value = result.offer
            is PairingLink.Result.Refused -> lastError.value = app.getString(
                when (result.problem) {
                    PairingLink.Problem.BAD_HOST, PairingLink.Problem.MISSING_HOST -> R.string.pairing_problem_host
                    PairingLink.Problem.BAD_PORT -> R.string.pairing_problem_port
                    PairingLink.Problem.BAD_FINGERPRINT -> R.string.pairing_problem_fingerprint
                    PairingLink.Problem.MISSING_FINGERPRINT -> R.string.pairing_problem_missing_fingerprint
                    PairingLink.Problem.BAD_NAME -> R.string.pairing_problem_name
                    PairingLink.Problem.NOT_A_PAIRING_LINK, PairingLink.Problem.EMPTY -> R.string.pairing_problem_not_link
                    else -> R.string.pairing_problem_generic
                },
            )
        }
        return true
    }

    /** The person accepted the offer on screen: the address is set, the certificate fingerprint is pinned with TLS required and the app connects. */
    fun confirmPairing() {
        val offer = pairingOffer.value ?: return
        pairingOffer.value = null
        ipAddress.value = offer.host
        port.value = offer.port.toString()
        saveTlsSettings(tlsSettings.value.copy(mode = TlsMode.REQUIRED, pinnedFingerprint = offer.fingerprint))
        connect()
    }

    fun dismissPairing() {
        pairingOffer.value = null
    }

    // ---- the watch ---------------------------------------------------------------------------------------------

    fun setWatchAlertsEnabled(on: Boolean) {
        watchAlertsEnabled.value = on
        if (on) ensureWatchAlertLoop()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { prefs.saveFlag(ConnectionPrefs.Flags.WATCH_ALERTS, on) }
    }

    fun setPresenceRelayEnabled(on: Boolean) {
        presenceRelayEnabled.value = on
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { prefs.saveFlag(ConnectionPrefs.Flags.PRESENCE_RELAY, on) }
    }

    fun setVoiceConfirmRelayEnabled(on: Boolean) {
        voiceConfirmRelayEnabled.value = on
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { prefs.saveFlag(ConnectionPrefs.Flags.VOICE_CONFIRM_RELAY, on) }
    }

    /** The sentence of an alert, in the language of the phone. */
    fun watchAlertText(event: WatchAlertEvent): String {
        val app = getApplication<Application>()
        val name = event.robotName ?: ""
        return when (event.kind) {
            WatchAlertKind.CONNECTION_LOST -> app.getString(R.string.watch_alert_connection_lost)
            WatchAlertKind.CONNECTION_RESTORED -> app.getString(R.string.watch_alert_connection_restored)
            WatchAlertKind.ROBOT_OFFLINE -> app.getString(R.string.watch_alert_robot_offline, name)
            WatchAlertKind.ROBOT_SAFE_MODE -> app.getString(R.string.watch_alert_robot_safe_mode, name)
            WatchAlertKind.STOP_NOT_CONFIRMED -> app.getString(R.string.watch_alert_stop_not_confirmed)
            WatchAlertKind.STOP_CONFIRMED -> app.getString(R.string.watch_alert_stop_confirmed)
        }
    }

    /** What the alert rules see of the plant right now. */
    fun watchAlertInput(): WatchAlertInput = WatchAlertInput(
        connected = connectionState.value == WsStatus.CONNECTED,
        robots = state.allRobots.map { WatchAlertRobot(it.id, it.name, it.online, it.isEnabled, it.isPlaying && !it.isPaused) },
        stop = when (stopAllStatus.value) {
            is StopAllStatus.Sending -> WatchAlertStop.SENDING
            is StopAllStatus.NotConfirmed -> WatchAlertStop.NOT_CONFIRMED
            is StopAllStatus.Done -> WatchAlertStop.DONE
            else -> WatchAlertStop.NONE
        },
    )

    /** One look at the plant: the alerts that deserve the wrist are sent. A failure to deliver is not an error of the app. */
    suspend fun watchAlertsTick() {
        if (!watchAlertsEnabled.value) return
        for (event in watchAlertRules.observe(watchAlertInput())) {
            watchAlertSink.send(WatchAlert.of(event.severity, watchAlertText(event)))
        }
    }

    private fun ensureWatchAlertLoop() {
        if (watchAlertJob?.isActive == true || !watchAlertsEnabled.value) return
        watchAlertJob = viewModelScope.launch {
            while (true) {
                try {
                    watchAlertsTick()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logTelemetry("Watch alert failed: ${e.javaClass.simpleName}")
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    // ---- work files (trajectories) -----------------------------------------------------------------------------

    /** The folder of [robotId]'s work files on the server, or null when the configured folder is not a plain relative path. */
    fun workFolderOf(robotId: Int): String? {
        val robot = state.robotById(robotId) ?: return null
        return WorkFiles.folderFor(state.raw.optJSONObject("worksPaths"), robotId, robot.name)
    }

    fun workBlock(robotId: Int?, tableMissing: Boolean = false): WorkFiles.Block {
        val robot = robotId?.let { state.robotById(it) }
        val stopStatus = stopAllStatus.value
        return WorkFiles.blockOf(
            WorkFiles.Situation(
                connected = apiClient != null,
                robotOnline = robot?.online == true,
                robotEnabled = robot?.isEnabled != false,
                robotPlaying = robot?.isPlaying == true,
                stopInProgress = stopStatus is StopAllStatus.Sending || stopStatus is StopAllStatus.NotConfirmed,
                role = jwtRole(authTokenState.value),
                tableMissing = tableMissing,
                serverSupports = workServerSupports.value,
            ),
        )
    }

    fun workBlockText(block: WorkFiles.Block): String = getApplication<Application>().getString(
        when (block) {
            WorkFiles.Block.NONE -> R.string.work_blocked_none
            WorkFiles.Block.NOT_CONNECTED -> R.string.error_not_connected
            WorkFiles.Block.OFFLINE -> R.string.work_blocked_offline
            WorkFiles.Block.SAFE_MODE -> R.string.work_blocked_safe_mode
            WorkFiles.Block.PLAYING -> R.string.work_blocked_playing
            WorkFiles.Block.STOP_PENDING -> R.string.work_blocked_stop
            WorkFiles.Block.ROLE -> R.string.work_blocked_role
            WorkFiles.Block.NO_TABLE -> R.string.work_blocked_no_table
            WorkFiles.Block.NOT_SUPPORTED -> R.string.work_not_supported
        },
    )

    /** Reads the names of the work files of [robotId]'s folder. */
    fun loadWorkList(robotId: Int) {
        val client = apiClient ?: run {
            lastError.value = getApplication<Application>().getString(R.string.error_not_connected)
            return
        }
        val folder = workFolderOf(robotId) ?: run {
            lastError.value = getApplication<Application>().getString(R.string.work_load_failed)
            return
        }
        workBusy.value = true
        workFiles.value = null
        viewModelScope.launch {
            try {
                workFiles.value = WorkFiles.parseIndex(client.getWorkIndexBody(folder)) ?: emptyList()
            } catch (e: HydraApiException) {
                // A folder that does not exist yet is an empty list, not an error.
                if (e.isNotFound) workFiles.value = emptyList() else lastError.value = getApplication<Application>().getString(R.string.work_load_failed)
                if (e.isSessionProblem) renewSessionOrLogout()
            } catch (e: Exception) {
                lastError.value = getApplication<Application>().getString(R.string.work_load_failed)
            } finally {
                workBusy.value = false
            }
        }
    }

    /**
     * Loads the work file [file] into [robotId] with the `trajectory` command. Nothing is read or sent when a rule refuses (the robot
     * is playing, in safe mode...); the file is checked here the way the server will check it, so a bad file never goes out.
     */
    fun selectWork(robotId: Int, file: String) {
        val app = getApplication<Application>()
        val client = apiClient ?: run {
            lastError.value = app.getString(R.string.error_not_connected)
            return
        }
        val folder = workFolderOf(robotId)
        if (folder == null || !WorkFiles.isSafeFileName(file)) {
            lastError.value = app.getString(R.string.work_load_failed)
            return
        }
        val early = workBlock(robotId)
        if (early != WorkFiles.Block.NONE) {
            lastError.value = workBlockText(early)
            return
        }
        workBusy.value = true
        viewModelScope.launch {
            try {
                when (val loaded = WorkFiles.parsePoints(client.getWorkFileBody(folder, file))) {
                    is WorkFiles.Loaded.Refused -> lastError.value = app.getString(R.string.work_invalid_file, file)
                    is WorkFiles.Loaded.Ok -> {
                        val block = workBlock(robotId, tableMissing = loaded.usesTable && state.robotById(robotId)?.hasXYTable != true)
                        if (block != WorkFiles.Block.NONE) {
                            lastError.value = workBlockText(block)
                        } else {
                            sendAtomicCommand(RobotCommandContract.TRAJECTORY, WorkFiles.trajectoryParams(loaded.points, file), explicitRobotId = robotId) { r ->
                                r.raw.put("selectedWorkFile", file)
                            }
                            lastError.value = null
                        }
                    }
                }
            } catch (e: HydraApiException) {
                lastError.value = if (e.isSessionProblem) app.getString(R.string.error_session_expired) else app.getString(R.string.work_load_failed)
                if (e.isSessionProblem) renewSessionOrLogout()
            } catch (e: Exception) {
                lastError.value = app.getString(R.string.work_load_failed)
            } finally {
                workBusy.value = false
            }
        }
    }

    /** What stops HOME/RESET of [robotId] right now, from this app's view; [HomeReset.Block.NONE] when it may go on. */
    fun homeResetBlock(robotId: Int?, nowMs: Long = System.currentTimeMillis()): HomeReset.Block {
        val robot = robotId?.let { state.robotById(it) }
        val stopStatus = stopAllStatus.value
        return HomeReset.blockOf(
            HomeReset.Situation(
                switchedOn = homeResetEnabled.value,
                connected = apiClient != null,
                robotOnline = robot?.online == true,
                robotEnabled = robot?.isEnabled != false,
                robotPlaying = robot?.isPlaying == true,
                jogInFlight = robotId != null && commands.hasJogInFlight(robotId),
                stopInProgress = stopStatus is StopAllStatus.Sending || stopStatus is StopAllStatus.NotConfirmed,
                needsArming = jogNeedsArming(robotId, nowMs),
                role = jwtRole(authTokenState.value),
                hasPose = robot?.raw?.optJSONObject("pos") != null,
                serverSupports = homeResetServerSupports.value,
            ),
        )
    }

    /** The phrase for a [HomeReset.Block], in the language of the phone. */
    fun homeResetBlockText(block: HomeReset.Block): String {
        val app = getApplication<Application>()
        return app.getString(
            when (block) {
                HomeReset.Block.NONE -> R.string.home_reset_blocked_none
                HomeReset.Block.OFF -> R.string.home_reset_blocked_off
                HomeReset.Block.NOT_CONNECTED -> R.string.error_not_connected
                HomeReset.Block.OFFLINE -> R.string.home_reset_blocked_offline
                HomeReset.Block.SAFE_MODE -> R.string.home_reset_blocked_safe_mode
                HomeReset.Block.MOVING -> R.string.home_reset_blocked_moving
                HomeReset.Block.JOG_BUSY -> R.string.home_reset_blocked_jog
                HomeReset.Block.STOP_PENDING -> R.string.home_reset_blocked_stop
                HomeReset.Block.NOT_ARMED -> R.string.error_jog_not_armed
                HomeReset.Block.ROLE -> R.string.home_reset_blocked_role
                HomeReset.Block.NO_POSE -> R.string.home_reset_blocked_no_pose
                HomeReset.Block.NOT_SUPPORTED -> R.string.home_reset_not_supported
            },
        )
    }

    /**
     * Sends the reset (the selected robot to its zero position) once the confirmation in steps is complete. The
     * screen calls it only after the last step; the checks run again here, so a robot that started moving
     * between the steps is not reset. A refusal shows its own phrase and nothing goes out.
     */
    fun homeReset() {
        val robotId = selectedRobotId.value ?: return
        val block = homeResetBlock(robotId)
        if (block != HomeReset.Block.NONE) {
            lastError.value = homeResetBlockText(block)
            return
        }
        // The pose is shown at zero at once; the server's answer (or a failure, which restores it) settles it.
        sendAtomicCommand(RobotCommandContract.RESET, JSONObject().put("target", "robot")) { r ->
            for (axis in listOf("x", "y", "z", "a", "b", "c")) r.setPosAxis(axis, 0.0)
        }
    }

    /** The server's `warnings` of a 200 answer (it applied nothing), joined; null when there are none. */
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
        commands.withRevision(robotId, "claim") { ifMatch -> client.postClaim(robotId, ifMatch = ifMatch) }
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
        commands.withRevision(robotId, "release") { ifMatch -> client.postRelease(robotId, ifMatch = ifMatch) }
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
                lastError.value = CommandErrorText.describe(getApplication(), what, e)
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
        if (deadMan.held.value.contains(robotId)) return true
        if (commands.hasJogInFlight(robotId)) return true
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
        // First of all: a held continuous jog ends and its axis is stopped.
        deadMan.interrupt()
        // The jogs that still wait their turn behind another write are dropped: they must not move a robot after the stop.
        state.allRobots.forEach { commandQueue.cancelWaitingJogs(it.id) }
        val first = apiClient
        if (first == null) {
            val reason = app.getString(R.string.error_not_connected)
            stopAllStatus.value = StopAllStatus.NotConfirmed(reason)
            lastError.value = app.getString(R.string.stop_all_not_confirmed)
            return
        }
        stopAllStatus.value = StopAllStatus.Sending
        viewModelScope.launch {
            val outcome = stopAllRunner.run(
                start = first.asStopAllEndpoint(),
                knownRobots = { state.allRobots.map { it.id to it.name } },
                relogin = { attemptSilentRelogin() },
                currentEndpoint = { apiClient?.asStopAllEndpoint() },
                onSessionLost = { renewSessionOrLogout() },
            )
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

    private val stopAllRunner by lazy {
        val app = getApplication<Application>()
        StopAllRunner(commands, StopAllTexts(app.getString(R.string.error_session_expired), app.getString(R.string.error_robot_not_found)))
    }

    private fun HydraApiClient.asStopAllEndpoint(): StopAllEndpoint {
        val client = this
        return object : StopAllEndpoint {
            override suspend fun stopAll() = client.postStopAll()
            override suspend fun stopRobot(robotId: Int, payload: JSONObject, urgent: Boolean, ifMatch: String?) =
                client.postRobotCommand(robotId, payload, urgent, ifMatch)
        }
    }

    /** Hides the result banner of the global STOP; a STOP that is not confirmed cannot be hidden. */
    fun dismissStopAllStatus() {
        if (stopAllStatus.value is StopAllStatus.Done) stopAllStatus.value = null
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
            sendAtomicCommand(RobotCommandContract.TABLE, move) { r -> r.setXyTableAxis(axis, r.xyTablePos.optDouble(axis, 0.0) + amount) }
            return
        }
        val params = JSONObject().put("axis", axis).put("amount", amount).put("target", target)
        sendAtomicCommand(RobotCommandContract.JOG, params) { r ->
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
                sendAtomicCommand(RobotCommandContract.JOG, params) { r ->
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
        sendAtomicCommand(RobotCommandContract.JOG, params) { r -> r.setJoint("j1", newJ1) }
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
        sendAtomicCommand(RobotCommandContract.JOG_STEP, params) { r -> r.setJogStep(value) }
    }

    fun toggleValve(index: Int) {
        val targetRobot = selectedRobotId.value?.let { state.robotById(it) } ?: return
        val newState = !targetRobot.valves.optBoolean(index, false)
        val params = JSONObject().put("index", index).put("state", newState)
        sendAtomicCommand(RobotCommandContract.VALVE, params) { it.setValve(index, newState) }
    }

    fun togglePump(index: Int) {
        val targetRobot = selectedRobotId.value?.let { state.robotById(it) } ?: return
        val newState = !targetRobot.pumps.optBoolean(index, false)
        val params = JSONObject().put("index", index).put("state", newState)
        sendAtomicCommand(RobotCommandContract.PUMP, params) { it.setPump(index, newState) }
    }

    /** Debounced (300ms) - a dragged slider fires this many times a second. */
    fun setSpeed(speed: Double, acceleration: Double) {
        val params = JSONObject().put("speed", speed).put("acceleration", acceleration)
        sendAtomicCommand(RobotCommandContract.SPEED, params, debounceMs = 300) { r ->
            r.setSpeed(speed)
            r.setAcceleration(acceleration)
        }
    }

    /** Toggles a robot's vision system on/off from the Camera screen (server.ts's own "vision" command). Takes an explicit robotId since the camera being browsed isn't necessarily the globally selected control robot. */
    fun setVisionEnabled(robotId: Int, enabled: Boolean) {
        val params = JSONObject().put("enabled", enabled)
        sendAtomicCommand(RobotCommandContract.VISION, params, explicitRobotId = robotId) { r ->
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
        sendAtomicCommand(RobotCommandContract.TOOL, params) { it.setTool(toolName) }
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

    private val bleScan by lazy {
        BleScanController(
            context = getApplication(),
            scope = viewModelScope,
            devices = discoveredBtDevices,
            scanning = isBtScanning,
            bluetoothEnabled = isBtEnabled,
            showAllDevices = { bleShowAllDevices.value },
            onError = { reportBleError(it) },
            onScanStarted = { lastBtError.value = null },
        )
    }

    fun scanBluetooth() = bleScan.start()

    private fun stopBtScan() = bleScan.stop()

    fun connectBle(device: BleDevice) {
        if (bleScan.missingPermissions(BlePermissions.connectPermissions(Build.VERSION.SDK_INT)).isNotEmpty()) {
            reportBleError(BleError(BleErrorKind.PERMISSION_DENIED))
            return
        }
        stopBtScan()
        liveSocket.close()
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
        connection.cancelConnect()
        connection.stopMetrics()
        stateCacheJob?.cancel()
        liveSocket.close()
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
