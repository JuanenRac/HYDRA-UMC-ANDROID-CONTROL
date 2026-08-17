<p align="center">
  <img src="images/HYDRA_UMC_ANDROID_CONTROL_BANNER.jpg" alt="HYDRA-UMC Android Control Banner" width="100%">
</p>

# 📱 HYDRA-UMC CONTROL

A native Android app (Kotlin + Jetpack Compose) that controls a robot on the [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) platform over Wi-Fi or Bluetooth, speaking the exact same [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-STUDIO/blob/main/docs/REMOTE_API.md) contract [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) uses - discovery, full-state read/write, and live WebSocket sync against a running [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO) server. Direct Android counterpart to [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL). See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design.

## 🏗️ What's implemented

- **Access Control & Biometrics** (`ui/LoginScreen.kt`, `util/BiometricHelper.kt`) - Professional login system with **Fingerprint and Face Unlock** support (`androidx.biometric`). Includes "Remember me" functionality, persistent session storage, and a secure **Logout** mechanism. Fully localized in **5 languages**.
- **Offline Mode & State Cache** (`network/StateCache.kt`) - Integrated persistence engine using **DataStore**. The app automatically caches the last known system state, allowing for instant dashboard viewing and configuration audits even without an active Wi-Fi connection.
- **Mission Notifications & Alerts** (`util/NotificationHelper.kt`) - Industrial-grade alerting system. Sends high-priority push notifications when a robot completes a job sequence or if critical hardware events occur, ensuring the operator is informed even when the app is in the background.
- **Industrial Telemetry Terminal** (`ui/TelemetryScreen.kt`) - A dedicated real-time log viewer with a terminal-style interface. Tracks system events, REST/WebSocket synchronization, and provides color-coded diagnostics (Matrix Green for success, Industrial Red for errors).
- **Advanced Dashboard** (`ui/DashboardScreen.kt`) - High-fidelity **3D Horizontal Carousel** with perspective swipe effects. Displays enriched robot metadata: **Manufacturer** (Source Robotics, Annin, Universal Robots, AgileX, etc.), **Robot Role** (CNC, Laser, PnP), and an **Industrial Module Matrix** with live status for CAM, XY, ATC, PNP, CNC, LSR, BED, VAC, and RCK modules.
- **System Health Monitor** (`ui/DashboardScreen.kt`) - Real-time metrics for the connected Compute Module 5, including **Hostname**, **Formatted Uptime** (e.g., "2d 4h 15m"), and active counts for controllers and robots.
- **Enhanced Manual Control** (`ui/ControlScreen.kt`) - Features a professional vertical layout with **50% larger Joystick buttons** for maximum precision. Includes a **Job/Trajectory Selector** to browse and execute files directly from the server.
- **Safety & Playback Panel** (`ui/ControlScreen.kt`) - Fixed bottom control bar housing the **E-STOP (Emergency Stop)**, **Start**, **Pause**, and **Stop** buttons. These controls are always visible and feature **Haptic Feedback** for physical sensory confirmation.
- **Improved 3D View** (`ui/ThreeDScreen.kt`) - High-performance **Native 3D Viewport** powered by the **Google Filament** engine. Migrated from WebView to provide consistent 60 FPS, high-fidelity industrial rendering, and significantly better battery efficiency. Includes dynamic identity overlays for selected robots and automatic full-screen immersion in landscape mode.
- **Real-time Octal Vision** (`ui/CameraScreen.kt`, `ui/MjpegPlayer.kt`) - Industrial-grade **Native MJPEG Streamer**. Features an autonomous background parser and Canvas-based renderer for zero-latency video telemetry. Supports automatic **Picture-in-Picture (PIP)** overlays in the manual control screen, mapped to specific robots via the server's camera configuration.
- **Smart Discovery & Connectivity** (`network/Discovery.kt`, `network/HydraApiClient.kt`, `network/HydraWebSocket.kt`) - Dual-transport system featuring instant **mDNS (Bonjour)** discovery and recursive subnet scanning. The app automatically activates WiFi on startup, scans the local factory network, and performs a **Zero-Click Auto-connect** to the first available HYDRA-UMC server.
- **Secure Industrial Access** (`network/HydraApiClient.kt`, `ui/LoginScreen.kt`) - Professional security layer using **JWT (JSON Web Tokens)**. Every control command (Jog, Play, E-STOP) is validated by the server using signed tokens. Seamlessly integrated with **Biometric Authentication** (Fingerprint/Face) for secure token renewal.
- **Emergency Management Widget** (`widget/GlobalStopWidget.kt`) - Dedicated **Home Screen Widget** for critical safety. Provides a high-visibility, instant-access **Global E-STOP** button to freeze all robotic operations in the swarm without needing to open the app.
- **Industrial Haptics & Safety** (`ui/ControlScreen.kt`) - Advanced sensory feedback system. Features **Long-Press Protection** on critical safety buttons and differentiated haptic signatures (Success, Error, and Emergency pulses) to provide physical confirmation to the operator in noisy environments.
- **Toolchain & Project Quality** - AGP 9.3.1, Kotlin 2.2.10, compileSdk 36. Clean build output with zero warnings, optimized R8 production variants, and advanced **Roborazzi** screenshot testing.

**Status: Wi-Fi, Bluetooth, Biometrics, and Notifications implemented.** The app is a high-grade industrial console ready for mission-critical robot operation.

## 🚀 Building

Requires **a JDK 21 specifically** and the Android SDK.

1. Install [Android Studio](https://developer.android.com/studio).
2. Open the project root and let the Gradle sync finish.
3. Connect a device and press ▶️ Run, or use the scripts below.

### 🛠️ Manual Build Scripts
From a terminal at the repo root:

```bash
./build-android.sh     # Linux/macOS
build-android.bat      # Windows
```

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

**URTC platform** — robot tool head controllers
- **[URTC](https://github.com/JuanenRac/URTC)** — Universal Robot Tool Controller (STM32F303).
- **[URTC Flasher](https://github.com/JuanenRac/URTC-FLASHER)** / **[URTC Tester](https://github.com/JuanenRac/URTC-TESTER)** / **[URTC Web Studio](https://github.com/JuanenRac/URTC-WEB-STUDIO)**.

## 👤 Author

**JuanenRac** (Electro Hobby 3D)
📧 electrohobby3d@gmail.com
📺 youtube.com/@electrohobby3d

## 📜 License

GNU General Public License v3.0 (GPL-3.0) - see [`LICENSE`](LICENSE).
