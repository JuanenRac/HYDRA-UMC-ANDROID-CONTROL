<p align="center">
  <img src="images/HYDRA_UMC_ANDROID_CONTROL_BANNER.jpg" alt="HYDRA-UMC Android Control Banner" width="100%">
</p>

# 📱 HYDRA-UMC CONTROL

A native Android app (Kotlin + Jetpack Compose) that controls a robot on the [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) platform over Wi-Fi or Bluetooth, speaking the exact same [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-STUDIO/blob/main/docs/REMOTE_API.md) contract [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) uses - discovery, full-state read/write, and live WebSocket sync against a running [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO) server. Direct Android counterpart to [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL). See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design.

## 🏗️ What's implemented

- **Access Control & Authentication** (`ui/LoginScreen.kt`, `network/AuthPrefs.kt`) - Professional login system with "Remember me" functionality and persistent session storage. Includes a comprehensive **User Profile** manager for account details and a secure **Logout** mechanism. Fully synchronized in **5 languages** (EN, ES, DE, FR, IT).
- **Industrial Telemetry Terminal** (`ui/TelemetryScreen.kt`) - A dedicated real-time log viewer with a terminal-style interface. Tracks system events, REST/WebSocket synchronization, and provides color-coded diagnostics (Matrix Green for success, Industrial Red for errors). Accessible globally via the top header bar.
- **Advanced Dashboard** (`ui/DashboardScreen.kt`) - High-fidelity **3D Horizontal Carousel** with perspective swipe effects. Now displays enriched robot metadata: **Manufacturer** (Source Robotics, Annin, Universal Robots, AgileX, etc.), **Robot Role** (CNC, Laser, PnP), and an **Industrial Module Matrix** (live indicators for CAM, XY, ATC, PNP, CNC, LSR, BED, VAC modules).
- **System Health Monitor** (`ui/DashboardScreen.kt`) - Real-time metrics for the connected Compute Module 5, including **Hostname**, **Formatted Uptime** (e.g., "2d 4h 15m"), and active counts for controllers and robots.
- **Enhanced Manual Control** (`ui/ControlScreen.kt`) - Features a professional vertical layout with **50% larger Joystick buttons** for maximum precision. Includes a **Job/Trajectory Selector** to browse and execute files directly from the server.
- **Safety & Playback Panel** (`ui/ControlScreen.kt`) - Fixed bottom control bar housing the **E-STOP (Emergency Stop)**, **Start**, **Pause**, and **Stop** buttons. These controls are always visible regardless of scrolling and feature **Haptic Feedback** (physical vibration) for sensory confirmation of every command.
- **Discovery & Connection** (`network/Discovery.kt`, `network/HydraApiClient.kt`, `network/HydraWebSocket.kt`, `network/HydraBleClient.kt`) - Dual-transport system: **Wi-Fi Subnet Scanning** and **Bluetooth Low Energy (BLE)** scanning with real-time hardware status detection. Features a **Global Server Selector** in the header for rapid switching between robotic cells.
- **Kinematic Sync Logic** (`model/HydraState.kt`) - Advanced state mapping that handles double-level synchronization. Updates coordinate mirrors (`pos.tx`/`pos.ty`) and controller-level gantry stages (`kinematicBrainStage`) to ensure direct hardware response and seamless browser-to-mobile parity.
- **Industrial 3D Theme** (`ui/theme/`) - A metallic visual engine providing a machinery dashboard feel. Components feature beveled borders, gradients, and **Status LEDs**.
- **Toolchain & Project Quality** - AGP 9.3.1, Kotlin 2.2.10, compileSdk 36. Clean build output with zero warnings, optimized R8 production variants, and advanced **Roborazzi** screenshot testing.

**Status: Wi-Fi and Bluetooth (Android-side) implemented.** The app is a high-grade industrial console ready for mission-critical robot operation.

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
4. **Haptics Note:** Ensure vibration is enabled in your device settings to experience the physical command confirmation.

## 🩺 Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| No Haptics | System vibration off | Enable "Touch Feedback" in Android Sound/Vibration settings |
| Robot won't move | Browser cerebral link | Keep a HYDRA-UMC STUDIO browser tab open for IK processing |
| Bluetooth disabled | Physical chip off | Use the "ENABLE SYSTEM BT" 3D button in the app |
| Stale Server List | WebSocket Drop | The app clears servers on real network loss for safety |

## 🔗 Related Projects

This project is part of a larger robotics ecosystem by the same author (JuanenRac / Electro Hobby 3D). Worth knowing about, since a request might actually be about one of these rather than this repository:

**HYDRA-UMC platform** — the multi-robot micro-factory cell
- **[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)** — the motherboard itself: Raspberry Pi CM5 host + dual-core STM32H745 real-time co-processor, orchestrating up to 8 distributed robot arms over CAN-OTA/SPI-OTA. Own hardware + firmware, GPL-3.0/CERN-OHL-S v2/CC BY-SA 4.0.
- **[HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)** — web-based control dashboard for HYDRA-UMC: multi-robot 3D visualization, kinematics/trajectory recording, CAN-OTA flashing and testing for the whole platform. React + Vite + Three.js. Also the server this app talks to (`server.ts`, `docs/REMOTE_API.md`).
- **HYDRA-UMC-CONTROL** *(this repository)* — Android control app for HYDRA-UMC over Wi-Fi and Bluetooth.
- **[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL)** — iOS control app for HYDRA-UMC, same contract, direct counterpart to this app.
- **[HYDRA-UMC-SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE)** — desktop (Python/PySide6) swarm command center: multi-controller network discovery, live bidirectional sync, real 3D robot viewport, Photoshop-style dockable workspace.

**URTC platform** — the tool head controller every HYDRA-UMC robot arm carries
- **[URTC](https://github.com/JuanenRac/URTC)** — Universal Robot Tool Controller: STM32F303-based CAN bus tool head controller, 25 fully-implemented tool profiles, CAN-OTA firmware update.
- **[URTC Flasher](https://github.com/JuanenRac/URTC-FLASHER)** — desktop CAN-OTA + full-chip SWD/JTAG flashing tool for URTC boards (Windows/Linux).
- **[URTC Tester](https://github.com/JuanenRac/URTC-TESTER)** — desktop live CAN-bus diagnostic tool for URTC boards, one panel per tool profile (Windows/Linux).
- **[URTC Web Studio](https://github.com/JuanenRac/URTC-WEB-STUDIO)** — browser-based alternative to the 2 desktop tools above (Web Serial API + SLCAN), no local install needed.

## 👤 Author

**JuanenRac** (Electro Hobby 3D)
📧 electrohobby3d@gmail.com
📺 youtube.com/@electrohobby3d

## 📜 License

GNU General Public License v3.0 (GPL-3.0) - see [`LICENSE`](LICENSE).
