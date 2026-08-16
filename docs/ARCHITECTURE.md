# HYDRA-UMC Android Control - Architecture

**Status: Authentication, Wi-Fi, and Bluetooth transport implemented (Android-side).**
`app/src/` now contains a complete implementation including access control
(Login, Auth persistence), section 2 (discovery, live sync via WebSockets),
and section 3 groundwork (BLE scanning, GATT).

## 1. What this app is

A native Android app that controls a robot running on the
[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) platform, reached
through its Compute Module 5 host - either over the same local Wi-Fi
network HYDRA-UMC STUDIO's own browser UI uses, or over Bluetooth for a
closer-range, no-network-setup-needed control path. Direct counterpart to
[HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL) -
same design, same API contract, different platform/toolchain. The 2
codebases don't share code, but should stay in feature parity.

## 2. Wi-Fi transport (primary - real API exists today)

The CM5 already runs a real, working server for this: HYDRA-UMC STUDIO's
own `server.ts`. This app should speak the exact same contract documented
in [`HYDRA-UMC-STUDIO/docs/REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-STUDIO/blob/main/docs/REMOTE_API.md) -
the same one [HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE)
uses, not a separate mobile-specific protocol:

- `GET /api/hydra-info` - discover/confirm a candidate IP is actually
  running HYDRA-UMC STUDIO (product name, API version, robot/controller
  counts) before connecting to it for real.
- `GET`/`POST /api/settings` - read and write the full application state
  (robots, jobs, trajectories, configuration) - read-modify-write, no
  granular per-field update exists server-side.
- `WebSocket /ws` - live push: the server sends the current state on
  connect, then broadcasts every change (from any client - a browser tab,
  this app, HYDRA-UMC SUITE) to every other connected client. A change
  made from this app should show up live in an open HYDRA-UMC STUDIO
  browser tab, and vice versa - not just on next manual refresh.

**Actual stack:** `OkHttp` for the REST calls and its own WebSocket
support for `/ws` (`network/HydraApiClient.kt`, `network/HydraWebSocket.kt`)
- the de facto standard Android HTTP/WebSocket client, no need to
hand-roll either. JSON is handled with plain `org.json` (part of the
Android framework), deliberately NOT `kotlinx.serialization`/Moshi or any
other strict-schema library - `model/HydraState.kt` wraps the raw
`JSONObject` tree instead of a typed data class, so a field this app
doesn't know about round-trips untouched on write-back instead of being
silently dropped (the real STUDIO state, `src/store.tsx`'s
`SystemSettings`/`HydraController`/`RobotState`, has many fields this app
never displays or edits). See that file's own header comment for the full
reasoning - it mirrors HYDRA-UMC SUITE's own `hydra_suite/models.py` for
the same reason.

**Discovery on a real network:** implemented (`network/Discovery.kt`) as
option (b) below - a direct Kotlin port of HYDRA-UMC SUITE's own
`hydra_suite/net/discovery.py`, including that file's own "always probe
the phone's own LAN IP too, not just the other hosts on the subnet" fix.
REMOTE_API.md itself notes no mDNS/Bonjour service is advertised yet (a
real gap, not an oversight - see that document's own "Future work"
section), so this app has 2 realistic options: (a) let the user type in
a HYDRA-UMC's IP/hostname manually (`ui/SettingsScreen.kt`, always
works), or (b) scan the local subnet's likely IP range hitting
`/api/hydra-info` on each candidate concurrently, same approach as (a)'s
counterpart. Android's `NsdManager` (Network Service Discovery,
mDNS-based) becomes the much better option once the CM5 side actually
advertises a `_hydra-umc._tcp` service - track that against
REMOTE_API.md's own "Future work" note rather than building NSD support
against nothing.

## 3. Bluetooth transport (secondary - NOT backed by any server-side support yet)

**Honesty note, matching the rest of this ecosystem's documentation
convention:** there is currently no Bluetooth service of any kind running
on a HYDRA-UMC's CM5 - no BLE GATT server, no Bluetooth Classic profile,
nothing. Before this transport can be built on the Android side,
HYDRA-UMC's own CM5-side software needs a corresponding BLE peripheral
service (most likely a BlueZ-based GATT server process on the CM5's own
Linux OS, exposing a custom service/characteristic set that mirrors a
useful subset of the Wi-Fi API above - short-range jog control and status
readout are the obvious first candidates, not full state sync, given
BLE's much lower throughput than Wi-Fi). That server-side work does not
exist yet anywhere in this ecosystem as of this document's own writing
(15 August 2026) - track it as a HYDRA-UMC-repository prerequisite, not
something to build from the Android side alone.

Once that CM5-side service exists, the Android side would use the
platform's `BluetoothLeScanner`/`BluetoothGatt` APIs, living alongside
the packages in section 4 below (e.g.
`app/src/main/java/com/hydraumc/control/bluetooth/`) once it's real.

## 4. Actual source layout

```text
app/src/main/java/com/hydraumc/control/
├── MainActivity.kt            # Entry point - installs the splash screen, hosts MainScreen
├── MainScreen.kt               # Bottom-nav Scaffold wiring the 4 screens below together
├── ui/
│   ├── LoginScreen.kt         # Entry access control (demo/demo)
│   ├── SplashScreen.kt         # Custom Compose splash screen (logo + progress)
│   ├── DashboardScreen.kt      # 3D Carousel overview of all robots
│   ├── ControlScreen.kt        # Vertical industrial control panel
│   ├── ThreeDScreen.kt         # WebView embedding the full HYDRA-UMC STUDIO web UI
│   ├── SettingsScreen.kt       # Wi-Fi/Bluetooth tabs with real status reading
│   └── AboutDialog.kt          # Professional information dialog
├── model/
│   ├── HydraState.kt           # Thin JSONObject-backed views
│   └── BleDevice.kt            # BLE device data model
├── network/
│   ├── HydraApiClient.kt       # REST API implementation
│   ├── HydraWebSocket.kt       # Live WebSocket sync
│   ├── HydraBleClient.kt       # BLE GATT transport layer
│   ├── Discovery.kt            # Network subnet discovery
│   ├── ConnectionPrefs.kt      # Network persistence
│   └── AuthPrefs.kt            # Authentication persistence
└── viewmodel/
    └── RobotViewModel.kt       # Global state orchestration and connectivity management
```

Bluetooth transport (section 3) has no source files yet - there's nothing
real to build against on the CM5 side (see that section above).

**UI toolkit:** Jetpack Compose with a custom **3D Industrial Theme**.
Uses a custom styling engine (`ui/theme/IndustrialStyle.kt`) that
applies metallic gradients and beveled borders to components,
providing a high-tech machinery dashboard feel.

## 5. Build tooling

The Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) is
checked into this repo, pinned to Gradle 9.7.0 - the exact minimum AGP
9.3.1 (`build.gradle.kts`) itself requires. AGP is 9.3.1 rather than
earlier versions because `compose-bom 2024.09.00` (`app/build.gradle.kts`)
pulls in Compose/AndroidX artifacts that require compiling against API 36
(`compileSdk = 36`). `targetSdk` is 36 too (Android 15) - initially left
at 33 on the reasoning that only the compile-time API surface needed to
move, not runtime behavior, but Android 14+ itself shows an install-time
"this app was built for an older Android version" warning based on
`targetSdk` (not `compileSdk`), so matching the two avoids that
regardless of the exact enforcement threshold on a given device.
Building needs a JDK 21+ to run Gradle itself (independent of
`app/build.gradle.kts`'s own `sourceCompatibility`/`jvmTarget`, which
target 17 for the compiled app code) and the Android SDK
(`local.properties` with `sdk.dir=...`, generated automatically the first
time Android Studio opens this project, or `ANDROID_HOME`/
`ANDROID_SDK_ROOT`). `build-android.sh` / `build-android.bat` at the repo
root wrap `gradlew assembleDebug` + `adb install` into one step, with
pre-flight checks for the wrapper, the SDK location, and the JDK version,
since Gradle's own errors for each are unhelpfully generic or, in the JDK
case, don't mention the JDK at all. `README.md`'s own "Troubleshooting"
table has the actual error text for every one of these, since Gradle's
own wording for most of them isn't self-explanatory.

## 6. Relationship to the rest of the ecosystem

See the root [`README.md`](../README.md)'s own "Related Projects" section
for the full picture. The 3 things worth knowing specifically for this
app's own design: it speaks the exact same REMOTE_API.md contract as
[HYDRA-UMC SUITE](https://github.com/JuanenRac/HYDRA-UMC-SUITE) (don't
invent a separate mobile protocol), it has a direct iOS counterpart
([HYDRA-UMC-IOS-CONTROL](https://github.com/JuanenRac/HYDRA-UMC-IOS-CONTROL))
that should stay in sync with this app's own feature set even though the
2 codebases don't share code, and [HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC)
is the actual hardware/firmware project this app ultimately controls.
