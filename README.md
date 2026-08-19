<p align="center">
  <img src="images/HYDRA_UMC_ANDROID_CONTROL_BANNER.jpg" alt="HYDRA-UMC Android Control Banner" width="100%">
</p>

# 📱 HYDRA-UMC CONTROL

A native Android app (Kotlin + Jetpack Compose) that controls a robot on the [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) platform over Wi-Fi or Bluetooth, speaking the exact same [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-STUDIO/blob/main/docs/REMOTE_API.md) contract [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) uses - discovery, full-state read/write, and live WebSocket sync against a running [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO) server. Direct Android counterpart to [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL). See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design.

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
- **Smart Discovery & Connectivity** (`network/Discovery.kt`, `network/HydraApiClient.kt`, `network/HydraWebSocket.kt`) - Concurrent subnet scan against every candidate host on the phone's own /24 (including the phone's own LAN IP and localhost, not just the other hosts), probing `GET /api/hydra-info` and identifying a real server purely by the presence of `remoteApiVersion` - the same check the manual-IP path uses, so a server whose owner renamed it away from the default product string is still found. An **NsdManager** (mDNS/Bonjour) listener runs alongside it for when the server side gains an announced `_hydra._tcp` service; the subnet scan is what actually finds a server today. The app automatically activates WiFi on startup, scans the local factory network, and performs a **Zero-Click Auto-connect** to the first available HYDRA-UMC server.
- **Secure Industrial Access** (`network/HydraApiClient.kt`, `ui/LoginScreen.kt`) - Professional security layer using **JWT (JSON Web Tokens)**. Every control command (Jog, Play, E-STOP) is validated by the server using signed tokens, sent over the real atomic `POST /api/robot/:id/command` endpoint (not a full-state overwrite, see below) rather than the old always-a-full-settings-tree write - works for either the `admin` or `operator` role, unlike a full `POST /api/settings` write (admin-only server-side). Every request also carries an `X-Hydra-Client: android` header so the server's own Config > Remote Access tab can allow/block this app independently of SUITE/iOS. Seamlessly integrated with **Biometric Authentication** (Fingerprint/Face) for secure token renewal. A WebSocket closed with code `1008` (invalid/expired token) is treated as "sign in again," not retried in a reconnect loop (`network/HydraWebSocket.kt`).
- **Atomic Command Sync** (`viewmodel/RobotViewModel.kt`'s own `sendAtomicCommand()`) - Every write (enable/disable/play/pause/stop/jog/valve/pump/speed/vision) now sends a small, single-robot atomic command instead of the entire settings tree - the server computes which combined robots are also affected, persists to disk, and broadcasts to every other connected client on its own. Enable/Disable now correctly propagates to a robot's own `combinedWith` siblings, matching Play/Pause/Stop (it didn't before).
- **Emergency Management Widget** (`widget/GlobalStopWidget.kt`) - Dedicated **Home Screen Widget** for critical safety. Provides a high-visibility, instant-access **Global E-STOP** button to freeze all robotic operations in the swarm without needing to open the app - reliably waits for the robot roster to actually load before acting, even from a fully cold start (process not already running).
- **Industrial Haptics & Safety** (`ui/ControlScreen.kt`) - Advanced sensory feedback system. Features real **Long-Press Protection** on the E-STOP and STOP buttons (a quick tap does nothing but a short buzz + hint; only a genuine hold sends the command) and differentiated haptic signatures (Success, Error, and Emergency pulses) to provide physical confirmation to the operator in noisy environments.
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

## 📲 Testing against HYDRA-UMC STUDIO

1. Run the server: `cd HYDRA-UMC-STUDIO && npm run dev` (Port 3000).
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

## 🔗 Related Projects

This project is part of a larger robotics ecosystem by the same author (JuanenRac / Electro Hobby 3D):

**HYDRA-UMC platform** — the multi-robot micro-factory cell
- **[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)** — Motherboard: Raspberry Pi CM5 host + STM32H745.
- **[HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)** — Web-based control dashboard and core server.
- **HYDRA-UMC-CONTROL** *(this repository)* — Android control app over Wi-Fi and Bluetooth.
- **[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL)** — iOS counterpart.
- **[HYDRA-UMC-SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE)** — Desktop swarm command center.
- **[HYDRA-UMC-EDITOR-URDF](https://github.com/JuanenRac/HYDRA-UMC-EDITOR-URDF)** — planned: graphical URDF creator/editor for STUDIO's model catalog. Not started yet.
- **[HYDRA-UMC-DSI](https://github.com/JuanenRac/HYDRA-UMC-DSI)** — planned: native touch UI for HYDRA-UMC's own 7" DSI touchscreen (1280×800) on the Compute Module 5. Not started yet.

**URTC platform** — robot tool head controllers
- **[URTC](https://github.com/JuanenRac/URTC)** — Universal Robot Tool Controller (STM32F303).
- **[URTC Flasher](https://github.com/JuanenRac/URTC-FLASHER)** / **[URTC Tester](https://github.com/JuanenRac/URTC-TESTER)** / **[URTC Web Studio](https://github.com/JuanenRac/URTC-WEB-STUDIO)**.

## 👤 Author

**JuanenRac** (Electro Hobby 3D)
📧 electrohobby3d@gmail.com
📺 youtube.com/@electrohobby3d

## 📜 License

GNU General Public License v3.0 (GPL-3.0) - see [`LICENSE`](LICENSE).
