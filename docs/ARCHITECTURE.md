# HYDRA-UMC Android Control - Architecture

**Status: Industrial console implementation, Wi-Fi transport fully working, Bluetooth transport still a stub.**
`app/src/` contains a Kotlin + Jetpack Compose console with biometric login, persistent state caching, atomic-command networking, a home-screen emergency-stop widget, and an embedded 3D viewer.

## 1. What this app is

A native Android app (Kotlin + Jetpack Compose) that serves as a console for robots running on the
[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) platform. It talks to the same backend (HYDRA-UMC-SERVER,
Node/Express + WebSocket - a separate repo from HYDRA-UMC STUDIO's own web UI since the two were split apart) that
the web UI and HYDRA-UMC-IOS-CONTROL use, over the same
[`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-SERVER/blob/main/docs/REMOTE_API.md) contract - same design,
no code shared between the three clients.

## 2. Wi-Fi transport (primary, working today)

- **Atomic commands**: `HydraApiClient.kt` uses `POST /api/robot/:id/command` for motion (Jog, Play, Pause, Stop) and
  peripherals (Valves, Pumps). A small per-command payload instead of a full state overwrite, and it avoids two
  phones racing to write the same settings.json.
- **WebSocket `/ws`** (`HydraWebSocket.kt`): live bidirectional sync of the full application state. Auto-reconnects
  via a cancellable `reconnectJob` (`Job?`) that waits `RECONNECT_DELAY_MS` and retries unless the disconnect was
  user-initiated (`closingByUser`). `RobotViewModel.connect()` mirrors the same pattern with its own `connectJob`,
  so a second `connect()` call cancels and replaces any in-flight connection attempt instead of racing it.
- **Incoming state** (`RobotViewModel.kt`): a `settings` WebSocket message carries the full state tree and replaces
  the local one. A schema-2 `delta` message carries `{controllerId, robotId, patch}` and only the keys of the patch
  are written into that robot. Neither path merges arrays: an array in a patch replaces the stored one. (`HydraState.merge()` and
  its `mergeArrays` helper are kept for a future partial-message type; nothing calls them today.)
- **Discovery** (`Discovery.kt`): two mechanisms run side by side. (1) A deterministic subnet scanner that probes
  `GET /api/hydra-info` concurrently across the phone's own `/24`, including its own LAN IP and `127.0.0.1` - a
  direct Kotlin port of HYDRA-UMC-SUITE's `discovery.py`. (2) An NSD/mDNS listener as a secondary path, useful when
  it works but not relied upon alone since Android multicast reliability varies by AP/chipset. Server identity is
  matched purely on the presence of `remoteApiVersion` in the response, the same test `HydraApiClient.getHydraInfo()`
  uses for the manual-IP path - not the `product` field, which is just the operator's configurable server name and
  would silently drop any server its owner ever renamed away from the default.
- **State cache** (`StateCache.kt`): persists the last known state via DataStore so the dashboard has something to
  show immediately on cold start and while offline.
- **Credentials** (`AuthPrefs.kt`): username/password/token cache is stored via `androidx.security.crypto`
  (Keystore-backed AES-256-GCM), not plain DataStore/SharedPreferences.
- **Connection settings** (`ConnectionPrefs.kt`): DataStore-backed persistence of the manually entered IP/port, used
  by `SettingsScreen.kt` as a fallback when discovery doesn't find a server.

## 3. Bluetooth transport (present in code, not usable end-to-end)

`HydraBleClient.kt` mirrors `HydraApiClient`/`HydraWebSocket`'s shape (connect/observe/send) but over Android's GATT
APIs, backed by `BleDevice.kt` as the scan-result model. The Android-side transport includes robustness fixes,
but `HYDRA_SERVICE_UUID`/`HYDRA_STATE_CHAR_UUID` are still
placeholder values - there is no GATT server/protocol implemented on the CM5 side of HYDRA-UMC yet, so BLE cannot
actually talk to a robot today regardless of how solid the Android-side client code is.

## 4. Other industrial features

- **Biometric login**: `BiometricHelper.kt` + `androidx.biometric`, fingerprint/face unlock gating `LoginScreen.kt`.
- **Global STOP** (`ui/GlobalStop.kt`, `RobotViewModel.globalStop()`, `HydraApiClient.postStopAll()`): the red STOP button in
  the top bar of `MainScreen`, the round `EStopButton` of the control console and of the 3D view, the home-screen widget and the
  notification action all end in `globalStop()`. One tap, no long press, no confirmation. It POSTs `/api/stop-all` at once
  (2 s timeouts, bearer token, no body; the server ignores reservations), retries once when nobody answers, renews an expired
  session and repeats, and on a 429 stops every known robot with the per-robot `stop`. The answer (`network/StopAllResult.kt`)
  is kept in `stopAllStatus` and drawn by `StopAllBanner`: a clean result hides itself, a failed robot, an unsaved stopped state
  or a robot reserved by someone else stays visible, and `StopAllStatus.NotConfirmed` ("STOP NOT CONFIRMED") stays and cannot be
  dismissed until a later STOP succeeds.
- **Home-screen widget and notification action** (`widget/GlobalStopWidget.kt`, a classic `AppWidgetProvider` with
  `RemoteViews`; `util/NotificationHelper.kt`): a tap fires an `ACTION_GLOBAL_ESTOP` intent that reaches `MainActivity`
  (`singleTop`). `MainActivity` sets `pendingGlobalEstop`; a `LaunchedEffect` waits for the server connection (`apiClient`
  non-null, at most `GLOBAL_ESTOP_TIMEOUT_MS`, 15 s) and then calls `globalStop()`, which needs no robot list. With no connection
  it records STOP NOT CONFIRMED instead of leaving the tap silently pending. This still opens the Activity rather than sending
  the stop from a Service/WorkManager path, which remains a future implementation.
- **Mission notifications**: `util/NotificationHelper.kt`, high-priority channels for job completion / hardware
  fault alerts.
- **Real-time MJPEG streaming**: `ui/MjpegPlayer.kt` decodes frames from `/api/camera/:id/stream` onto a Compose
  `Canvas` (no WebView involved for video).
- **3D visualization**: `ui/ThreeDScreen.kt` embeds the server's own 3D scene in a WebView with `?hideUI=true` to
  hide the server-side chrome. Its `update` block pushes token/IP/robotId changes into the WebView after the first
  load (previously a new value never reached an already-loaded page).

## 5. Actual source layout

```text
app/src/main/java/com/hydraumc/control/
├── MainActivity.kt             # Entry point - splash, login flow, deferred global STOP handling
├── MainScreen.kt                # Navigation scaffold wiring the screens below
├── ui/
│   ├── SplashScreen.kt          # Animated splash screen
│   ├── LoginScreen.kt           # Biometric-aware access control
│   ├── DashboardScreen.kt       # 3D carousel + system health + module matrix
│   ├── ControlScreen.kt         # Jog controls + I/O (valves/pumps) + speed/acceleration sliders
│   ├── CameraScreen.kt          # MJPEG viewer screen wrapping MjpegPlayer
│   ├── MjpegPlayer.kt           # Canvas-based MJPEG frame decoder/renderer
│   ├── ThreeDScreen.kt          # Embedded 3D scene (WebView, headless mode)
│   ├── TelemetryScreen.kt       # Terminal-style real-time log console
│   ├── SettingsScreen.kt        # Manual IP/port + Bluetooth device picker
│   ├── UserProfileDialog.kt     # Account management + biometric toggle
│   ├── AboutDialog.kt           # Credits/version dialog
│   ├── Joystick3D.kt            # Reusable 2-axis joystick control component
│   ├── PlaybackConsole.kt       # Shared floating STOP/play/pause/stop console
│   ├── GlobalStop.kt            # Global STOP button (top bar) + result banner
│   ├── VoiceAssistantDialog.kt  # In-app mic/text button to HYDRA-UMC-VOICE-UI
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       ├── Typography.kt
│       ├── HydraButton.kt          # Shared industrial-styled button
│       └── IndustrialComponents.kt / IndustrialStyle.kt   # Shared industrial-look Compose primitives
├── kinematics/
│   └── Parol6Kinematics.kt      # Parol6-specific forward/inverse kinematics
├── model/
│   ├── HydraState.kt            # JSONObject views + delta merge logic (mergeArrays, pos.tx/ty mirroring)
│   └── BleDevice.kt             # Discovered BLE device (name/address/rssi)
├── network/
│   ├── HydraApiClient.kt        # REST client, atomic command support
│   ├── HydraWebSocket.kt        # Live sync, echo-guard, reconnectJob-based auto-reconnect
│   ├── HydraBleClient.kt        # GATT client mirroring the REST/WS client shape - UUIDs still placeholders
│   ├── Discovery.kt             # Subnet scan + NSD listener for server discovery
│   ├── StateCache.kt            # Persistent DataStore state storage (offline/cold-start view)
│   ├── StopAllResult.kt         # Global STOP answer (per-robot result) and the status the banner shows
│   ├── AuthPrefs.kt             # Encrypted (security-crypto) credential + biometric settings persistence
│   └── ConnectionPrefs.kt       # DataStore persistence for manually entered IP/port
├── update/
│   ├── GitHubReleaseUpdater.kt  # Safe GitHub Release update client - see docs/GITHUB_RELEASE_UPDATES.md
│   ├── ReleaseMetadataParser.kt # Safe GitHub Release metadata parser
│   └── SemanticVersion.kt       # Strict semantic version parser for updates
├── util/
│   ├── BiometricHelper.kt       # Fingerprint/face authentication manager
│   ├── NotificationHelper.kt    # High-priority mission alert dispatcher
│   └── NotificationPrefs.kt     # Persistent storage for the in-app notifications toggle
├── wear/
│   ├── BoundedRequestScope.kt      # Per-requestId in-flight job scope; a superseded attempt is cancelled
│   ├── WatchCompanionProtocol.kt   # Watch companion version-status wire contract
│   ├── WatchVoiceRelayContract.kt  # Authenticated Watch voice relay wire contract
│   └── WatchVoiceRelayService.kt   # Wear OS voice relay service - see docs/WATCH_VOICE_RELAY.md
├── widget/
│   └── GlobalStopWidget.kt      # Home-screen widget (RemoteViews), launches ACTION_GLOBAL_ESTOP -> globalStop()
└── viewmodel/
    ├── AppUpdateViewModel.kt    # Lifecycle-aware application update state
    └── RobotViewModel.kt        # Central orchestration: connectJob-guarded connect/reconnect, metrics polling
```

## 6. Build tooling

The project targets **Android 16 (API 36 compile) / Android 15 (API 35 target)** - `targetSdk` intentionally trails `compileSdk`
per its own comment ("for stable runtime behavior"); confirm with the owner if that should change. It builds with
**Gradle 9.7.0** and **JDK/toolchain 21** (`sourceCompatibility`/`targetCompatibility` = `VERSION_21`,
`gradle-daemon-jvm.properties` toolchainVersion = 21 - this is now actually applied, not just claimed).

- **Build scripts**: `build-android.bat/sh`, localized in English, with pre-flight environment checks.
- **Key dependencies**: `OkHttp 4.10`, `DataStore Preferences 1.1.7`, `Biometric 1.2.0-alpha05`, `Media 1.7.0`,
  `androidx.security.crypto`, `Glance AppWidget 1.1.1` (home-screen widget), `Navigation Compose`,
  `kotlinx.coroutines`.
- **Tests**: JUnit + Robolectric + Roborazzi dependencies are declared; the default Android Studio template package
  (`com.example`) is still there (including the Roborazzi screenshot test), but real `com.hydraumc.control` unit
  tests now exist too - `Parol6KinematicsTest.kt`, `RobotViewContractTest.kt`, `HydraWebSocketTest.kt`,
  `SubHeaderRowLayoutTest.kt`, `MjpegStreamParserTest.kt`, `GitHubReleaseUpdaterTest.kt`,
  `ReleaseMetadataParserTest.kt`, `SemanticVersionTest.kt`, `RobotViewModelSendAtomicCommandTest.kt`,
  `RobotViewModelSendVoiceTurnTest.kt`, `BoundedRequestScopeTest.kt`, and `WatchCompanionProtocolTest.kt`, `JoystickHoldTest.kt`, `HydraApiClientErrorsTest.kt` and `RobotViewModelCommandErrorsTest.kt`
  (90 `@Test` methods total across those files plus the `com.example` template, all passing via
  `./gradlew testDebugUnitTest`).

## 7. Relationship to the rest of the ecosystem

The app is synchronized with **HYDRA-UMC-SERVER** (backend) and **URTC** (tooling). It does not implement the URTC
tool standard itself and relies on the server's own kinematics (IK) support for autonomous robot movement independent of any
active browser session.
