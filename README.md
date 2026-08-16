<p align="center">
  <img src="images/HYDRA_UMC_ANDROID_CONTROL_BANNER.jpg" alt="HYDRA-UMC Android Control Banner" width="100%">
</p>

# 📱 HYDRA-UMC CONTROL

A native Android app (Kotlin + Jetpack Compose) that controls a robot on the [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) platform over Wi-Fi or Bluetooth, speaking the exact same [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-STUDIO/blob/main/docs/REMOTE_API.md) contract [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) uses - discovery, full-state read/write, and live WebSocket sync against a running [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO) server. Direct Android counterpart to [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL). See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design.

## 🏗️ What's implemented

- **Access Control & Authentication** (`ui/LoginScreen.kt`, `network/AuthPrefs.kt`) - Secure-style login system with demo credentials (`demo`/`demo`). Includes a "Remember me" feature that persists session data for automatic login on subsequent app launches, providing a professional entry flow.
- **Global Header & Connectivity** (`MainScreen.kt`) - A centralized control bar featuring a **Global Server Selector** (ComboBox) that lists all detected HYDRA-UMC instances. This selector is visible from any screen (Dashboard, Control, Camera, 3D, Settings) and automatically clears stale entries if the connection is lost.
- **Discovery & Connection** (`network/Discovery.kt`, `network/HydraApiClient.kt`, `network/HydraWebSocket.kt`, `network/HydraBleClient.kt`) - Subnet scanning against `GET /api/hydra-info`, manual IP/port entry, and a robust `/ws` live-sync connection with auto-reconnect. Now includes **Bluetooth Low Energy (BLE)** scanning and GATT transport, featuring real-time hardware status detection (detects if the phone's Bluetooth is physically enabled).
- **State model** (`model/HydraState.kt`) - A thin, mutation-friendly view over the server's raw JSON tree rather than a strict schema, so unrecognized fields round-trip untouched instead of being dropped on write-back. Mirrors HYDRA-UMC SUITE's own `hydra_suite/models.py`.
- **Dashboard & Visualization** (`ui/DashboardScreen.kt`, `ui/theme/`) - A high-end **3D Horizontal Carousel** allowing users to swipe between different robots with perspective and scale effects. Features color-coded server status: **Connected** (Green), **Connecting** (Orange), and **Disconnected** (Red).
- **Manual Control Panel** (`ui/ControlScreen.kt`) - Professional vertical layout with jog joystick, speed/acceleration sliders, and ATC tool change controls. Action buttons (Play, Pause, Stop) are arranged vertically for better ergonomics on mobile devices.
- **Camera Streaming** (`ui/CameraScreen.kt`) - A dedicated section for real-time video monitoring, prepared for integration with the CM5 camera server.
- **3D Viewer** (`ui/ThreeDScreen.kt`) - An embedded WebView showing the full HYDRA-UMC STUDIO 3D visualization for real-time kinematic feedback.
- **Industrial 3D Theme** (`ui/theme/`) - A complete visual overhaul using a **Metallic Dark Blue** palette. Components feature beveled borders, vertical gradients, and "Status LEDs" for a high-tech machinery dashboard feel.
- **Gradle project** (AGP 9.3.1, Kotlin 2.2.10, compileSdk 36, targetSdk 36, minSdk 24) with the real Gradle wrapper checked in (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`), a launcher icon (adaptive on API 26+), and optimized build variants.
- **Build Scripts** (`build-android.sh` / `build-android.bat`) - One-shot build + `adb install` to a connected device, with pre-flight checks for the Gradle wrapper, the Android SDK location, and the JDK version (requires JDK 21+).
- **Advanced Testing** - Unit and UI tests using JUnit 4, Robolectric, and **Roborazzi** for pixel-perfect screenshot verification.

**Status: Wi-Fi and Bluetooth (Android-side) implemented.** The app features a hybrid transport system. It is fully ready for the upcoming CM5-side Bluetooth peripheral service.

## 🚀 Building

Requires **a JDK 21 specifically** (not older, not "whatever's newest") and the Android SDK. The easiest way to get both at once, correctly matched, is Android Studio:

1. Install [Android Studio](https://developer.android.com/studio).
2. Open it → **Open** → select this repo's root folder, and let the initial Gradle sync finish. This generates `local.properties` (`sdk.dir=...`) automatically and installs missing platform SDKs.
3. Either build from inside Android Studio (connect a phone via USB with USB debugging on, pick it from the device dropdown, press the green ▶️ Run button), or close it and use the terminal scripts below.

### 🛠️ Manual Setup (Without Android Studio)
- **JDK 21**: Install one (e.g. [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21)) and point `JAVA_HOME` at it. This project pins Gradle 9.7.0.
- **Android SDK**: Install the ["command line tools only"](https://developer.android.com/studio), set `ANDROID_HOME`, then run `sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0" && sdkmanager --licenses`.

Then, from a terminal at the repo root:

```bash
./build-android.sh     # Linux/macOS/Git Bash
build-android.bat      # Windows cmd
```

Both build a debug APK (`app/build/outputs/apk/debug/app-debug.apk`) and install it on a connected device via `adb`.

> [!NOTE]
> **APK Size Optimization:** The debug APK (~18MB) includes full symbols. For a production-ready version, generate the **release** variant (`./gradlew assembleRelease`). This enables R8 minification and resource shrinking, reducing the size to a few megabytes by stripping unused icons and code.

### 📲 Testing against a real HYDRA-UMC STUDIO server

1. On the computer running HYDRA-UMC STUDIO: `cd HYDRA-UMC-STUDIO && npm run dev`. Ensure Settings → Integrations → "Remote App Access" is enabled and Windows Firewall allows port 3000.
2. Find the computer's LAN IP (`ipconfig`).
3. Connect the phone to the **same Wi-Fi network**.
4. In the app's Settings tab, use the **Global Server Selector** or enter the IP/port manually.
5. Once connected, changes made from the phone (jog, tool change, playback) appear live in the browser UI and vice versa.

> [!TIP]
> **Remote testing:** While local Wi-Fi is primary, you can test from outside your network by configuring a **NAT/Port Forwarding** rule on your router. Map an external port to the server's internal IP and port 3000 (TCP/UDP). Use your public IP to connect.

### 🩺 Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `SDK location not found` | Missing `local.properties` or `ANDROID_HOME` | Open project in AS once or set `ANDROID_HOME` manually |
| `Incompatible Java version` | Active JDK is not 21 | Install JDK 21 and point `JAVA_HOME` at it |
| `AAR metadata FAILED` | compileSdk version mismatch | Ensure `compileSdk` in `build.gradle.kts` is 36 |
| App connects to nothing | Network isolation or Firewall | Check router AP isolation and Firewall port 3000 status |
| "Built for an older version" | `targetSdk` floor issue | Rebuild with `gradlew clean` and ensure `targetSdk` is 36 |

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
