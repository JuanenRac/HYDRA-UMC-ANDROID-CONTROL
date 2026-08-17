# HYDRA-UMC Android Control - Architecture

**Status: Industrial Console Implementation Completed (Wi-Fi, Bluetooth, Biometrics, Notifications).**
`app/src/` now contains a high-grade professional console implementation including advanced security, persistent caching, and high-speed atomic command synchronization.

## 1. What this app is

A native Android app (Kotlin + Jetpack Compose) that serves as the primary console for robots running on the
[HYDRA-UMC](https://github.com/JuanenRac/HYDRA-UMC) platform. It provides professional-grade control over the Compute Module 5 host, featuring an industrial 3D UI, real-time telemetry, and multi-robot orchestration.

## 2. Wi-Fi transport (Primary Industrial Path)

The app implements a hybrid transport layer speaking the [`REMOTE_API.md`](https://github.com/JuanenRac/HYDRA-UMC-STUDIO/blob/main/docs/REMOTE_API.md) contract:

- **Atomic Commands (New)**: Uses `POST /api/robot/:id/command` for high-speed, low-latency control of motion (Jog, Play, Pause, Stop) and peripherals (Valves, Pumps). This avoids the overhead of full state overwrites and prevents data collisions in multi-user environments.
- **WebSocket /ws**: Maintains a live bidirectional sync for full application state. Every change in the robot's physical position or module status is pushed to the app in real-time.
- **REST Discovery**: Subnet scanning against `/api/hydra-info` for automatic server identification.

## 3. Industrial Features & Roadmap

- **Biometry of Planta**: Integrated `androidx.biometric` support for **Fingerprint and Face Unlock**. Allows operators in industrial environments to access control systems without removing gloves to type passwords.
- **Mission Notifications**: A background alerting system (`util/NotificationHelper.kt`) that monitors job status. Uses high-priority Android notification channels to alert the operator when a task is completed or if a hardware fault occurs.
- **State Cache & Offline Mode**: Implements a persistent caching engine using **DataStore**. The app saves the last known system state, allowing for configuration audits and dashboard viewing even when disconnected from the robotic cell.
- **Real-time MJPEG Streaming**: Integrated industrial MJPEG viewer in the Camera tab, pulling directly from the CM5's `/api/camera/:id/stream` endpoint for zero-latency visual monitoring.
- **Headless 3D Visualization**: Optimizes the embedded 3D scene by requesting a "headless" mode (`?hideUI=true`), which hides server-side web chrome to provide a native-like full-screen 3D experience.

## 4. Actual source layout

```text
app/src/main/java/com/hydraumc/control/
├── MainActivity.kt            # Entry point - handles splash, login flow, and E-STOP actions
├── MainScreen.kt               # Navigation Scaffold wiring the 5 core screens below
├── ui/
│   ├── LoginScreen.kt         # Biometric-aware access control
│   ├── DashboardScreen.kt      # 3D Carousel + System Health + Module Matrix
│   ├── ControlScreen.kt        # Industrial Joystick + URTC Tooling + I/O Controls
│   ├── CameraScreen.kt         # Real-time MJPEG video streaming viewer
│   ├── ThreeDScreen.kt         # Headless 3D visualization (WebView optimized)
│   ├── TelemetryScreen.kt      # Terminal-style real-time log console
│   └── UserProfileDialog.kt    # Account management + Biometric toggle
├── model/
│   └── HydraState.kt           # JSONObject views with kinematic mirror logic (pos.tx/ty)
├── network/
│   ├── HydraApiClient.kt       # REST implementation with Atomic Command support
│   ├── HydraWebSocket.kt       # Live sync with echo-guard and auto-reconnect
│   ├── StateCache.kt           # Persistent DataStore state storage
│   └── AuthPrefs.kt            # Secure credential and biometric settings persistence
├── util/
│   ├── BiometricHelper.kt      # Fingerprint/Face authentication manager
│   ├── NotificationHelper.kt   # High-priority mission alert dispatcher
│   └── BiometricHelper.kt      # Hardware-level biometric logic
└── viewmodel/
    └── RobotViewModel.kt       # Central orchestration, metrics polling, and telemetry engine
```

## 5. Build tooling

The project targets **Android 15 (API 36)** and requires **JDK 21**. 
- **Build Scripts**: `build-android.bat/sh` are localized in English and provide pre-flight checks for the environment.
- **Dependencies**: Uses `OkHttp 4.10`, `DataStore 1.1`, `Biometric 1.2`, and `Media 1.7` (for stylized notifications).

## 6. Relationship to the rest of the ecosystem

The app is synchronized with **HYDRA-UMC STUDIO** (Server) and **URTC** (Tooling). It enforces the 26-tool URTC standard and implements server-side kinematics (IK) support to allow for autonomous robot movement independent of any active browser sessions.
