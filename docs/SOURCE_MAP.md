# Source map

Every file of the repository with a line on what it does: the annotated tree that the README carried before it was reduced to the first level (the first level, in eight languages, is in the section Repository Structure of the README).

```text
├── app/
│   ├── build.gradle.kts          # App module Gradle config - AGP/Kotlin/Compose versions, dependencies, debug-signed release build type
│   ├── version.properties        # versionMajor/Minor/Patch/Code (bumped only by the explicit `bump` command)
│   ├── lint.xml                  # Android lint settings
│   ├── proguard-rules.pro        # Release-build code shrinking/obfuscation rules
│   ├── src/main/
│   │   ├── AndroidManifest.xml   # Permissions, activity/receiver declarations, usesCleartextTraffic (the LAN server speaks plain HTTP unless TLS is enabled on it; every call needs a signed-in token)
│   │   ├── java/com/hydraumc/control/
│   │   │   ├── MainActivity.kt          # Entry point - splash, login/main screen gating, cold-start-safe global E-STOP handling
│   │   │   ├── MainScreen.kt            # Bottom-nav scaffold, top bar (server selector, profile, telemetry, settings)
│   │   │   ├── kinematics/
│   │   │   │   └── Parol6Kinematics.kt   # Parol6-specific forward/inverse kinematics
│   │   │   ├── model/
│   │   │   │   ├── BleDevice.kt          # Bluetooth LE scan result data class
│   │   │   │   └── HydraState.kt         # settings.json field-by-field mirror (RobotView/ControllerView/JobView) + ServerInfo discovery model
│   │   │   ├── network/
│   │   │   │   ├── AuthPrefs.kt           # Encrypted (AES256-GCM) credential/session storage
│   │   │   │   ├── ConnectionPrefs.kt     # Persisted server IP/port (DataStore Preferences)
│   │   │   │   ├── Discovery.kt           # Concurrent /24 subnet scan (primary) + NSD/mDNS listener (secondary) for finding a server on the LAN
│   │   │   │   ├── AccessModels.kt         # Controller id, reservations, safety limits and the jog check (pure rules)
│   │   │   │   ├── HydraApiClient.kt      # REST client - login, settings read/write, atomic robot commands, system metrics
│   │   │   │   ├── HydraBleClient.kt      # Bluetooth GATT client, alternative transport to Wi-Fi
│   │   │   │   ├── BleContract.kt / BleGatt.kt / AndroidBleGatt.kt   # BLE contract, GATT abstraction and its Android implementation
│   │   │   │   ├── TlsSettings.kt / HydraHttp.kt / TlsPrefs.kt      # Optional TLS: modes, pinned fingerprint, custom CA, storage
│   │   │   │   ├── HydraWebSocket.kt      # Live state-delta push over WS, reconnect handling
│   │   │   │   ├── StateCache.kt          # Last-known-state cache (DataStore) for offline dashboard viewing
│   │   │   │   ├── StopAllResult.kt       # Result of the global STOP (per-robot answer, status)
│   │   │   │   ├── ContractV2.kt          # The server's second contract as pure rules: conditional writes by robot revision, the atomic table move and the audit record
│   │   │   │   ├── BleCommands.kt         # One robot command to send over Bluetooth LE and its acknowledgement (see docs/BLE_CONTRACT.md)
│   │   │   │   └── BlePermissions.kt      # The runtime permissions Bluetooth LE needs on each Android version
│   │   │   ├── ui/
│   │   │   │   ├── AboutDialog.kt          # App/version info dialog
│   │   │   │   ├── AccessPanel.kt          # Reservation and jog-arming panel of the Control screen
│   │   │   │   ├── AuditDialog.kt           # Audit record of the server (administrators), with the chain check
│   │   │   │   ├── CameraScreen.kt         # Per-robot MJPEG camera feed + vision on/off switch
│   │   │   │   ├── ControlScreen.kt        # Manual jog controls, E-STOP/play/pause/stop with long-press protection
│   │   │   │   ├── DashboardScreen.kt      # 3D carousel robot picker + system health + module matrix
│   │   │   │   ├── GlobalStop.kt           # Global STOP button + result banner (one tap, every robot)
│   │   │   │   ├── Joystick3D.kt           # Reusable 2-axis joystick control component
│   │   │   │   ├── LoginScreen.kt          # Username/password + IP/port entry, biometric login
│   │   │   │   ├── MjpegPlayer.kt          # MJPEG stream parser + Canvas renderer
│   │   │   │   ├── PlaybackConsole.kt      # Shared floating STOP/play/pause/stop console
│   │   │   │   ├── SettingsScreen.kt       # Wi-Fi/Bluetooth scan UI, connection settings
│   │   │   │   ├── SplashScreen.kt         # Custom Compose splash screen
│   │   │   │   ├── TelemetryScreen.kt      # Terminal-style event/sync log viewer
│   │   │   │   ├── ThreeDScreen.kt         # Real 3D viewport - WebView embedding STUDIO's own headless 3D scene
│   │   │   │   ├── UserProfileDialog.kt    # Profile edit + biometric toggle dialog
│   │   │   │   ├── VoiceAssistantDialog.kt # In-app mic/text button to HYDRA-UMC-VOICE-UI
│   │   │   │   ├── theme/
│   │   │   │   │   ├── Color.kt, Theme.kt, Typography.kt   # Material 3 color scheme, theme wrapper, type scale
│   │   │   │   │   └── HydraButton.kt, IndustrialComponents.kt, IndustrialStyle.kt   # Shared industrial-styled UI building blocks
│   │   │   │   └── SecuritySettings.kt                                           # Settings > Security: optional, configurable TLS (mode, pinned fingerprint, CA)
│   │   │   ├── update/
│   │   │   │   ├── GitHubReleaseUpdater.kt   # Safe GitHub Release update client
│   │   │   │   ├── ReleaseMetadataParser.kt  # Safe GitHub Release metadata parser
│   │   │   │   └── SemanticVersion.kt        # Strict semantic version parser for updates
│   │   │   ├── util/
│   │   │   │   ├── BiometricHelper.kt      # androidx.biometric prompt wrapper
│   │   │   │   ├── NotificationHelper.kt   # Job-complete/safety push notifications
│   │   │   │   ├── NotificationPrefs.kt    # Persistent storage for the in-app notifications toggle
│   │   │   │   ├── BleErrorText.kt         # The text of the Bluetooth errors, in the language of the phone
│   │   │   │   ├── JsonText.kt             # Reads a text field the way the UI means it (a JSON null is not the word "null")
│   │   │   │   ├── OptimisticRollback.kt   # Field-level undo for an optimistic edit
│   │   │   │   ├── PlayConfirmation.kt     # When PLAY asks first: the rules of the confirmation before a trajectory starts
│   │   │   │   ├── RenewalBudget.kt        # How often the app may renew the session by itself (a minimum gap and a maximum per window)
│   │   │   │   ├── StaleData.kt            # When the robot data on screen is a remembered copy and not a live reading
│   │   │   │   └── ViewerUrl.kt            # The address of the embedded 3D viewer: the access token goes in the fragment, which is never sent to the server
│   │   │   ├── viewmodel/
│   │   │   │   ├── AppUpdateViewModel.kt   # Lifecycle-aware application update state
│   │   │   │   └── RobotViewModel.kt   # Shared ViewModel - networking, auth, discovery, atomic command dispatch, all UI state
│   │   │   ├── wear/
│   │   │   │   ├── BoundedRequestScope.kt     # Per-requestId in-flight job scope; a superseded attempt is cancelled, cancelAll() on service destroy
│   │   │   │   ├── WatchCompanionProtocol.kt    # Watch companion version-status wire contract
│   │   │   │   ├── WatchVoiceRelayContract.kt   # Authenticated Watch voice relay wire contract
│   │   │   │   └── WatchVoiceRelayService.kt    # Wear OS voice relay service
│   │   │   └── widget/
│   │   │       └── GlobalStopWidget.kt # Home-screen widget for a global E-STOP
│   │   └── res/
│   │       ├── drawable/, layout/, mipmap*/, xml/   # Icons, widget layout, launcher icons, backup/data-extraction rules
│   │       └── values/, values-es/, values-de/, values-fr/, values-it/, values-ja/, values-zh/, values-pt/   # Strings in 8 languages, colors, theme
│   └── src/test/             # JVM unit tests of the pure rules: kinematics, access, contract v2, BLE framing and commands, TLS, the updater and the helpers
├── docs/
│   ├── ARCHITECTURE.md              # Design/architecture notes
│   ├── GITHUB_RELEASE_UPDATES.md    # In-app update check/download/install flow
│   ├── WATCH_VOICE_RELAY.md         # Watch-to-phone-to-server voice relay contract
│   └── BLE_CONTRACT.md              # How the app talks to a HYDRA-UMC device over Bluetooth Low Energy (GATT): service, characteristics, framing
├── images/                       # README banner + splash screen source assets
├── tools/
│   ├── build_test.py             # Build/compile check without bumping version
│   ├── ci_validate.py            # Manifest/CHANGELOG/docs validation used by CI
│   ├── test_publish_script.py    # Checks that publish-github-release.sh never puts the GitHub token on a command line
│   ├── _doc_policy.py            # Documentation rules shared by the family (a copy of HYDRA-UMC-SDK's, kept in sync)
│   ├── _readme_parity.py         # Check that the eight READMEs share one structure (a copy of HYDRA-UMC-SDK's, kept in sync)
│   └── hydra_project_tool.py     # The same commands in every repository: validate, test, ci, version, status, bump
├── dist/                         # Signed release APK output (gitignored)
├── build-android.bat / .sh       # One-shot build + adb install convenience scripts
├── build-test.bat / .sh          # Non-versioning build/compile check
├── prepare-github-release.bat / .sh  # Builds a privately signed, stable release APK without bumping version
├── publish-github-release.ps1 / .sh  # Local-only: publishes dist/'s APK as a GitHub Release
├── bump_manifest_version.py      # Syncs hydra-umc.project.json's version to the native one (--sync)
├── bump_version_code.py          # Android versionCode counter bump (run only by the explicit `bump` command)
├── gradlew, gradlew.bat          # Gradle wrapper
├── build.gradle.kts, settings.gradle.kts, gradle.properties   # Root Gradle project config
├── local.properties              # Local Android SDK path (machine-specific, not committed)
├── keystore.properties.example   # Private release signing configuration template
├── .env.example                  # Example environment variables
├── README.md                     # This file
├── README_spa.md / README_ita.md / README_fra.md / README_deu.md / README_zho.md / README_jpn.md / README_por.md   # Translations
└── LICENSE                       # GPL-3.0-or-later
```
