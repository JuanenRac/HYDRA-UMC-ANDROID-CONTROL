<p align="center">
  <img src="images/HYDRA_UMC_BANNER.svg" alt="HYDRA-UMC-ANDROID-CONTROL banner" width="100%">
</p>

# 📱 HYDRA-UMC CONTROL

<p align="center">
  🇺🇸 <b>English</b> |
  <a href="README_spa.md">🇪🇸 Español</a> |
  <a href="README_fra.md">🇫🇷 Français</a> |
  <a href="README_ita.md">🇮🇹 Italiano</a> |
  <a href="README_deu.md">🇩🇪 Deutsch</a> |
  <a href="README_zho.md">🇨🇳 简体中文</a> |
  <a href="README_jpn.md">🇯🇵 日本語</a>
</p>


<p align="left">
  <img src="https://img.shields.io/badge/License-GPL%203.0-blue.svg" alt="GPL 3.0">
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF.svg" alt="Kotlin">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg" alt="Compose">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84.svg" alt="Android">
</p>


A native Android app (Kotlin + Jetpack Compose) that controls a robot on the [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) platform over Wi-Fi or Bluetooth, speaking the exact same [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-SERVER/blob/main/docs/REMOTE_API.md) contract [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) uses - discovery, full-state read/write, and live WebSocket sync against a running [HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER) backend (the same one [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)'s own web dashboard talks to). Direct Android counterpart to [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL). See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design.

## 🏗️ What's implemented

- **Access Control & Biometrics** (`ui/LoginScreen.kt`, `util/BiometricHelper.kt`) - Professional login system with **Fingerprint and Face Unlock** support (`androidx.biometric`), plus IP/port fields right on the same screen so a server can be pointed at without a separate trip through Settings first. Includes "Remember me" functionality, a secure **Logout** mechanism, and fully localized in **5 languages**. The cached username/password/token (`network/AuthPrefs.kt`) live in Keystore-backed **EncryptedSharedPreferences** (AES256-GCM), not plaintext - every server in this ecosystem seeds a default `admin`/`admin` account on first start, with additional lower-privilege **operator** accounts creatable server-side from Config > Users.
- **Offline Mode & State Cache** (`network/StateCache.kt`) - Integrated persistence engine using **DataStore**. The app automatically caches the last known system state, allowing for instant dashboard viewing and configuration audits even without an active Wi-Fi connection.
- **Mission Notifications & Alerts** (`util/NotificationHelper.kt`) - Industrial-grade alerting system. Sends high-priority push notifications when a robot completes a job sequence or if critical hardware events occur, ensuring the operator is informed even when the app is in the background.
- **Industrial Telemetry Terminal** (`ui/TelemetryScreen.kt`) - A dedicated real-time log viewer with a terminal-style interface. Tracks system events, REST/WebSocket synchronization, and provides color-coded diagnostics (Matrix Green for success, Industrial Red for errors).
- **Advanced Dashboard** (`ui/DashboardScreen.kt`) - High-fidelity **3D Horizontal Carousel** with perspective swipe effects. Displays enriched robot metadata: **Manufacturer** (Source Robotics, Annin, Universal Robots, AgileX, etc.), **Robot Role** (CNC, Laser, PnP), and an **Industrial Module Matrix** with live status for CAM, XY, ATC, PNP, CNC, LSR, BED, VAC, and RCK modules.
- **System Health Monitor** (`ui/DashboardScreen.kt`) - Real-time metrics for the connected Compute Module 5, including **Hostname**, **Formatted Uptime** (e.g., "2d 4h 15m"), and active counts for controllers and robots.
- **Enhanced Manual Control** (`ui/ControlScreen.kt`) - Features a professional vertical layout with **50% larger Joystick buttons** for maximum precision. Includes a **Job/Trajectory Selector** to browse and execute files directly from the server.
- **Safety & Playback Panel** (`ui/ControlScreen.kt`) - Fixed bottom control bar housing the **E-STOP (Emergency Stop)**, **Start**, **Pause**, and **Stop** buttons. These controls are always visible and feature **Haptic Feedback** for physical sensory confirmation.
- **3D View** (`ui/ThreeDScreen.kt`) - Embeds HYDRA-UMC STUDIO's own real-time 3D viewport in a WebView (`?hideUI=true&robotId=&token=`), rather than a native renderer - `ui/NativeThreeDScreen.kt` is an unfinished Google Filament experiment (declared but not routed to from navigation, and has no `.glb` asset loading yet), kept in the tree for whoever picks that back up later but not the active code path. The WebView approach gets the real, currently-shipping STUDIO 3D scene (every real robot mesh/kinematics) for free instead of reimplementing it natively - the tradeoff is WebView rendering overhead, not battery-optimal, but functionally complete today.
- **Real-time Octal Vision** (`ui/CameraScreen.kt`, `ui/MjpegPlayer.kt`) - Industrial-grade **Native MJPEG Streamer**. Features an autonomous background parser and Canvas-based renderer for zero-latency video telemetry, a clear "Camera Disabled" state (instead of a silently blank feed) when a robot's vision system is off, and a switch to turn a robot's camera on/off directly from the server. Supports automatic **Picture-in-Picture (PIP)** overlays in the manual control screen, mapped to specific robots via the server's camera configuration.
- **Smart Discovery & Connectivity** (`network/Discovery.kt`, `network/HydraApiClient.kt`, `network/HydraWebSocket.kt`) - Concurrent subnet scan against every candidate host on the phone's own /24 (including the phone's own LAN IP and localhost, not just the other hosts), probing `GET /api/hydra-info` and identifying a real server purely by the presence of `remoteApiVersion` - the same check the manual-IP path uses, so a server whose owner renamed it away from the default product string is still found. An **NsdManager** (mDNS/Bonjour) listener runs alongside it - the server does announce itself as `_hydra._tcp` (`bonjour-service`), and `MainActivity` requests the runtime location/nearby-devices permission this needs up front (a manifest declaration alone never grants it on API 23+) - but the subnet scan stays the primary path since multicast reachability on Wi-Fi is inherently less reliable than a plain HTTP probe. The app automatically activates WiFi on startup, scans the local factory network, and performs a **Zero-Click Auto-connect** to the first available HYDRA-UMC server.
- **Secure Industrial Access** (`network/HydraApiClient.kt`, `ui/LoginScreen.kt`) - Professional security layer using **JWT (JSON Web Tokens)**. Every control command (Jog, Play, E-STOP) is validated by the server using signed tokens, sent over the atomic `POST /api/robot/:id/command` endpoint (see Atomic Command Sync below) - works for either the `admin` or `operator` role, unlike a full `POST /api/settings` write (admin-only server-side). Every request also carries an `X-Hydra-Client: android` header so the server's own Config > Remote Access tab can allow/block this app independently of SUITE/iOS. Seamlessly integrated with **Biometric Authentication** (Fingerprint/Face) for secure token renewal. A WebSocket closed with code `1008` (invalid/expired token) is treated as "sign in again," not retried in a reconnect loop (`network/HydraWebSocket.kt`).
- **Atomic Command Sync** (`viewmodel/RobotViewModel.kt`'s own `sendAtomicCommand()`) - Every write (enable/disable/play/pause/stop/jog/jogStep/valve/pump/speed/vision/tool) sends a small, single-robot atomic command instead of the entire settings tree - the server computes which combined robots are also affected, persists to disk, and broadcasts to every other connected client on its own. Enable/Disable propagates to a robot's own `combinedWith` siblings the same way Play/Pause/Stop does, since all of them share the same affected-robots computation.
- **Emergency Management Widget** (`widget/GlobalStopWidget.kt`) - Dedicated **Home Screen Widget** for critical safety. Provides a high-visibility, instant-access **Global E-STOP** button to freeze all robotic operations in the swarm without needing to open the app - reliably waits for the robot roster to actually load before acting, even from a fully cold start (process not already running).
- **Industrial Haptics & Safety** (`ui/ControlScreen.kt`) - Advanced sensory feedback system. Features real **Long-Press Protection** on the E-STOP and STOP buttons (a quick tap does nothing but a short buzz + hint; only a genuine hold sends the command) and differentiated haptic signatures (Success, Error, and Emergency pulses) to provide physical confirmation to the operator in noisy environments.
- **In-App Update Channel** (`update/GitHubReleaseUpdater.kt`, `update/ReleaseMetadataParser.kt`, `update/SemanticVersion.kt`) - Checks the latest stable GitHub Release on launch and from **Settings → Updates**; downloads only the exact `HYDRA-UMC-ANDROID-CONTROL-release.apk` asset off a non-draft, non-prerelease tag, never auto-installs - Android's own package installer asks for final consent. Full release contract: [`docs/GITHUB_RELEASE_UPDATES.md`](docs/GITHUB_RELEASE_UPDATES.md).
- **Paired Wear OS Companion & Voice Relay** (`wear/WatchVoiceRelayService.kt`, `wear/WatchCompanionProtocol.kt`) - Relays an already-recognized, user-initiated voice turn from the paired [HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH) app through this app's own authenticated Server session to `HYDRA-UMC-VOICE-UI`, returning a typed reply to the Watch; neither this relay nor the watch system-status card ever issues a robot command or touches `HydraState` directly - a motion-related reply is explicitly marked `requiresConfirmation` and must go through a primary control UI. Full contract: [`docs/WATCH_VOICE_RELAY.md`](docs/WATCH_VOICE_RELAY.md).
- **Toolchain & Project Quality** - AGP 9.3.1, Kotlin 2.2.10, Gradle 9.7.0, compileSdk 36, **JDK 21** (`compileOptions`, `gradle-daemon-jvm.properties`, and both `.idea/`/`.vscode/` project files all actually target it, not just this line of documentation). Clean build output with zero warnings, optimized R8 production variants, and advanced **Roborazzi** screenshot testing.

**Status: Wi-Fi, Bluetooth, Biometrics, and Notifications implemented.** The app is a high-grade industrial console ready for mission-critical robot operation.

## 🚀 Building

Requires **a JDK 21 specifically** and the Android SDK.

1. Install [Android Studio](https://developer.android.com/studio).
2. Open the project root and let the Gradle sync finish.
3. Connect a device and press ▶️ Run, or use the scripts below.

### 🛠️ Build + Install Scripts

The fastest path from a terminal at the repo root - builds the debug APK, lists connected devices via `adb`, and installs it in one go:

```bash
./build-android.sh     # Linux/macOS
build-android.bat      # Windows
```

If `adb` isn't on `PATH`, the script still finishes the build and prints where the APK landed so it can be installed by hand.

### ⚙️ Manual Build

Equivalent steps without the scripts, for CI or a plain terminal:

```bash
./gradlew assembleDebug        # Linux/macOS
gradlew.bat assembleDebug      # Windows
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Install it with `adb install -r -d app/build/outputs/apk/debug/app-debug.apk`, or transfer it to the device manually. Swap `assembleDebug` for `assembleRelease` for a release build - it currently signs with the debug key (`app/build.gradle.kts`'s own `release` block, kept that way for easy testing), so it installs fine but isn't ready for distribution as-is.

## 🔢 Versioning

This repo follows an ecosystem-wide policy: the version bumps automatically on **every real build**, no manual editing of `app/build.gradle.kts`'s `versionName`/`versionCode`. `app/version.properties` holds the current `versionMajor`/`versionMinor`/`versionPatch`/`versionCode`; `app/build.gradle.kts` reads it, bumps it, and rewrites it at Gradle **configuration** time - which runs on every real build (`assembleDebug`, `compileDebugKotlin`, an IDE sync, ...) - so the produced APK always carries a number strictly newer than the last one:

- **Patch, odometer-style (base 10):** +1 on every build; once it would exceed 9 it resets to 0 and minor gets +1 instead - e.g. `0.0.9` -> `0.1.0`. Major is never touched automatically.
- **`versionCode`:** a plain monotonic counter, +1 on every build, no carry - Android requires it to strictly increase across every build that ever ships.

The running version is visible live in the **About** dialog (`BuildConfig.VERSION_NAME`, reading the same `versionName` Gradle just computed). See [CHANGELOG.md](CHANGELOG.md) for the version history.

## 📲 Testing against a live server

1. Run the backend: `cd HYDRA-UMC-SERVER && npm run dev` (Port 3000) - this is the actual REST/WS API this app talks to (see Related Projects below); `HYDRA-UMC-STUDIO`'s own `npm run dev` only starts its Vite frontend dev server (port 5173) against that same backend, it isn't the API server itself.
2. Connect your Android device to the same Wi-Fi.
3. Use the **Global Server Selector** or enter the IP manually in the header.
4. **Biometrics:** Enable "Biometric Login" in your User Profile to skip the password screen on the next launch.

## 🩺 Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| No Notifications | Permission denied | Grant "Notifications" permission in Android settings for this app |
| No Biometrics | Hardware not set | Ensure you have a Fingerprint/Face registered in your Android System Security |
| Robot won't move | Browser cerebral link | Keep a HYDRA-UMC STUDIO browser tab open for IK processing |
| Bluetooth disabled | Physical chip off | Use the "ENABLE SYSTEM BT" 3D button in the app |

## 📂 Repository Structure

```text
HYDRA-UMC-ANDROID-CONTROL/
├── app/
│   ├── build.gradle.kts          # App module Gradle config - AGP/Kotlin/Compose versions, dependencies, debug-signed release build type
│   ├── version.properties        # Odometer-versioned app version + Android versionCode, kept in sync by bump_manifest_version.py/bump_version_code.py
│   ├── proguard-rules.pro        # Release-build code shrinking/obfuscation rules
│   └── src/main/
│       ├── AndroidManifest.xml   # Permissions, activity/receiver declarations, usesCleartextTraffic (plain-HTTP LAN server, no TLS)
│       ├── java/com/hydraumc/control/
│       │   ├── MainActivity.kt          # Entry point - splash, login/main screen gating, cold-start-safe global E-STOP handling
│       │   ├── MainScreen.kt            # Bottom-nav scaffold, top bar (server selector, profile, telemetry, settings)
│       │   ├── kinematics/
│       │   │   └── Parol6Kinematics.kt   # Parol6-specific forward/inverse kinematics
│       │   ├── model/
│       │   │   ├── BleDevice.kt          # Bluetooth LE scan result data class
│       │   │   └── HydraState.kt         # settings.json field-by-field mirror (RobotView/ControllerView/JobView) + ServerInfo discovery model
│       │   ├── network/
│       │   │   ├── AuthPrefs.kt           # Encrypted (AES256-GCM) credential/session storage
│       │   │   ├── ConnectionPrefs.kt     # Persisted server IP/port (DataStore Preferences)
│       │   │   ├── Discovery.kt           # Concurrent /24 subnet scan (primary) + NSD/mDNS listener (secondary) for finding a server on the LAN
│       │   │   ├── HydraApiClient.kt      # REST client - login, settings read/write, atomic robot commands, system metrics
│       │   │   ├── HydraBleClient.kt      # Bluetooth GATT client, alternative transport to Wi-Fi
│       │   │   ├── HydraWebSocket.kt      # Live state-delta push over WS, reconnect handling
│       │   │   └── StateCache.kt          # Last-known-state cache (DataStore) for offline dashboard viewing
│       │   ├── ui/
│       │   │   ├── AboutDialog.kt          # App/version info dialog
│       │   │   ├── CameraScreen.kt         # Per-robot MJPEG camera feed + vision on/off switch
│       │   │   ├── ControlScreen.kt        # Manual jog controls, E-STOP/play/pause/stop with long-press protection
│       │   │   ├── DashboardScreen.kt      # 3D carousel robot picker + system health + module matrix
│       │   │   ├── Joystick3D.kt           # Reusable 2-axis joystick control component
│       │   │   ├── LoginScreen.kt          # Username/password + IP/port entry, biometric login
│       │   │   ├── MjpegPlayer.kt          # MJPEG stream parser + Canvas renderer
│       │   │   ├── NativeThreeDScreen.kt   # Google Filament native 3D visor - not wired into navigation yet, no .glb pipeline
│       │   │   ├── PlaybackConsole.kt      # Shared floating E-STOP/play/pause/stop console
│       │   │   ├── SettingsScreen.kt       # Wi-Fi/Bluetooth scan UI, connection settings
│       │   │   ├── SplashScreen.kt         # Custom Compose splash screen
│       │   │   ├── TelemetryScreen.kt      # Terminal-style event/sync log viewer
│       │   │   ├── ThreeDScreen.kt         # Real 3D viewport - WebView embedding STUDIO's own headless 3D scene
│       │   │   ├── UserProfileDialog.kt    # Profile edit + biometric toggle dialog
│       │   │   └── theme/
│       │   │       ├── Color.kt, Theme.kt, Typography.kt   # Material 3 color scheme, theme wrapper, type scale
│       │   │       └── HydraButton.kt, IndustrialComponents.kt, IndustrialStyle.kt   # Shared industrial-styled UI building blocks
│       │   ├── update/
│       │   │   ├── GitHubReleaseUpdater.kt   # Safe GitHub Release update client
│       │   │   ├── ReleaseMetadataParser.kt  # Safe GitHub Release metadata parser
│       │   │   └── SemanticVersion.kt        # Strict semantic version parser for updates
│       │   ├── util/
│       │   │   ├── BiometricHelper.kt      # androidx.biometric prompt wrapper
│       │   │   ├── NotificationHelper.kt   # Job-complete/safety push notifications
│       │   │   └── NotificationPrefs.kt    # Persistent storage for the in-app notifications toggle
│       │   ├── viewmodel/
│       │   │   ├── AppUpdateViewModel.kt   # Lifecycle-aware application update state
│       │   │   └── RobotViewModel.kt   # Shared ViewModel - networking, auth, discovery, atomic command dispatch, all UI state
│       │   ├── wear/
│       │   │   ├── WatchCompanionProtocol.kt    # Watch companion version-status wire contract
│       │   │   ├── WatchVoiceRelayContract.kt   # Authenticated Watch voice relay wire contract
│       │   │   └── WatchVoiceRelayService.kt    # Wear OS voice relay service
│       │   └── widget/
│       │       └── GlobalStopWidget.kt # Home-screen widget for a global E-STOP without opening the app
│       └── res/
│           ├── drawable/, layout/, mipmap*/, xml/   # Icons, widget layout, launcher icons, backup/data-extraction rules
│           └── values/, values-es/, values-de/, values-fr/, values-it/, values-ja/, values-zh/   # Strings in 7 languages, colors, theme
├── docs/
│   ├── ARCHITECTURE.md              # Design/architecture notes
│   ├── GITHUB_RELEASE_UPDATES.md    # In-app update check/download/install flow
│   └── WATCH_VOICE_RELAY.md         # Watch-to-phone-to-server voice relay contract
├── images/                       # README banner + splash screen source assets
├── tools/
│   ├── build_test.py             # Build/compile check without bumping version
│   └── ci_validate.py            # Manifest/CHANGELOG/docs validation used by CI
├── dist/                         # Signed release APK output (gitignored)
├── build-android.bat / .sh       # One-shot build + adb install convenience scripts
├── build-test.bat / .sh          # Non-versioning build/compile check
├── prepare-github-release.bat / .sh  # Builds a privately signed, stable release APK without bumping version
├── publish-github-release.ps1 / .sh  # Local-only: publishes dist/'s APK as a GitHub Release
├── bump_manifest_version.py      # Syncs hydra-umc.project.json's version to the native one (--sync)
├── bump_version_code.py          # Increments Android's own versionCode counter in app/version.properties
├── gradlew, gradlew.bat          # Gradle wrapper
├── build.gradle.kts, settings.gradle.kts, gradle.properties   # Root Gradle project config
├── local.properties              # Local Android SDK path (machine-specific, not committed)
├── keystore.properties.example   # Private release signing configuration template
├── .env.example                  # Example environment variables
├── metadata.json                 # App Store listing metadata (name/description)
├── README.md                     # This file
├── README_spa.md / README_ita.md / README_fra.md / README_deu.md / README_zho.md / README_jpn.md   # Translations
└── LICENSE                       # GPL-3.0
```

## 🔗 Related Projects

This project is part of the HYDRA-UMC robotics ecosystem by the same author (JuanenRac / Electro Hobby 3D). Worth knowing about, since a request might actually be about one of these rather than this repository.

**Parent Project**
- **[HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER)** — the real headless backend (REST/WebSocket) every control client actually talks to; the backend this app's own discovery, auth, and WebSocket sync all run against.

**Sibling Projects** — also talk to HYDRA-UMC-SERVER's own API, each their own client
- **[HYDRA-UMC-STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)** — web control dashboard with real-time multi-robot 3D visualization; its own 3D viewport is embedded directly in this app's 3D View screen via WebView.
- **[HYDRA-UMC-SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE)** — desktop (PySide6) swarm command center for multiple servers at once, packaged as a standalone executable; speaks the exact same `REMOTE_API.md` contract as this app.
- **[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL)** — iOS/iPadOS control app (Flutter) with real-time WebSocket sync; this app's direct iOS/iPadOS counterpart, same feature set.
- **[HYDRA-UMC-DSI](https://github.com/JuanenRac/HYDRA-UMC-DSI)** — native touch UI for the onboard 7" DSI touchscreen, embedded on the CM5 itself.
- **[HYDRA-UMC-BRIDGE-AMR](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-AMR)** — coordination boundary for AGV/AMR fleets via a real VDA 5050 MQTT publisher.
- **[HYDRA-UMC-BRIDGE-CNC](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-CNC)** — high-level CNC-cell coordinator with real GRBL status/control-byte access.
- **[HYDRA-UMC-BRIDGE-DROIDS](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-DROIDS)** — coordination boundary for legged/humanoid droids, with a real Boston Dynamics Spot command sender.
- **[HYDRA-UMC-BRIDGE-LASER](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-LASER)** — laser-cell safety coordinator reading 3 real key/enclosure/interlock GPIO safeguards.
- **[HYDRA-UMC-BRIDGE-OPENPNP](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-OPENPNP)** — safe high-level board-flow coordinator for OpenPnP pick-and-place.
- **[HYDRA-UMC-BRIDGE-PRINTER3D](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-PRINTER3D)** — safe coordination boundary for Moonraker/Klipper 3D printers, with real gated job commands.
- **[HYDRA-UMC-BRIDGE-ROS2](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-ROS2)** — safety coordinator with a real, lazily-imported rclpy ROS 2 transport.
- **[HYDRA-UMC-BRIDGE-UAV](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-UAV)** — coordination boundary for camera-equipped UAVs, with a real MAVLink command sender.

**Directly Related**
- **[HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH)** — WearOS companion app with real haptic alerts and a paired-phone voice relay; the WearOS companion to this app, for at-a-glance robot status and control from the wrist.
- **[HYDRA-UMC-HIL-BRIDGE](https://github.com/JuanenRac/HYDRA-UMC-HIL-BRIDGE)** — real hardware-in-the-loop safety interlock routing commands between simulation and real hardware; enables remote control of the digital twin directly from this app.

**Also Part of the Ecosystem**

*Core Hardware & Platform*
- **[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)** — the physical robot-arm motherboard: CM5 host + dual-core STM32H745, orchestrating up to 8 tool arms over CAN-OTA/SPI-OTA.
- **[HYDRA-UMC-OS](https://github.com/JuanenRac/HYDRA-UMC-OS)** — reproducible Raspberry Pi OS product layer for the CM5: read-only agent, validated config/profiles, WiFi first-contact provisioning.
- **[HYDRA-UMC-SDK](https://github.com/JuanenRac/HYDRA-UMC-SDK)** — the shared JSON-Schema contract and safety-gate boundary every bridge validates its commands against.

*Core Backend & Clients*
- **[HYDRA-UMC-EDITOR-URDF](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-URDF)** — desktop graphical URDF creator/editor that pushes finished models into STUDIO's own catalog.

*URTC Tool Platform*
- **[URTC](https://github.com/JuanenRac/URTC)** — firmware for the physical Universal Robot Tool Controller PCB, 25+ tool profiles over CAN bus.
- **[URTC-FLASHER](https://github.com/JuanenRac/URTC-FLASHER)** — desktop GUI flashing tool for URTC boards, CAN-OTA plus full-chip SWD/JTAG.
- **[URTC-TESTER](https://github.com/JuanenRac/URTC-TESTER)** — desktop live CAN-bus diagnostic tool for URTC boards, one panel per tool profile.
- **[URTC-WEB-STUDIO](https://github.com/JuanenRac/URTC-WEB-STUDIO)** — browser-based alternative to URTC-TESTER via the Web Serial API, no local install needed.

*Vision AI Node (Hailo-8)*
- **[HYDRA-UMC-VISION-NODE](https://github.com/JuanenRac/HYDRA-UMC-VISION-NODE)** — integration hub for the Hailo-8 vision pipeline, with a real per-stage hardware-readiness check.
- **[HYDRA-UMC-DETECTION-HEF](https://github.com/JuanenRac/HYDRA-UMC-DETECTION-HEF)** — real compiled-model registry with Hailo-architecture/checksum safe-load verification.
- **[HYDRA-UMC-VISION-STREAMER](https://github.com/JuanenRac/HYDRA-UMC-VISION-STREAMER)** — real GStreamer pipeline + MediaMTX config generator with a real HailoRT integration boundary.
- **[HYDRA-UMC-VISUAL-SERVOING-API](https://github.com/JuanenRac/HYDRA-UMC-VISUAL-SERVOING-API)** — real Position-Based Visual Servoing correction law, safety-gated on upstream zone state.
- **[HYDRA-UMC-SAFETY-ZONES](https://github.com/JuanenRac/HYDRA-UMC-SAFETY-ZONES)** — real zone-breach checking and E-STOP requesting, with calibration-freshness enforcement.

*Cognitive AI Node (Hailo-10)*
- **[HYDRA-UMC-COGNITIVE-NODE](https://github.com/JuanenRac/HYDRA-UMC-COGNITIVE-NODE)** — integration hub for the Hailo-10 cognitive pipeline (LLM/VLA/voice orchestration).
- **[HYDRA-UMC-VLA-ENGINE](https://github.com/JuanenRac/HYDRA-UMC-VLA-ENGINE)** — real action-token encoding/decoding and trajectory generation for a Vision-Language-Action model.
- **[HYDRA-UMC-VOICE-UI](https://github.com/JuanenRac/HYDRA-UMC-VOICE-UI)** — real voice front-end (VAD + intent parser) with a bounded, confirmation-gated Watch relay.
- **[HYDRA-UMC-SEMANTIC-PLANNER](https://github.com/JuanenRac/HYDRA-UMC-SEMANTIC-PLANNER)** — real rule-based task decomposition and semantic error recovery over MCU error codes.
- **[HYDRA-UMC-DOCS-QA](https://github.com/JuanenRac/HYDRA-UMC-DOCS-QA)** — real stdlib-only TF-IDF document search over this ecosystem's own Markdown docs.

*Orchestration & Swarm*
- **[HYDRA-UMC-ORCHESTRATOR](https://github.com/JuanenRac/HYDRA-UMC-ORCHESTRATOR)** — integration hub with a real gRPC/Protobuf health-report contract and mission state machine.
- **[HYDRA-UMC-JOB-DISPATCHER](https://github.com/JuanenRac/HYDRA-UMC-JOB-DISPATCHER)** — real priority-based job queue with deduplication, over a real HTTP API.
- **[HYDRA-UMC-NODE-HEALING](https://github.com/JuanenRac/HYDRA-UMC-NODE-HEALING)** — real gRPC-based fleet health watchdog with retry/backoff and identity-mismatch detection.
- **[HYDRA-UMC-PATH-PLANNER-3D](https://github.com/JuanenRac/HYDRA-UMC-PATH-PLANNER-3D)** — real RRT-based 3D path planner with real obstacle/workspace collision validation.
- **[HYDRA-UMC-SWARM-SYNC](https://github.com/JuanenRac/HYDRA-UMC-SWARM-SYNC)** — real CRDT LWW-Element-Map state sync, property-tested for multi-cell convergence.

*Digital Twin & Simulation*
- **[HYDRA-UMC-TWIN](https://github.com/JuanenRac/HYDRA-UMC-TWIN)** — integration hub for the digital-twin engine, with a real version-compatibility sync contract.
- **[HYDRA-UMC-PHYSICS-REPLICA](https://github.com/JuanenRac/HYDRA-UMC-PHYSICS-REPLICA)** — real forward kinematics and joint-limit validation over a real URDF subset.
- **[HYDRA-UMC-SYNTHETIC-DATA-GEN](https://github.com/JuanenRac/HYDRA-UMC-SYNTHETIC-DATA-GEN)** — real procedural 2D scene generator with YOLO/COCO annotation export.

*Data & Analytics*
- **[HYDRA-UMC-DATALAKE](https://github.com/JuanenRac/HYDRA-UMC-DATALAKE)** — real sqlite3-backed time-series store with a real ingest/query HTTP API.
- **[HYDRA-UMC-ANOMALY-DETECTOR](https://github.com/JuanenRac/HYDRA-UMC-ANOMALY-DETECTOR)** — real FFT + statistical baseline anomaly detector with drift monitoring.
- **[HYDRA-UMC-PRODUCTION-REPORTS](https://github.com/JuanenRac/HYDRA-UMC-PRODUCTION-REPORTS)** — real OEE/availability calculation over DATALAKE history, with reproducible CSV export.
- **[HYDRA-UMC-TELEMETRY-COLLECTOR](https://github.com/JuanenRac/HYDRA-UMC-TELEMETRY-COLLECTOR)** — real CAN/WebSocket ingestion pipeline into DATALAKE, with sequence deduplication.

*Industrial Gateway*
- **[HYDRA-UMC-GATEWAY-INDUSTRIAL](https://github.com/JuanenRac/HYDRA-UMC-GATEWAY-INDUSTRIAL)** — integration hub relaying to industrial protocols, with a real command allowlist/backpressure layer.
- **[HYDRA-UMC-OPCUA-SERVER](https://github.com/JuanenRac/HYDRA-UMC-OPCUA-SERVER)** — real OPC-UA address space, verified with a real binary-protocol client session.
- **[HYDRA-UMC-MQTT-BROKER](https://github.com/JuanenRac/HYDRA-UMC-MQTT-BROKER)** — real MQTT broker with optional per-client authentication and topic ACLs.
- **[HYDRA-UMC-MTCONNECT-ADAPTER](https://github.com/JuanenRac/HYDRA-UMC-MTCONNECT-ADAPTER)** — real MTConnect `/probe` and `/current` XML endpoints with degraded-mode output.

*Complementary Tools & Ecosystem Operations*
- **[HYDRA-UMC-DASHBOARD-AI](https://github.com/JuanenRac/HYDRA-UMC-DASHBOARD-AI)** — Smart Summaries and Anomaly Highlighting panels over DATALAKE/ANOMALY-DETECTOR, with an honest statistical fallback.
- **[HYDRA-UMC-TOOL-CLI](https://github.com/JuanenRac/HYDRA-UMC-TOOL-CLI)** — fleet CLI with a real, stable exit-code contract, a genuine live client of HYDRA-UMC-SERVER's own API.
- **[URTC-SMART-RACK](https://github.com/JuanenRac/URTC-SMART-RACK)** — firmware for a board-mounting rack with real tool-ID decoding and Smart Idle pre-heating logic.
- **[URTC-VISION-TOOL](https://github.com/JuanenRac/URTC-VISION-TOOL)** — firmware plus a real Python vision companion for a thermal/RGB inspection tool head.
- **[HYDRA-UMC-UPDATER](https://github.com/JuanenRac/HYDRA-UMC-UPDATER)** — administrative desktop tool that discovers, clones and updates every repo in this ecosystem.
- **[HYDRA-UMC-OS-REBUILDER](https://github.com/JuanenRac/HYDRA-UMC-OS-REBUILDER)** — Windows/Linux desktop tool that builds a ready-to-flash CM5 image pre-loaded with the ecosystem's most current versions, with Raspberry-Pi-Imager-style first-boot Wi-Fi/user/SSH configuration.

---

## 📚 Documentation & Community

- **[CONTRIBUTING.md](CONTRIBUTING.md)** — tech stack and coding guidelines for a pull request.
- **[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)** — the standards of behavior expected in this community.
- **[SECURITY.md](SECURITY.md)** — how to report a vulnerability, and this project's own real security focus areas.
- **[SUPPORT.md](SUPPORT.md)** — where to ask questions and report bugs.
- **[LICENSE.md](LICENSE.md)** — this project's own license.

## 👤 AUTHOR
**JuanenRac** (Electro Hobby 3D)
📧 electrohobby3d@gmail.com
📺 [youtube.com/@electrohobby3d](https://youtube.com/@electrohobby3d)

## 📜 LICENSE

**GNU General Public License v3.0 (GPL-3.0)** for the source code - see [`LICENSE`](LICENSE).

This documentation (this README and its own translations - `README_spa.md`, `README_ita.md`, `README_fra.md`, `README_deu.md`, `README_zho.md`, `README_jpn.md`) is available under **Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)**. Full text at https://creativecommons.org/licenses/by-sa/4.0/.
