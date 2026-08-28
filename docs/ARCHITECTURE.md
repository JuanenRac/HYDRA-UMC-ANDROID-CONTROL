# HYDRA-UMC Android Control - Architecture

**Status: Industrial console implementation, Wi-Fi transport fully working, Bluetooth transport still a stub.**
`app/src/` contains a Kotlin + Jetpack Compose console with biometric login, persistent state caching, atomic-command networking, a home-screen emergency-stop widget, and an embedded 3D viewer. A native (Filament) 3D engine exists in the tree but is not wired into navigation yet.

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
- **Delta merge semantics** (`HydraState.kt`, `mergeArrays`): incoming WebSocket payloads are deltas, not full
  snapshots. Object fields overwrite by key; JSON *arrays* are merged by appending rather than replacing, and an
  **empty** array in a delta is treated as "no change to this list" rather than "clear the list" - that's how
  `RobotState` fields like `valves`/`pumps`/`combinedWith` accumulate correctly across successive partial updates
  instead of a later empty delta wiping them out.
- **Discovery** (`Discovery.kt`): two mechanisms run side by side. (1) A deterministic subnet scanner that probes
  `GET /api/hydra-info` concurrently across the phone's own `/24`, including its own LAN IP and `127.0.0.1` - a
  direct Kotlin port of HYDRA-UMC-SUITE's `discovery.py`. (2) An NSD/mDNS listener as a secondary path, useful when
  it works but not relied upon alone since Android multicast reliability varies by AP/chipset. Server identity is
  matched on the `product` field, not `remoteApiVersion`.
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
- **Home-screen Global E-STOP widget** (`widget/GlobalStopWidget.kt`, Glance `AppWidgetProvider`): a single tap
  fires an `ACTION_GLOBAL_ESTOP` intent that launches `MainActivity`. `MainActivity` doesn't stop anything itself -
  it sets a `pendingGlobalEstop` flag, and a `LaunchedEffect` keyed on `(pendingGlobalEstop, robots.value)` sends
  `stop` to every known robot once the roster is actually populated (handles the cold-start case where
  `robots.value` is still empty at tap time). A companion `LaunchedEffect` bounds that wait to
  `GLOBAL_ESTOP_TIMEOUT_MS` (15s): if the roster is still empty after that, it gives up and surfaces
  `error_global_estop_no_robots` via `robotViewModel.lastError` instead of leaving the tap silently pending forever.
  This still opens the Activity and depends on a loaded roster rather than sending the stop directly from a
  Service/WorkManager path, which remains a future implementation.
- **Mission notifications**: `util/NotificationHelper.kt`, high-priority channels for job completion / hardware
  fault alerts.
- **Real-time MJPEG streaming**: `ui/MjpegPlayer.kt` decodes frames from `/api/camera/:id/stream` onto a Compose
  `Canvas` (no WebView involved for video).
- **3D visualization**: `ui/ThreeDScreen.kt` embeds the server's own 3D scene in a WebView with `?hideUI=true` to
  hide the server-side chrome. Its `update` block pushes token/IP/robotId changes into the WebView after the first
  load (previously a new value never reached an already-loaded page).
  `ui/NativeThreeDScreen.kt` (Filament engine) is separate dead code: present in the tree, not reachable from any
  navigation route, no real `.glb` loading. Kept pending an explicit decision to resume or archive it.

## 5. Actual source layout

```text
app/src/main/java/com/hydraumc/control/
├── MainActivity.kt             # Entry point - splash, login flow, deferred Global E-STOP handling
├── MainScreen.kt                # Navigation scaffold wiring the screens below
├── ui/
│   ├── SplashScreen.kt          # Animated splash screen
│   ├── LoginScreen.kt           # Biometric-aware access control
│   ├── DashboardScreen.kt       # 3D carousel + system health + module matrix
│   ├── ControlScreen.kt         # Jog controls + I/O (valves/pumps) + speed/acceleration sliders
│   ├── CameraScreen.kt          # MJPEG viewer screen wrapping MjpegPlayer
│   ├── MjpegPlayer.kt           # Canvas-based MJPEG frame decoder/renderer
│   ├── ThreeDScreen.kt          # Embedded 3D scene (WebView, headless mode)
│   ├── NativeThreeDScreen.kt    # Filament-based 3D viewer - dead code, not routed
│   ├── TelemetryScreen.kt       # Terminal-style real-time log console
│   ├── SettingsScreen.kt        # Manual IP/port + Bluetooth device picker
│   ├── UserProfileDialog.kt     # Account management + biometric toggle
│   ├── AboutDialog.kt           # Credits/version dialog
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       ├── Typography.kt
│       ├── HydraButton.kt          # Shared industrial-styled button
│       └── IndustrialComponents.kt / IndustrialStyle.kt   # Shared industrial-look Compose primitives
├── model/
│   ├── HydraState.kt            # JSONObject views + delta merge logic (mergeArrays, pos.tx/ty mirroring)
│   └── BleDevice.kt             # Discovered BLE device (name/address/rssi)
├── network/
│   ├── HydraApiClient.kt        # REST client, atomic command support
│   ├── HydraWebSocket.kt        # Live sync, echo-guard, reconnectJob-based auto-reconnect
│   ├── HydraBleClient.kt        # GATT client mirroring the REST/WS client shape - UUIDs still placeholders
│   ├── Discovery.kt             # Subnet scan + NSD listener for server discovery
│   ├── StateCache.kt            # Persistent DataStore state storage (offline/cold-start view)
│   ├── AuthPrefs.kt             # Encrypted (security-crypto) credential + biometric settings persistence
│   └── ConnectionPrefs.kt       # DataStore persistence for manually entered IP/port
├── util/
│   ├── BiometricHelper.kt       # Fingerprint/face authentication manager
│   └── NotificationHelper.kt    # High-priority mission alert dispatcher
├── widget/
│   └── GlobalStopWidget.kt      # Home-screen Glance widget, launches ACTION_GLOBAL_ESTOP
└── viewmodel/
    └── RobotViewModel.kt        # Central orchestration: connectJob-guarded connect/reconnect, metrics polling
```

## 6. Build tooling

The project targets **Android 15 (API 36 compile / API 35 target)** - `targetSdk` intentionally trails `compileSdk`
per its own comment ("for stable runtime behavior"); confirm with the owner if that should change. It builds with
**Gradle 9.7.0** and **JDK/toolchain 21** (`sourceCompatibility`/`targetCompatibility` = `VERSION_21`,
`gradle-daemon-jvm.properties` toolchainVersion = 21 - this is now actually applied, not just claimed).

- **Build scripts**: `build-android.bat/sh`, localized in English, with pre-flight environment checks.
- **Key dependencies**: `OkHttp 4.10`, `DataStore Preferences 1.1.7`, `Biometric 1.2.0-alpha05`, `Media 1.7.0`,
  `androidx.security.crypto`, `Glance AppWidget 1.1.1` (home-screen widget), `Navigation Compose`,
  `kotlinx.coroutines`, and the `Filament 1.75.0` family (`filament-android`/`gltfio`/`filamat`/`utils`) for the
  currently-unused `NativeThreeDScreen.kt`.
- **Tests**: JUnit + Robolectric + Roborazzi dependencies are declared, but only the default Android Studio template
  test package (`com.example`) exists today - no real tests of `com.hydraumc.control` yet.

## 7. Relationship to the rest of the ecosystem

The app is synchronized with **HYDRA-UMC-SERVER** (backend) and **URTC** (tooling). It enforces the 26-tool URTC
standard and relies on the server's own kinematics (IK) support for autonomous robot movement independent of any
active browser session.
