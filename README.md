<p align="center">
  <img src="images/HYDRA_UMC_ANDROID_CONTROL_BANNER.jpg" alt="HYDRA-UMC Android Control Banner" width="100%">
</p>

# 📱 HYDRA-UMC ANDROID CONTROL

A native Android app (Kotlin + Jetpack Compose) that controls a robot on the [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) platform over Wi-Fi, speaking the exact same [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-STUDIO/blob/main/docs/REMOTE_API.md) contract [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) uses - discovery, full-state read/write, and live WebSocket sync against a running [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO) server. Direct Android counterpart to [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL). See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design.

## 🏗️ What's implemented

- **Discovery & connection** (`network/Discovery.kt`, `network/HydraApiClient.kt`, `network/HydraWebSocket.kt`) - subnet scan against `GET /api/hydra-info`, manual IP/port entry, `GET`/`POST /api/settings`, and a `/ws` live-sync connection with auto-reconnect.
- **State model** (`model/HydraState.kt`) - a thin, mutation-friendly view over the server's raw JSON tree rather than a strict schema, so unrecognized fields round-trip untouched instead of being dropped on write-back. Mirrors HYDRA-UMC SUITE's own `hydra_suite/models.py`.
- **UI** (`ui/DashboardScreen.kt`, `ui/ControlScreen.kt`, `ui/ThreeDScreen.kt`, `ui/SettingsScreen.kt`, Jetpack Compose + Navigation) - robot overview, jog/speed/tool/playback controls, an embedded WebView of the full HYDRA-UMC STUDIO UI, and connection settings with network scan.
- **ViewModel** (`viewmodel/RobotViewModel.kt`) - holds the `HydraState` mirror, keeps it in sync in both directions, and turns every jog/speed/tool/play/pause/stop action into a whole-object push (`POST /api/settings` or a WebSocket send), exactly like the browser UI's own read-modify-write pattern.
- Gradle project (AGP 8.1.0, Kotlin 1.8.10, compileSdk 33, minSdk 24) with the real Gradle wrapper checked in (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`), a launcher icon (adaptive on API 26+, vector fallback below), and `android:usesCleartextTraffic="true"` (the server is plain `http`/`ws`, no TLS, by design - see `AndroidManifest.xml`'s own comment).
- `build-android.sh` / `build-android.bat` - one-shot build + `adb install` to a connected device, with pre-flight checks for the Gradle wrapper, the Android SDK location, and the JDK version (AGP 8.1.0 needs JDK 17+ to run Gradle at all).
- `.vscode/` - recommended Gradle/Kotlin extensions, editor settings, and build tasks.

**Not implemented:** Bluetooth transport - honestly flagged as blocked on CM5-side work that doesn't exist yet anywhere in this ecosystem. See `docs/ARCHITECTURE.md` section 3.

## 🚀 Building

Requires a JDK 17+ (Android Studio bundles one under `.../Android Studio/jbr`) and the Android SDK (`local.properties` with `sdk.dir=...`, or `ANDROID_HOME`/`ANDROID_SDK_ROOT`). Then either open the repo root in Android Studio, or from a terminal:

```bash
./build-android.sh     # Linux/macOS/Git Bash
build-android.bat      # Windows cmd
```

Both build a debug APK (`app/build/outputs/apk/debug/app-debug.apk`) and install it on a connected device via `adb` if one is found.

## 🔗 Related Projects

This project is part of a larger robotics ecosystem by the same author (JuanenRac / Electro Hobby 3D). Worth knowing about, since a request might actually be about one of these rather than this repository:

**HYDRA-UMC platform** — the multi-robot micro-factory cell
- **[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)** — the motherboard itself: Raspberry Pi CM5 host + dual-core STM32H745 real-time co-processor, orchestrating up to 8 distributed robot arms over CAN-OTA/SPI-OTA. Own hardware + firmware, GPL-3.0/CERN-OHL-S v2/CC BY-SA 4.0.
- **[HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)** — web-based control dashboard for HYDRA-UMC: multi-robot 3D visualization, kinematics/trajectory recording, CAN-OTA flashing and testing for the whole platform. React + Vite + Three.js. Also the server this app talks to (`server.ts`, `docs/REMOTE_API.md`).
- **HYDRA-UMC-ANDROID-CONTROL** *(this repository)* — Android control app for HYDRA-UMC over Wi-Fi (Bluetooth pending CM5-side support).
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

GNU General Public License v3.0 (GPL-3.0) - see [`LICENSE`](LICENSE), matching the convention used elsewhere in this ecosystem for pure-software projects (e.g. [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO)).
