<p align="center">
  <img src="images/HYDRA_UMC_ANDROID_CONTROL_BANNER.jpg" alt="HYDRA-UMC Android Control Banner" width="100%">
</p>

# 📱 HYDRA-UMC ANDROID CONTROL

A native Android app (Kotlin + Jetpack Compose) that controls a robot on the [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) platform over Wi-Fi, speaking the exact same [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-STUDIO/blob/main/docs/REMOTE_API.md) contract [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) uses - discovery, full-state read/write, and live WebSocket sync against a running [HYDRA-UMC STUDIO](https://github.com/JuanenRac/HYDRA-UMC-STUDIO) server. Direct Android counterpart to [HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL). See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design.

## 🏗️ What's implemented

- **Discovery & connection** (`network/Discovery.kt`, `network/HydraApiClient.kt`, `network/HydraWebSocket.kt`) - subnet scan against `GET /api/hydra-info`, manual IP/port entry, `GET`/`POST /api/settings`, and a `/ws` live-sync connection with auto-reconnect.
- **State model** (`model/HydraState.kt`) - a thin, mutation-friendly view over the server's raw JSON tree rather than a strict schema, so unrecognized fields round-trip untouched instead of being dropped on write-back. Mirrors HYDRA-UMC SUITE's own `hydra_suite/models.py`.
- **Discovery & connection** (`network/Discovery.kt`, `network/HydraApiClient.kt`, `network/HydraWebSocket.kt`, `network/HydraBleClient.kt`) - subnet scan against `GET /api/hydra-info`, manual IP/port entry, Bluetooth Low Energy (BLE) scanning and GATT connection, `GET`/`POST /api/settings`, and a `/ws` live-sync connection.
- **UI** (`ui/DashboardScreen.kt`, `ui/ControlScreen.kt`, `ui/ThreeDScreen.kt`, `ui/SettingsScreen.kt`, `ui/SplashScreen.kt`) - robot overview, jog/speed/tool/playback controls, an embedded WebView of the full HYDRA-UMC STUDIO UI, and professional settings with **Wi-Fi/Bluetooth tabs**. Now featuring a custom **3D Industrial Theme** with metallic finishes and beveled components.
- **ViewModel** (`viewmodel/RobotViewModel.kt`) - holds the `HydraState` mirror, keeps it in sync in both directions, and turns every jog/speed/tool/play/pause/stop action into a whole-object push (`POST /api/settings` or a WebSocket send), exactly like the browser UI's own read-modify-write pattern.
- Gradle project (AGP 9.3.1, Kotlin 2.2.10, compileSdk 36, targetSdk 36, minSdk 24) with the real Gradle wrapper checked in (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`), a launcher icon (adaptive on API 26+, vector fallback below), and `android:usesCleartextTraffic="true"` (the server is plain `http`/`ws`, no TLS, by design - see `AndroidManifest.xml`'s own comment).
- **Testing** - Unit and UI tests using JUnit 4, Robolectric, and **Roborazzi** for screenshot testing (`app/src/test/java/com/hydraumc/control/`).
- `build-android.sh` / `build-android.bat` - one-shot build + `adb install` to a connected device, with pre-flight checks for the Gradle wrapper, the Android SDK location, and the JDK version (AGP 9.3.1 needs JDK 21+ to run Gradle at all).
- `.vscode/` - recommended Gradle/Kotlin extensions, editor settings, and build tasks.

**Status: Wi-Fi and Bluetooth (Android-side) implemented.** This app features a hybrid transport system. It speaks the standard JSON contract over Wi-Fi/WebSockets and has a preliminary BLE GATT implementation ready for the upcoming CM5-side Bluetooth service.

## 🚀 Building

Requires **a JDK 21 specifically** (not older, not "whatever's newest") and the Android SDK. The easiest way to get both at once, correctly matched, is Android Studio:

1. Install [Android Studio](https://developer.android.com/studio).
2. Open it → **Open** → select this repo's root folder, and let the initial Gradle sync finish. This generates `local.properties` (`sdk.dir=...`) automatically and, if `compileSdk 36` isn't installed yet, Android Studio shows an "Install missing platform(s) and sync project" prompt at the top - accept it.
3. Either build from inside Android Studio (connect a phone via USB with USB debugging on, pick it from the device dropdown, press the green ▶️ Run button - this always uses Android Studio's own bundled JDK, so JDK version mismatches never come up), or close it and use the terminal scripts below (they'll find the `local.properties` Android Studio just generated).

Without Android Studio, both of the above have to be set up by hand instead:
- **JDK 21**: install one (e.g. [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21)) and point `JAVA_HOME` at it. Don't grab whatever the latest available JDK is - this project pins Gradle 9.7.0 (`gradle/wrapper/gradle-wrapper.properties`), and Gradle 9.7.0 doesn't know how to run on a JDK much older than 21 (see Troubleshooting below); JDK 21 is the version this whole toolchain (AGP 9.3.1 / Gradle 9.7.0 / Kotlin 2.2.10) is actually validated against.
- **Android SDK**: install the ["command line tools only"](https://developer.android.com/studio) package, set `ANDROID_HOME` to where you extracted it, then `sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0" && sdkmanager --licenses`.

Then, from a terminal at the repo root:

```bash
./build-android.sh     # Linux/macOS/Git Bash
build-android.bat      # Windows cmd
```

Both build a debug APK (`app/build/outputs/apk/debug/app-debug.apk`) and install it on a connected device via `adb` if one is found. Each script checks for the 3 things above (Gradle wrapper present, SDK location, JDK version) before touching Gradle, since Gradle's own errors for a missing/wrong one of these are either generic (`SDK location not found` - at least that one's clear) or actively misleading (see Troubleshooting).

> [!NOTE]
> **APK Size:** The debug APK is relatively large (~18MB) because it includes full debug symbols and the complete Material Design extended icons library. For production, generating the **release** variant (`./gradlew assembleRelease`) enables R8 minification and resource shrinking, which reduces the size to a few megabytes by stripping out unused code and icons.

### 📲 Testing against a real HYDRA-UMC STUDIO server

1. On the same computer running HYDRA-UMC STUDIO: `cd HYDRA-UMC-STUDIO && npm run dev` (starts `server.ts` on port 3000). Check Settings → Integrations → "Remote App Access" is enabled, and that Windows Firewall isn't blocking inbound port 3000 on your private network (it may prompt the first time the server starts).
2. Find that computer's LAN IP (`ipconfig`, look for the Wi-Fi adapter).
3. Install this app on a phone connected to **the same Wi-Fi network** (see "Building" above) - phone and server must be able to reach each other directly; a "guest"/isolated Wi-Fi network that blocks device-to-device traffic will prevent discovery even on the same SSID.
4. In the app's Settings tab, either type in that IP + port `3000` and "Guardar y Conectar", or tap "Escanear" to subnet-scan and pick the server from the results.
5. Once connected, changes made from the phone (jog, tool change, play/pause/stop) should appear live in an open HYDRA-UMC STUDIO browser tab, and vice versa - both go through the same `/ws` (`docs/REMOTE_API.md` section 3 in HYDRA-UMC STUDIO).

> [!TIP]
> **Remote testing:** While local Wi-Fi is the primary path, you can also test from outside your network by configuring a **NAT/Port Forwarding** rule on your router. Map an external port to the server's internal IP and port 3000 (TCP/UDP). In the app, use your router's public IP address to connect.

### 🩺 Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable or by setting the sdk.dir path in your project's local properties file` | No `local.properties` and no `ANDROID_HOME`/`ANDROID_SDK_ROOT` | Open the project once in Android Studio (generates `local.properties`), or set `ANDROID_HOME` by hand |
| `No matching variant of com.android.tools.build:gradle:9.3.1 was found ... compatible with Java 17` | Active JDK is 17 (or older) | Install/select a JDK 21, point `JAVA_HOME` at it |
| Build fails almost instantly with a bare, unhelpful `* What went wrong: 26.0.2` (or any other version-number-only message), sometimes preceded by `WARNING: A restricted method in java.lang.System has been called` | Active JDK is *too new* - Gradle 9.7.0 (pinned by this project) doesn't know how to run on a JDK much past ~23; it doesn't fail with a clear "unsupported JDK" message, just a cryptic one like this | Install/select a JDK **21** specifically, not whatever the newest available JDK happens to be - point `JAVA_HOME` at it |
| `:app:checkDebugAarMetadata FAILED` listing many `androidx.*` dependencies that "require... to compile against version 36 or later of the Android APIs" | A dependency bump (Compose BOM, AndroidX libraries) pulled in artifacts compiled against a newer API level than this project's own `compileSdk` | Bump `compileSdk` in `app/build.gradle.kts` to match (currently 36) - independent of `targetSdk`, which doesn't need to move with it. If AGP's own max-recommended `compileSdk` is below what's needed (check the same error message), AGP needs bumping too - AGP 9.3.1 supports `compileSdk 36` while still only requiring Gradle 9.7.0 (this project's own pinned wrapper version) |
| App connects to nothing / scan finds nothing, but the server is definitely running | Phone and server aren't actually reachable to each other despite being on the "same" Wi-Fi | Check for router client/AP isolation (common on guest networks), Windows Firewall blocking port 3000, and `remoteAccess.enabled` in HYDRA-UMC STUDIO's own settings not being set to `false` |
| Phone shows "This app was built for an older version of Android" during install | `targetSdk` below the installing device's own enforced floor - Android 14 specifically refuses to install anything with `targetSdk < 23` outright | `targetSdk` is 36 (this project's own current value, matching `compileSdk`) - if it's showing anyway, the installed APK is stale from an earlier build attempt with an older `targetSdk`; uninstall the app from the phone first, then rebuild (`gradlew clean` if a plain rebuild doesn't help) and reinstall rather than upgrading in place |

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
