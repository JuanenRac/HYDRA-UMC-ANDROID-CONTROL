<!-- =============================================================================
HYDRA-UMC-ANDROID-CONTROL - Android operator console for a HYDRA-UMC server: control, cameras, 3D view and a global STOP
Copyright (C) JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
GPL-3.0-or-later - see LICENSE
============================================================================= -->

<p align="center">
  <img src="images/HYDRA_UMC_BANNER.svg" alt="HYDRA-UMC-ANDROID-CONTROL banner" width="100%">
</p>

# 📱 HYDRA-UMC-ANDROID-CONTROL

<p align="center">🇺🇸 <b>English</b> | <a href="README_spa.md">🇪🇸 Español</a> | <a href="README_fra.md">🇫🇷 Français</a> | <a href="README_ita.md">🇮🇹 Italiano</a> | <a href="README_deu.md">🇩🇪 Deutsch</a> | <a href="README_zho.md">🇨🇳 简体中文</a> | <a href="README_jpn.md">🇯🇵 日本語</a> | <a href="README_por.md">🇵🇹 Português</a></p>

### Your HYDRA-UMC robots in your pocket: find the server, move and watch the robots, and stop everything with one red button.

<p align="center">
  <img src="https://img.shields.io/badge/License-GPL%203.0--or--later-blue.svg" alt="GPL 3.0 or later">
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF.svg" alt="Language">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg" alt="UI">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84.svg" alt="Platform">
  <img src="https://img.shields.io/badge/Tests-303%20passing-2ea44f.svg" alt="Tests">
  <img src="https://img.shields.io/badge/Version-0.7.2-0f6cbd.svg" alt="Version">
  <img src="https://img.shields.io/badge/Maturity-functional-0F6CBD.svg" alt="Maturity">
</p>

---

A native Android app (Kotlin + Jetpack Compose) that controls a robot on the [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) platform over Wi-Fi or Bluetooth, speaking the exact same [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-SERVER/blob/main/docs/REMOTE_API.md) contract [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) uses - discovery, full-state read/write, and live WebSocket sync against a running [HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER) backend (the same one [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)'s own web dashboard talks to). Direct Android counterpart to [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL). See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design.

> **Honesty check - what actually runs today:** the core business logic - Parol6 kinematics (`kinematics/Parol6Kinematics.kt`), the settings-tree state contract (`model/HydraState.kt`), atomic command dispatch and the voice-turn relay (`viewmodel/RobotViewModel.kt`), WebSocket reconnect handling (`network/HydraWebSocket.kt`), MJPEG frame parsing (`ui/MjpegPlayer.kt`), the in-app update channel (`update/GitHubReleaseUpdater.kt`, `ReleaseMetadataParser.kt`, `SemanticVersion.kt`), and the Wear voice-relay request bookkeeping (`wear/BoundedRequestScope.kt`, `WatchCompanionProtocol.kt`) are real and covered by 303 passing JVM/Robolectric unit tests (`./gradlew testDebugUnitTest`). What those tests structurally can't reach - and what this Windows-only dev environment has no physical Android device to verify - is `network/AuthPrefs.kt`'s real Keystore-backed `EncryptedSharedPreferences`, the `androidx.biometric` fingerprint/face prompt, `network/HydraBleClient.kt`'s Bluetooth GATT transport, and the API 28+ hardware `ImageDecoder` path in `MjpegStreamParser`: real, compiled code that hasn't been re-verified on hardware since it was written. Basic launch/login *was* confirmed live on a real device for earlier releases (see `CHANGELOG.md`'s `v0.3.2`/`v0.3.3` entries), but that predates Bluetooth, biometrics, and the hardware-decode path. See `CHANGELOG.md` for exactly what has shipped, including its own per-entry notes on what remains hardware-unverified.

> **Safety and security status:** this ecosystem is not a certified safety system. There is no hardware E-STOP path, no firmware-enforced limits or interlocks and no verified motion command yet; firmware images are checked with CRC-32 and an Ed25519 signature, and a release build refuses the published development keys (the tool heads and expansion boards behind the relay still run URTC bootloaders, which take a shared-key HMAC-SHA256 instead of a signature); TLS and mutual TLS are optional and off by default; no custom board has been populated or tested yet. Read the [security baseline](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/SECURITY_BASELINE.md) and the [threat model](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/THREAT_MODEL.md) before relying on any statement here for the safety of a person.

---

## 📋 At a glance

| | |
|---|---|
| Version | `0.7.2` |
| Maturity | Functional |
| Tests | 303 (`./gradlew testDebugUnitTest`) |
| Stack | Kotlin / Jetpack / biometric auth / WebSocket client |
| Target | An Android phone or tablet |
| On real hardware | Not yet: no run on real hardware is recorded for this version |

**Jump to:** [Overview](#-overview) · [Architecture](#-architecture) · [Features](#-features) · [Security & Safety](#-security--safety) · [Interfaces](#-interfaces) · [Configuration](#-configuration) · [Quick start](#-quick-start) · [Testing](#-testing) · [Troubleshooting](#-troubleshooting) · [Repository Structure](#-repository-structure) · [Related Projects](#-related-projects)

## 🚦 Project status

Each line is one part of this repository and the light says how far it is today. It is written from what the repository can show (tests, builds, runs), not from what is planned.

🟢 Done and verified · 🟡 Works, with known limits · 🟠 Partly done or not verified yet · 🔴 Not done or not working

| Area | State | What it means here |
|---|---|---|
| Software | 🟡 | Works for its main purpose; the honesty check above lists what is still missing |
| Tests | 🟢 | 303 automated tests, run on every change |
| Real hardware | 🔴 | Not yet: no run on real hardware is recorded for this version |
| Documentation | 🟢 | README in eight languages, checked against the manifest on every change |

## 🎯 Overview

Screens of the app: **login** (server address, password, fingerprint or face), **dashboard** (a 3D carousel of the robots with their module matrix and the health of the compute module), **control** (joysticks, play, pause, stop, reservation of the robot), **cameras** (live MJPEG with picture-in-picture), **3D view** (the real STUDIO scene), **telemetry** (a terminal-style log) and **settings** (connection, security, notifications, updates). A red **Stop all** button sits in the top bar of every screen, and also on a home-screen widget.

## 🧩 Role in HYDRA-UMC

**HYDRA-UMC-ANDROID-CONTROL** is the native Android operator console of HYDRA-UMC: it finds a HYDRA-UMC-SERVER on the local network, signs in, and then shows the robots, moves them, watches their cameras and a 3D view, and stops everything with one tap. It speaks the same remote contract as the SUITE and the iOS app and keeps a copy of the last state for when the Wi-Fi is gone. It is not a safety device.

## 🏛️ Architecture

<p align="center">
  <img src="images/architecture.svg" alt="HYDRA-UMC-ANDROID-CONTROL architecture" width="100%">
</p>

## ✨ Features

- **Access Control & Biometrics** (`ui/LoginScreen.kt`, `util/BiometricHelper.kt`) - Professional login system with **Fingerprint and Face Unlock** support (`androidx.biometric`), plus IP/port fields right on the same screen so a server can be pointed at without a separate trip through Settings first. Includes "Remember me" functionality, a secure **Logout** mechanism, and fully localized in **8 languages**. The cached username/password/token (`network/AuthPrefs.kt`) live in Keystore-backed **EncryptedSharedPreferences** (AES256-GCM), not plaintext - a development server (`NODE_ENV` unset) seeds a default `admin`/`admin` account on first start; a production server (`NODE_ENV=production`) requires `HYDRA_UMC_BOOTSTRAP_ADMIN_USERNAME`/`HYDRA_UMC_BOOTSTRAP_ADMIN_PASSWORD` instead and never falls back to `admin`/`admin` silently - see HYDRA-UMC-SERVER's own README for the full production setup. Additional lower-privilege **operator** accounts are creatable server-side from Config > Users either way.
- **Offline Mode & State Cache** (`network/StateCache.kt`) - Integrated persistence engine using **DataStore**. The app automatically caches the last known system state, allowing for instant dashboard viewing and configuration audits even without an active Wi-Fi connection.
- **Mission Notifications & Alerts** (`util/NotificationHelper.kt`) - Alerting system. Sends high-priority push notifications when a robot completes a job sequence or if critical hardware events occur, ensuring the operator is informed even when the app is in the background.
- **Industrial Telemetry Terminal** (`ui/TelemetryScreen.kt`) - A dedicated real-time log viewer with a terminal-style interface. Tracks system events, REST/WebSocket synchronization, and provides color-coded diagnostics (Matrix Green for success, Industrial Red for errors).
- **Advanced Dashboard** (`ui/DashboardScreen.kt`) - High-fidelity **3D Horizontal Carousel** with perspective swipe effects. Displays enriched robot metadata: **Manufacturer** (Source Robotics, Annin, Universal Robots, AgileX, etc.), **Robot Role** (CNC, Laser, PnP), and an **Industrial Module Matrix** with live status for CAM, XY, ATC, PNP, CNC, LSR, BED, VAC, and RCK modules.
- **System Health Monitor** (`ui/DashboardScreen.kt`) - Real-time metrics for the connected Compute Module 5, including **Hostname**, **Formatted Uptime** (e.g., "2d 4h 15m"), and active counts for controllers and robots.
- **Enhanced Manual Control** (`ui/ControlScreen.kt`) - Features a professional vertical layout with **50% larger Joystick buttons** for maximum precision.
- **Playback Panel** (`ui/ControlScreen.kt`, `ui/PlaybackConsole.kt`) - Fixed bottom control bar housing the red **STOP** button (the global STOP below), **Start**, **Pause**, and **Stop** (selected robot only) buttons. These controls are always visible and feature **Haptic Feedback** for physical sensory confirmation.
- **Global STOP** (`ui/GlobalStop.kt`, `network/HydraApiClient.kt`, `viewmodel/RobotViewModel.kt`) - A red **STOP** button (caption "Stop all", translated in the 8 languages) in the top bar of every screen, in the Control screen's console and on the 3D view (portrait and fullscreen). One tap, no hold and no confirmation: it sends `POST /api/stop-all` at once and stops every robot, even those reserved by another account. It shows the result - which robots stopped, which did not, which are reserved by someone else, and a warning when the server could not save the stopped state - retries once when nobody answers, stops the robots one by one if the server rate limits the call (429), and keeps a persistent **STOP NOT CONFIRMED** banner until a later STOP succeeds. This is the server's software stop: it is not a hardware E-STOP and does not cut power to anything.
- **Reservations and safety limits** (`ui/AccessPanel.kt`, `network/AccessModels.kt`, `network/HydraApiClient.kt`, `viewmodel/RobotViewModel.kt`) - The Control screen shows who holds the selected robot (nobody, this phone, the same account on another device, or another account) and offers **Reserve** and **Release**, plus the list of every reservation of the server (`GET /api/claims`). The phone keeps one controller id for the installation and sends it with every reserve, release, jog-arm and command (body and `X-Controller-Id` header), so a reservation belongs to the account and to this phone, not to the whole account. The refusals of the server are told apart: reserved by another account, by your account on another device or window, controller id required, and jog not armed. The limits of the robot's model are read from `GET /api/safety-limits`: a jog step above the limit is not sent, and the server's warnings are shown. When the administrator requires it, an **Arm jog** button (with a countdown) must be pressed before the joystick moves the robot; the arming ends by itself, with STOP, or on release. Reservations are optional: a robot nobody reserved is commanded as before. For an **administrator** only, a **Take control** button appears on a robot someone else holds; it asks for confirmation and then reserves the robot with `force: true` (the server accepts that from administrators only and checks the role again). Writes are conditional: each one carries the revision of the robot this app last saw (`If-Match`, `network/ContractV2.kt`), and when someone else changed the robot in between the server answers 412, nothing is applied, the robot is read again and the message says so (Standard checks reservations, the table and one-off commands; Strict checks everything but STOP; Off checks nothing; the choice is in the list of reservations; STOP is never checked). The XY table jog is one atomic `table` command (an older server gets the jogs it understands), the server pushes the reservations over the WebSocket so the Control screen stops polling while they arrive, and administrators read the audit record (`ui/AuditDialog.kt`) with its SHA-256 chain verified twice, by the server and on the phone, and can share its lines as `.jsonl`. PLAY asks first (the robot, its recorded points, the speed and who has it reserved) unless the person turns that off under the reservations.
- **3D View** (`ui/ThreeDScreen.kt`) - Embeds HYDRA-UMC STUDIO's own real-time 3D viewport in a WebView (`?hideUI=true&robotId=#token=`), rather than a native renderer. The WebView approach gets the real, currently-shipping STUDIO 3D scene (every real robot mesh/kinematics) for free instead of reimplementing it natively - the tradeoff is WebView rendering overhead, not battery-optimal, but functionally complete today.
- **Real-time Octal Vision** (`ui/CameraScreen.kt`, `ui/MjpegPlayer.kt`) - **Native MJPEG Streamer**. Features a background parser and Canvas-based renderer for low-latency video, a clear "Camera Disabled" state (instead of a silently blank feed) when a robot's vision system is off, and a switch to turn a robot's camera on/off directly from the server. Supports automatic **Picture-in-Picture (PIP)** overlays in the manual control screen, mapped to specific robots via the server's camera configuration. On API 28+, each frame decodes through the platform's own hardware-accelerated `ImageDecoder` path (a GPU-resident bitmap, skipping the CPU-side decode/upload) with an automatic, self-healing fallback to the original CPU decode.
- **Smart Discovery & Connectivity** (`network/Discovery.kt`, `network/HydraApiClient.kt`, `network/HydraWebSocket.kt`) - Concurrent subnet scan against every candidate host on the phone's own /24 (including the phone's own LAN IP and localhost, not just the other hosts), probing `GET /api/hydra-info` and identifying a real server purely by the presence of `remoteApiVersion` - the same check the manual-IP path uses, so a server whose owner renamed it away from the default product string is still found. An **NsdManager** (mDNS/Bonjour) listener runs alongside it - the server does announce itself as `_hydra._tcp` (`bonjour-service`), and `MainActivity` requests the runtime location/nearby-devices permission this needs up front (a manifest declaration alone never grants it on API 23+) - but the subnet scan stays the primary path since multicast reachability on Wi-Fi is inherently less reliable than a plain HTTP probe. The app automatically activates WiFi on startup, scans the local factory network, and performs a **Zero-Click Auto-connect** to the first available HYDRA-UMC server.
- **JWT Access** (`network/HydraApiClient.kt`, `ui/LoginScreen.kt`) - Login with **JWT (JSON Web Tokens)**. Every control command (Jog, Play and the server's global stop) is validated by the server using signed tokens, sent over the atomic `POST /api/robot/:id/command` endpoint (see Atomic Command Sync below) - works for either the `admin` or `operator` role, unlike a full `POST /api/settings` write (admin-only server-side). Every request also carries an `X-Hydra-Client: android` header so the server's own Config > Remote Access tab can allow/block this app independently of SUITE/iOS. Seamlessly integrated with **Biometric Authentication** (Fingerprint/Face) for secure token renewal. A WebSocket closed with code `1008` (invalid/expired token) is treated as "sign in again," not retried in a reconnect loop (`network/HydraWebSocket.kt`).
- **Atomic Command Sync** (`viewmodel/RobotViewModel.kt`'s own `sendAtomicCommand()`) - Every write (play/pause/stop/jog/jogStep/valve/pump/speed/vision/tool) sends a small, single-robot atomic command instead of the entire settings tree - the server computes which combined robots are also affected, persists to disk, and broadcasts to every other connected client on its own. Enable/Disable propagates to a robot's own `combinedWith` siblings the same way Play/Pause/Stop does, since all of them share the same affected-robots computation.
- **Global STOP Widget** (`widget/GlobalStopWidget.kt`) - Dedicated **Home Screen Widget** for quick access. Provides a high-visibility, instant-access **Global STOP** button - the same call as above: the tap brings the app to the front and sends the stop as soon as the server connection exists, even from a fully cold start (process not already running), and shows STOP NOT CONFIRMED if it cannot reach the server. The notification action does the same. This is the server's software stop: it is not a hardware E-STOP and does not cut power to anything.
- **Haptic Feedback** (`ui/ControlScreen.kt`) - Advanced sensory feedback system. The global STOP answers a tap with an immediate double buzz; the per-robot Stop button keeps its **Long-Press Protection** (a quick tap does nothing but a short buzz + hint; only a genuine hold sends the command). Differentiated haptic signatures (Success, Error, and Emergency pulses) provide physical confirmation to the operator in noisy environments.
- **In-App Update Channel** (`update/GitHubReleaseUpdater.kt`, `update/ReleaseMetadataParser.kt`, `update/SemanticVersion.kt`) - Checks the latest stable GitHub Release on launch and from **Settings → Updates**; downloads only the exact `HYDRA-UMC-ANDROID-CONTROL-release.apk` asset off a non-draft, non-prerelease tag, never auto-installs - Android's own package installer asks for final consent. Full release contract: [`docs/GITHUB_RELEASE_UPDATES.md`](docs/GITHUB_RELEASE_UPDATES.md).
- **Bluetooth LE (GATT)** (`network/HydraBleClient.kt`, `network/BleContract.kt`, `network/AndroidBleGatt.kt`) - A real GATT client following a documented contract ([`docs/BLE_CONTRACT.md`](docs/BLE_CONTRACT.md)): scan filtered by the HYDRA service, connection with MTU negotiation, service check, notifications, framed messages in both directions, one write at a time, automatic reconnection with growing delays, Android 12+ permissions (`BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`) and a clear message in the user's language for every failure. **No HYDRA-UMC device implements the peripheral side yet:** the client is tested only against a simulated GATT, the Android part has never run on a phone, and robot commands are not sent over Bluetooth.
- **Optional TLS** (`network/TlsSettings.kt`, `network/HydraHttp.kt`, `ui/SecuritySettings.kt`) - **Settings → Security** chooses Automatic (the default, the behaviour the app always had), Require TLS or Plain only, and can pin the server certificate fingerprint (SHA-256) or trust a custom CA. A session that is not encrypted to a host outside the local network shows a red warning above every screen. The embedded 3D view does not follow these rules yet. Tested with real handshakes against a local server; never tried against a real HYDRA-UMC server with TLS.
- **No plain traffic outside the local network** (`network/TlsSettings.kt`, `PlainRemoteGuard`) - the app refuses unencrypted HTTP/WS to a server that is not on the local network unless **Settings → Security → Allow plain traffic outside the local network** is on (off by default). The embedded 3D view receives the session token in the address fragment (`#token=`), never in the query, so no server or proxy log records it. Automatic backups of the app data are off.
- **Paired Wear OS Companion & Voice Relay** (`wear/WatchVoiceRelayService.kt`, `wear/WatchCompanionProtocol.kt`) - Relays an already-recognized, user-initiated voice turn from the paired [HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH) app through this app's own authenticated Server session to `HYDRA-UMC-VOICE-UI`, returning a typed reply to the Watch; neither this relay nor the watch system-status card ever issues a robot command or touches `HydraState` directly - a motion-related reply is explicitly marked `requiresConfirmation` and must go through a primary control UI. Full contract: [`docs/WATCH_VOICE_RELAY.md`](docs/WATCH_VOICE_RELAY.md).
- **In-App Voice Assistant** (`ui/VoiceAssistantDialog.kt`) - A mic icon in the top bar opens a dialog to type a question/order, or use this phone's own on-device speech recognition (`RecognizerIntent`, same real pattern as the Watch's own voice button). Sends the recognized transcript through the same `POST /api/voice/turn` route the Watch relay uses, but as this phone's own ordinary traffic (`HydraApiClient.postVoiceTurn()`) rather than a relay - so Config > Remote Access can allow/block this button independently of the paired Watch. Never uploads audio, and a reply never invokes a robot command directly.
- **Toolchain & Project Quality** - AGP 9.3.1, Kotlin 2.2.10, Gradle 9.7.0, compileSdk 36, **JDK 21** (`compileOptions`, `gradle-daemon-jvm.properties`, and both `.idea/`/`.vscode/` project files all actually target it, not just this line of documentation). Clean build output with zero warnings, optimized R8 production variants, and advanced **Roborazzi** screenshot testing.

**Status: Wi-Fi, Bluetooth, Biometrics, and Notifications implemented.** The app is an industrial operator console; Bluetooth is still experimental, and no screen replaces a physical emergency stop.

## 🔒 Security & Safety

| Threat | Defence | Where |
|---|---|---|
| A token stolen from the phone | The sign-in token and the refresh token are kept with EncryptedSharedPreferences (AES-256-GCM per value, master key in the Android Keystore); automatic backup of the app data is off (`allowBackup=false`) | `network/AuthPrefs.kt`, `AndroidManifest.xml` |
| Plain traffic to a server outside the local network | `PlainRemoteGuard` refuses unencrypted HTTP and WebSocket to such a server unless Settings > Security > Allow plain traffic outside the local network is on (off by default); a session that is not encrypted shows a red warning above every screen | `network/TlsSettings.kt`, `ui/SecuritySettings.kt` |
| A fake server | Optional TLS with three modes (Automatic, Require TLS, Plain only); with a pinned SHA-256 fingerprint only a certificate with exactly that fingerprint is accepted, and a custom CA can be trusted instead | `network/TlsSettings.kt`, `network/HydraHttp.kt` |
| A fake update | Only the asset named `HYDRA-UMC-ANDROID-CONTROL-release.apk` of the latest stable, non-draft release is accepted; the app never installs by itself, Android's own installer asks for consent | `update/ReleaseMetadataParser.kt` |
| A robot moved by two people, or a stop that does not arrive | The robot is reserved before it is jogged and the screen shows who holds it; the global STOP is posted with short timeouts, retried once, falls back to one stop per robot and keeps a persistent "STOP NOT CONFIRMED" state if it could not be delivered | `ui/GlobalStop.kt`, `network/AccessModels.kt`, `network/StopAllResult.kt` |

**What it never does:** replace a physical emergency stop. The STOP is the server's software stop and does not cut power to anything.

## 🔌 Interfaces

| Topic or route | What it carries |
|---|---|
| `POST /api/login`, `/api/logout`, `/api/refresh` | sign in, sign out and renew the session |
| `GET /api/hydra-info` | tells a real server by `remoteApiVersion`; used by the subnet scan and the manual address |
| `GET /api/settings`, `/api/ecosystem/status`, `/api/system/metrics`, `/api/safety-limits`, `/api/claims` | the state of the robots, the family, the compute module, the safety limits and the reservations |
| `POST /api/robot/{id}/command`, `/claim`, `/release`, `/jog-arm` | one small command for one robot, its reservation and the arming of a jog |
| `POST /api/stop-all` | the global STOP: every robot, even those reserved by someone else |
| `GET /api/audit`, `/api/camera/ticket`, `POST /api/voice/turn`, `GET /api/watch/system-status` | the audit record (administrators), a ticket for the camera stream, a voice turn and the status card of the watch |
| WebSocket `hydra.bearer` | live state changes pushed by the server; the token goes in the sub-protocol and the app reconnects every 3 seconds |
| Bluetooth LE GATT | the contract of `docs/BLE_CONTRACT.md`: framed messages, one write at a time, Android 12 permissions; no HYDRA-UMC device implements the other side yet |

## ⚙️ Configuration

The app has no configuration file: everything is set on screen and stored on the phone. Defaults and limits come from the code.

| Variable | Default | What it sets |
|---|---|---|
| Settings > Security > TLS mode | Automatic | Automatic, Require TLS or Plain only; optional pinned SHA-256 fingerprint or custom CA |
| Settings > Security > Allow plain traffic outside the local network | off | lets the app talk plain HTTP and WebSocket to a server that is not on the local network |
| Server address and port | `3000` | typed on the login screen, or found by the scan of the phone's own /24 subnet (1.5 s per host, 16 at a time) plus mDNS |
| Session renewal | 20 s / 3 / 10 min | the app renews the session by itself at most 3 times in 10 minutes and at least 20 s apart; past that it asks for the sign-in again |
| Settings > Notifications | on | alerts when a robot finishes a job or a critical event happens (two channels: missions and safety) |

## 🚀 Quick start

Requires JDK 21 and the Android SDK (Android Studio brings both), and a phone or emulator with Android 7.0 (API 24) or newer.

```bash
./build-android.sh           # builds the debug APK and installs it by adb (build-android.bat on Windows)
./gradlew assembleDebug      # only builds; the APK lands in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest  # runs the unit tests (gradlew.bat on Windows)
```

## 🧪 Testing

303 automated tests run with `./gradlew testDebugUnitTest`. They need no hardware and nothing outside this machine.

The CI runs `HYDRA_UMC_CI=1 python tools/ci_validate.py`, which checks the manifest, the documents, the eight READMEs and the pictures.

### Testing against a live server

1. Run the backend: `cd HYDRA-UMC-SERVER && npm run dev` (Port 3000) - this is the actual REST/WS API this app talks to (see Related Projects below); `HYDRA-UMC-STUDIO`'s own `npm run dev` only starts its Vite frontend dev server (port 5173) against that same backend, it isn't the API server itself.
2. Connect your Android device to the same Wi-Fi.
3. Use the **Global Server Selector** or enter the IP manually in the header.
4. **Biometrics:** Enable "Biometric Login" in your User Profile to skip the password screen on the next launch.

## 🏗️ Build & Release

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

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Install it with `adb install -r -d app/build/outputs/apk/debug/app-debug.apk`, or transfer it to the device manually. Swap `assembleDebug` for `assembleRelease` for a release build - it signs with the private release key when `keystore.properties` (or the release signing environment variables) is present, and otherwise falls back to the debug key, which installs fine for testing but must never be distributed.

### Versioning

This repo follows an ecosystem-wide policy: building never changes the version. `app/version.properties` holds the current `versionMajor`/`versionMinor`/`versionPatch`/`versionCode`; only the explicit bump command raises them (`python tools/hydra_project_tool.py bump`: `bump_manifest_version.py` for the native version, the `hydra-umc.project.json` manifest and `CHANGELOG.md`, and `bump_version_code.py` for the separate, always-monotonic Android `versionCode`). `app/build.gradle.kts` only ever *reads* `version.properties` and never writes it.

- **`versionCode`:** a plain monotonic counter, +1 on every bump, no carry - Android requires it to strictly increase across every build that ever ships.

A plain `./gradlew assembleDebug` (the "Manual Build" path above, with neither bump script run first) reuses whatever `version.properties` last had - it does not bump anything on its own. The running version is visible live in the **About** dialog (`BuildConfig.VERSION_NAME`, reading whichever `versionName` `version.properties` last held). See [CHANGELOG.md](CHANGELOG.md) for the version history.

## 🩺 Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| No Notifications | Permission denied | Grant "Notifications" permission in Android settings for this app |
| No Biometrics | Hardware not set | Ensure you have a Fingerprint/Face registered in your Android System Security |
| Robot won't move | Session expired, robot reserved by another account, or a trajectory is running | Read the message in the status bar; the server plays trajectories itself and no browser tab is needed |
| Bluetooth disabled | Physical chip off | Use the "ENABLE SYSTEM BT" 3D button in the app |

## 📂 Repository Structure

<p align="center">
  <img src="images/repository-structure.svg" alt="Repository structure of HYDRA-UMC-ANDROID-CONTROL" width="100%">
</p>

<details>
<summary>Text version of the tree</summary>

```text
HYDRA-UMC-ANDROID-CONTROL/
├── app/src/main/java/com/hydraumc/control/
│   ├── MainActivity.kt                                          Entry point - splash, login/main screen gating, cold-start-safe global E-STOP handling
│   ├── MainScreen.kt                                            Bottom-nav scaffold, top bar (server selector, profile, telemetry, settings)
│   ├── kinematics/                                              forward and inverse kinematics of the PAROL6 arm
│   ├── model/                                                   the state of the server as the app reads it
│   ├── network/                                                 REST client, WebSocket and discovery
│   ├── ui/                                                      screens and widgets
│   ├── update/                                                  the in-app update channel
│   ├── util/                                                    pure rules of the app
│   ├── viewmodel/                                               the shared state every screen listens to
│   ├── wear/                                                    voice and status relay
│   └── widget/                                                  the home-screen STOP widget
├── docs/                                                        documents of the project
├── images/                                                      README banner + splash screen source assets
├── tools/                                                       project tools and CI checks
├── .github/                                                     CI workflow and issue templates
├── gradle/                                                      Gradle wrapper files
├── bump_manifest_version.py                                     Syncs hydra-umc.project.json's version to the native one (--sync)
├── bump_version_code.py                                         Android versionCode counter bump (run only by the explicit `bump` command)
├── gradlew / gradlew.bat                                        Gradle wrapper
├── build.gradle.kts / settings.gradle.kts / gradle.properties   Root Gradle project config
├── keystore.properties.example                                  Private release signing configuration template
├── .env.example                                                 Example environment variables
├── LICENSE                                                      GPL-3.0-or-later
├── CHANGELOG.md                                                 what changed in each version
├── build-test.sh                                                non-mutating build check
├── hydra-umc.project.json                                       manifest: version, maturity, family
├── test.sh                                                      non-mutating build check
├── prepare-github-release.sh / prepare-github-release.bat       builds the privately signed release APK; changes no version
├── publish-github-release.sh / publish-github-release.ps1       publishes that APK as the GitHub release of the current version
├── app/version.properties                                       version and versionCode
└── app/src/test/                                                JVM unit tests
```

</details>

## 🗺️ Roadmap & Known limits

Bluetooth LE stays experimental until a device implements the peripheral side of `docs/BLE_CONTRACT.md`. TLS and the pinned fingerprint were tested with real handshakes against a local test server, not against a HYDRA-UMC server with TLS. The embedded 3D view does not follow the TLS settings yet. The Keystore storage, the biometric prompt and the Bluetooth transport have not been checked again on hardware since they were written.

## 🌍 Languages

This README and the documents of the repository exist in eight languages: English, Spanish, German, French, Italian, Japanese, Chinese and Portuguese (Portugal). They are written together and checked in the CI so that the eight files have the same sections and the same links.

## 🔗 Related Projects

This project is part of the HYDRA-UMC robotics ecosystem by the same author (JuanenRac / Electro Hobby 3D). Worth knowing about, since a request might actually be about one of these rather than this repository.

**Parent Project**
- **[HYDRA-UMC-SERVER](https://github.com/JuanenRac/HYDRA-UMC-SERVER)** — the real headless backend (REST/WebSocket) every control client actually talks to; the backend this app's own discovery, auth, and WebSocket sync all run against.

**Sibling Projects** — also talk to HYDRA-UMC-SERVER's own API, each their own client
- **[HYDRA-UMC-STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)** — web control dashboard with real-time multi-robot 3D visualization; its own 3D viewport is embedded directly in this app's 3D View screen via WebView.
- **[HYDRA-UMC-SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE)** — desktop (PySide6) swarm command center for multiple servers at once, packaged as a standalone executable; speaks the exact same `REMOTE_API.md` contract as this app.
- **[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL)** — iOS/iPadOS control app (Flutter) with real-time WebSocket sync; this app's direct iOS/iPadOS counterpart, same feature set.
- **[HYDRA-UMC-DSI](https://github.com/JuanenRac/HYDRA-UMC-DSI)** — native touch UI for the onboard 7" DSI touchscreen, embedded on the CM5 itself.
- **[HYDRA-UMC-BRIDGE-AMR](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-AMR)** — coordination boundary for AGV/AMR fleets via a VDA 5050 MQTT publisher (tested against an emulated fleet only).
- **[HYDRA-UMC-BRIDGE-CNC](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-CNC)** — high-level CNC-cell coordinator with GRBL status/control-byte access (tested against an emulator only).
- **[HYDRA-UMC-BRIDGE-DROIDS](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-DROIDS)** — coordination boundary for legged/humanoid droids, with a Boston Dynamics Spot command sender (tested against an emulator only).
- **[HYDRA-UMC-BRIDGE-LASER](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-LASER)** — laser-cell coordinator reading 3 key/enclosure/interlock GPIO signals (tested against a fake chip only; it cannot arm or fire a laser).
- **[HYDRA-UMC-BRIDGE-OPENPNP](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-OPENPNP)** — safe high-level board-flow coordinator for OpenPnP pick-and-place.
- **[HYDRA-UMC-BRIDGE-PRINTER3D](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-PRINTER3D)** — safe coordination boundary for Moonraker/Klipper 3D printers, with real gated job commands.
- **[HYDRA-UMC-BRIDGE-ROS2](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-ROS2)** — coordinator with a lazily imported rclpy ROS 2 transport (not yet run against a ROS 2 installation).
- **[HYDRA-UMC-BRIDGE-UAV](https://github.com/JuanenRac/HYDRA-UMC-BRIDGE-UAV)** — coordination boundary for camera-equipped UAVs, with a MAVLink command sender (tested against an emulator only).

**Directly Related**
- **[HYDRA-UMC-WATCH](https://github.com/JuanenRac/HYDRA-UMC-WATCH)** — WearOS companion app with haptic alert patterns and a paired-phone voice relay (not yet run on a physical watch); the WearOS companion to this app, for at-a-glance robot status and control from the wrist.
- **[HYDRA-UMC-HIL-BRIDGE](https://github.com/JuanenRac/HYDRA-UMC-HIL-BRIDGE)** — hardware-in-the-loop router with a software collision interlock between simulation and real hardware (tested without real hardware); enables remote control of the digital twin directly from this app.

**Also Part of the Ecosystem**

*Core Hardware & Platform*
- **[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)** — the robot-arm motherboard design: CM5 host + dual-core STM32H745, meant to orchestrate up to 8 tool arms over CAN-OTA/SPI-OTA (firmware at an early stage; no populated board yet).
- **[HYDRA-UMC-OS](https://github.com/JuanenRac/HYDRA-UMC-OS)** — reproducible Raspberry Pi OS product layer for the CM5: read-only agent, validated config/profiles, WiFi first-contact provisioning.
- **[HYDRA-UMC-SDK](https://github.com/JuanenRac/HYDRA-UMC-SDK)** — the shared JSON-Schema contract and software job-gate boundary every bridge validates its commands against (not a certified safety function).
- **[HYDRA-UMC-CONNECTOR-HUB](https://github.com/JuanenRac/HYDRA-UMC-CONNECTOR-HUB)** — declarative adapter-manifest registry and validator for external-machine connectors; extends the SDK's own contract idea to external machines without replacing the industrial-gateway projects.

*Core Backend & Clients*
- **[HYDRA-UMC-EDITOR-URDF](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-URDF)** — desktop graphical URDF creator/editor that pushes finished models into STUDIO's own catalog.
- **[HYDRA-UMC-EDITOR-STL](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-STL)** — desktop STL model editor, transforming/replacing/removing/adding real parts in the same model catalog HYDRA-UMC-EDITOR-URDF also edits.

*URTC Tool Platform*
- **[URTC](https://github.com/JuanenRac/URTC)** — firmware for the physical Universal Robot Tool Controller PCB, 25+ tool profiles over CAN bus.
- **[URTC-FLASHER](https://github.com/JuanenRac/URTC-FLASHER)** — desktop GUI flashing tool for URTC boards, CAN-OTA plus full-chip SWD/JTAG.
- **[URTC-TESTER](https://github.com/JuanenRac/URTC-TESTER)** — desktop live CAN-bus diagnostic tool for URTC boards, one panel per tool profile.
- **[URTC-WEB-STUDIO](https://github.com/JuanenRac/URTC-WEB-STUDIO)** — browser-based alternative to URTC-TESTER via the Web Serial API, no local install needed.
- **[URTC-GATEWAY](https://github.com/JuanenRac/URTC-GATEWAY)** — gateway in front of the URTC CAN bus: an allowed list of identifiers, a rate limit and a session key; software tested on a computer, not yet on a real bus.

*Vision AI Node (Hailo-8)*
- **[HYDRA-UMC-VISION-NODE](https://github.com/JuanenRac/HYDRA-UMC-VISION-NODE)** — integration hub for the Hailo-8 vision pipeline, with a real per-stage hardware-readiness check.
- **[HYDRA-UMC-DETECTION-HEF](https://github.com/JuanenRac/HYDRA-UMC-DETECTION-HEF)** — real compiled-model registry with Hailo-architecture/checksum safe-load verification.
- **[HYDRA-UMC-VISION-STREAMER](https://github.com/JuanenRac/HYDRA-UMC-VISION-STREAMER)** — real GStreamer pipeline + MediaMTX config generator with a real HailoRT integration boundary.
- **[HYDRA-UMC-VISUAL-SERVOING-API](https://github.com/JuanenRac/HYDRA-UMC-VISUAL-SERVOING-API)** — Position-Based Visual Servoing correction law with authorisation gated on the zone state; no camera pose estimation yet.
- **[HYDRA-UMC-SAFETY-ZONES](https://github.com/JuanenRac/HYDRA-UMC-SAFETY-ZONES)** — v0 zone-breach, calibration-freshness and E-STOP-request logic that takes object positions from a file; no detector feeds it and no physical E-STOP is wired to it yet.

*Cognitive AI Node (Hailo-10)*
- **[HYDRA-UMC-COGNITIVE-NODE](https://github.com/JuanenRac/HYDRA-UMC-COGNITIVE-NODE)** — integration hub for the Hailo-10 cognitive pipeline (LLM/VLA/voice orchestration).
- **[HYDRA-UMC-VLA-ENGINE](https://github.com/JuanenRac/HYDRA-UMC-VLA-ENGINE)** — real action-token encoding/decoding and trajectory generation for a Vision-Language-Action model.
- **[HYDRA-UMC-VOICE-UI](https://github.com/JuanenRac/HYDRA-UMC-VOICE-UI)** — real voice front-end (VAD + intent parser) with a bounded, confirmation-gated Watch relay.
- **[HYDRA-UMC-SEMANTIC-PLANNER](https://github.com/JuanenRac/HYDRA-UMC-SEMANTIC-PLANNER)** — real rule-based task decomposition and semantic error recovery over MCU error codes.
- **[HYDRA-UMC-DOCS-QA](https://github.com/JuanenRac/HYDRA-UMC-DOCS-QA)** — real stdlib-only TF-IDF document search over this ecosystem's own Markdown docs.
- **[HYDRA-UMC-LOCAL-TECHNICIAN](https://github.com/JuanenRac/HYDRA-UMC-LOCAL-TECHNICIAN)** — local, policy-gated AI maintenance technician for the ecosystem itself - observes, diagnoses and proposes fixes; the two highest risk levels are deliberately not implemented yet.

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
- **[HYDRA-UMC-OPS-AGENT](https://github.com/JuanenRac/HYDRA-UMC-OPS-AGENT)** — maintenance-incident coordinator: a low-privilege edge role collects a sanitized inventory/health snapshot, a control-plane role renders it read-only and asks an AI provider to suggest a diagnosis - never applies a patch or deploys anything.
- **[HYDRA-UMC-DEV-SERVER](https://github.com/JuanenRac/HYDRA-UMC-DEV-SERVER)** — reproducible development host (Raspberry Pi 5 / CM5) that stores the ecosystem's source and runs bounded build/test tasks under a durable queue; a dedicated dev role, explicitly not an operational CM5.

## 📚 Documentation

Where to read more:

* [docs/SOURCE_MAP.md](docs/SOURCE_MAP.md) - every source file of the app with a line on what it does
* [Security baseline of the ecosystem](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/SECURITY_BASELINE.md)
* [Threat model of the ecosystem](https://github.com/JuanenRac/HYDRA-UMC-DOCS/blob/main/docs/THREAT_MODEL.md)
* [Changelog of this repository](CHANGELOG.md)
* [License of this repository](LICENSE.md)

## 🤝 Contributing & Community

* Before a pull request run `HYDRA_UMC_CI=1 py -3 tools/ci_validate.py` (or `HYDRA_UMC_CI=1 python3 tools/ci_validate.py`): it must print `CI_VALIDATION=PASS`. Do not run `build.sh` or `build.bat` for this check; building is not a check.
* The eight READMEs are written from one source in HYDRA-UMC-DOCS; do not edit them by hand. Change the source and write them again with `tools/make_readmes.py`.
* Do not state in a README what has not run: say "simulated" or "not tried on hardware" where that is the truth.
* See [CONTRIBUTING.md](CONTRIBUTING.md), the [code of conduct](CODE_OF_CONDUCT.md), [SECURITY.md](SECURITY.md) and [SUPPORT.md](SUPPORT.md).

## 👤 AUTHOR

**JuanenRac (Electro Hobby 3D)** · electrohobby3d@gmail.com

## 📜 LICENSE

GPL-3.0-or-later - [LICENSE](LICENSE).
