# Changelog

## [Unreleased]

- **The camera picture is asked for by the camera of the robot:** the stream address used the id of the robot as the id of the camera; the screen now takes the camera whose `assignedRobotId` names the robot (the lowest one if two do) and keeps the robot id when no camera names it, so a server whose cameras are not numbered like its robots shows the right picture. `HydraState.cameraIdForRobot`, 6 tests (`CameraIdForRobotTest`). Not run with a real server and cameras.
- **The view model tests no longer read the real stored login:** every unit test that creates a `RobotViewModel` now hands it an `EmptyAuthStore` that answers at once. The real store goes to the keystore, which a JVM test does not have, and its failure in the background could land in a later test of the same run ("uncaught exceptions before the test started", which showed up in `RobotCommandRepositoryTest`). Tests only.
- **The reservation tests no longer depend on how fast the stored login is read:** the view model reads the stored login in the background when it is created and puts its account name in the login field; a test that had just set the name by hand could find it replaced, which failed the tests of the reservations now and then. The tests now give the view model a stored login with the same name, so the order no longer matters, and a failed unit test prints its whole cause chain in the log of the CI.
- **The emulator checks run on their own:** the start-up check on emulators of Android 8, 11 and 14 and the instrumented tests now run once a week and on a pull request to main that changes the app or its build files (they were started by hand only), with a Gradle cache and a newer run of the same pull request cancelling the older one. They never run on a push.
- **A server in read-only mode is told in the language of the phone:** a command refused with `server-read-only` (423) is no longer shown as a robot in safe mode; the app says that the server is read-only and that only STOP and the safe-mode disable go through, in the eight languages (`error_server_read_only`). `HydraApiClientErrorsTest`.
- **Listen only can be set for the server in use alone (off by default):** next to the general switch, the access panel has a switch for the server the app is connected to (the list of `host:port` is kept on the phone). While it is on, that server gets no command but STOP and the safe-mode disable and no write but the session, and a held jog stops; other servers are not affected, and the general switch still covers all. The texts are in the eight languages. `ListenOnlyServersTest`.
- **The unit tests run on API 34 by default (`robolectric.properties`):** the app targets 37, which the test library does not emulate, so the tests that name no API level follow the same level as those that do.
- **The app targets Android 17 (`targetSdk` 37, the same as `compileSdk`):** the behavior changes of Android 16 and 17 were reviewed against the app. Back is already handled by the system (no `onBackPressed`/`KEYCODE_BACK` code), edge-to-edge is already on, the activity handles `orientation|screenSize|screenLayout` itself so the large-screen rule that ignores fixed orientation changes nothing, and the foreground services and exact alarms of the targets 35 to 37 are not used. The one change that reaches the app is the local network permission: `ACCESS_LOCAL_NETWORK` is declared and asked at start from API 37 (`DiscoveryPermissions`, which also makes the list of permissions testable), because the server of the cell is a device of the local network. Not run on an Android 17 device. `DiscoveryPermissionsTest` (6 tests). The touch filter for overlays (STOP and the other controls) was already on by default with a switch in Settings.
- **The reconnect tests of the WebSocket do not wait for real seconds:** the pause before a dropped socket is opened again is a parameter (`reconnectDelayMs`, three seconds by default), and the tests that wait for the reconnect pass a short one and a generous bound, so a slow runner can no longer fail them; no behaviour changes.

## [0.8.0.4] - Listen-only mode, touches through an overlay ignored, a coverage floor for the unit tests and the Bluetooth session out of the view model

All notable changes to this project are documented here.

- **Tests of the tool changer, the speed slider and the work block through the view model:** 6 new tests against a loopback server (a slot turned into the name of its tool or None, a tool by name, no robot selected, a dragged slider that shows its last value and waits for the pause, a work file refused while no client is connected); no behaviour changes.
- **Tests of the valves, the pumps, the jog step and the vision switch through the view model:** 7 new tests against a loopback server (what goes out, what shows at once, and what is refused with Listen only on, with no robot and with no client); no behaviour changes.
- **README: the platform statement:** a note says that where the README speaks of the CM5 it means the pilot bench that runs today, and that the target platform of HYDRA-UMC, an NVIDIA Jetson Orin NX 16 GB, is a design that has not been built or tested.
- **Listen only dims the controls it disables:** the joystick pad, the base-rotation buttons, HOME/RESET, the valves and pumps and the speed and acceleration sliders are drawn at 40 % opacity while the switch is on, like the DSI (the playback bar with STOP is untouched); the opacity is a pure function (`ListenOnly.controlAlpha`) with a test.
- **Listen only (off by default):** a switch in the access panel makes the app show the robots and their data without sending any command. While it is on, a banner on the Control tab says so and the one place every command goes through (and the held jog) refuses everything except STOP and the safe-mode disable, because refusing a stop would be the unsafe side; a held jog stops when it is switched on. The choice is kept across restarts. Texts in the eight languages; `ListenOnlyTest` has 3 tests. `FakeServerSessionTest` also reads the camera ticket from the stand-in server of the SDK (no ticket without a session, a new one each time). The same switch also refuses, at the door of the network (an interceptor of the shared HTTP client), every other write to the server (settings, push and presence registration, users, files) with a 423 that never leaves the phone, except STOP, the sign-in, the renewal and the end of the session; `ListenOnlyDoorTest` has 3 more tests.

- **The unit tests have a coverage floor of 62 %:** the manual `Android coverage` workflow measured 62.1 % of the lines (5559 of 8946) twice with the same result, `tools/coverage_floor.json` holds that value rounded down, and the workflow now fails when the total falls below it (`tools/kotlin_coverage.py --floor-file`). Tooling only: no behaviour changes.

- **Touches through an overlay are ignored (on by default):** the window drops a touch while another app draws over it, so the STOP and the other controls cannot be pressed through a hidden overlay (clickjacking). A switch in Settings turns it off for an overlay that is harmless (a screen filter) and blocks the buttons; the text is in the eight languages. `ObscuredTouchPolicy` and its tests cover the default, the switch and the flags of a covered window.

- **The command checks, the undo of an optimistic change, the jog arming and the work-file failures leave the view model:** `CommandPreflight` (which robots a command reaches, whether a jog or a table move is above the limit the server holds), `OptimisticEdits` (the change shown at once and exactly what is put back when the send fails, without touching newer deltas), `JogArming` (the end of each arming on the phone clock) and `WorkFileFlow` (an absent folder is an empty list; an ended session is renewed) are plain classes with their own tests, and `sendAtomicCommand` calls them. `WatchRelayHandler` takes the five routes of the watch relay (spoken turn, system status, server security, presence, spoken confirmation) out of `WatchVoiceRelayService` behind a `MessageSender` and a `WatchRelayBackend`, so each route is tested with a recorder in place of the Data Layer; a request replaced or cancelled no longer sends the "connection unavailable" reply that raced its successor. Tests: `CommandPreflightTest`, `JogArmingTest`, `WatchRelayHandlerTest`, `RobotViewModelWorkFilesTest`, `MainThreeDSettingsRenderTest` (the main scaffold, the 3D screen, the settings screen and the activity).

- **The Bluetooth session and the reaction to a refused command leave the view model:** `BleManager` holds the one Bluetooth session of the app (opening one closes the previous one, closing twice is harmless, nothing is kept once it is closed) and `CommandSender` decides what follows a refused robot command (read the robot again, remember that an older server has no `reset` or `trajectory`, end the jog arming, renew the session) from the answer and the command alone. The view model keeps thin calls to both; 13 tests (6 and 7) cover them without a phone. The Bluetooth link itself has still not run against a real device.

- **The line coverage of the unit tests can be measured on demand:** `./gradlew -PhydraUmcCoverage=true testDebugUnitTest createDebugUnitTestCoverageReport` runs the tests with the JaCoCo agent of the Android Gradle plugin (told to include the classes Robolectric loads, or the app shows as never run), and `python tools/kotlin_coverage.py` prints the share of lines run and the files with the most lines no test runs. The manual workflow `android-coverage.yml` does both on a CI runner. No floor is set: a floor comes from a measured value.

- **Screens and dialogs that no test drew are drawn:** `ScreensRenderTest` (8 tests) composes the login screen, the dashboard, the audit and connections dialogs, the link and security settings, the telemetry and the settings screens with a view model that has no server, and checks the words a person needs to use them; the control screen is tested to carry the global STOP once a robot is selected.

- **The view model gives up the sentences it only formatted:** the reasons a refused pairing link, the continuous jog, the work files and HOME/RESET are blocked, and the watch alerts, now live in `PairingText`, `DeadManText` and `BlockText` (the view model keeps thin methods that call them, so no screen changes). 10 new tests cover that every reason has a sentence and that the alerts of a robot name it.

## [0.8.0.3] - The view model tests no longer depend on timing: swapping the main dispatcher of the coroutines library fails when a request of the previous test is still reading it on a worker thread, which made two tests fail now and then on a slow runner. All the tests tha

- **The view model tests no longer depend on timing:** swapping the main dispatcher of the coroutines library fails when a request of the previous test is still reading it on a worker thread, which made two tests fail now and then on a slow runner. All the tests that swap it now go through one helper that tries the change again for a short while, and the tear-down of the tests that start a local server no longer hides the real error when the set-up failed; the helper has its own tests (including a worker thread that keeps reading the dispatcher while it is swapped). The test that checks that a STOP is not held back by a jog on the wire no longer depends on which of the two requests the server sees first.

- **The stored login and the queue of writes are shared through the application of the app:** a new `HydraApplication` holds `SharedServices`, built on first use and then the same object for everyone: the encrypted login store (opening it is a keystore round-trip that took seconds, and the view model and the watch voice relay each opened their own) and the per-robot queue of writes. The view model and the watch relay take both from it when the app runs under `HydraApplication` and fall back to their own when it does not (a test host). No behaviour changes for the person. 5 new tests (nothing is built until asked, one login store for every caller even from eight threads at once, one queue, a custom queue factory).

- **The administration readings leave the view model:** the audit record, who connects and the access options of the server (their states, their busy flags, the reason when they could not be read and the save of the options) are now in `viewmodel/AdminReadings.kt`; `RobotViewModel` keeps the same properties and functions and hands them over (the view model is 140 lines shorter). `RobotViewModelAdminReadingsTest` (14 tests against a local HTTP server: no connection, 403, 404, 5xx, a server that does not answer, the filter that is asked, the export, the options read and saved) passed before the move and after it.
- **Instrumented tests on Android emulators (manual workflow):** `app/src/androidTest` holds six checks that need a real Android system and `android-instrumented.yml` runs them on Android 8, 11 and 14: what the package manager keeps of the manifest on each version (the legacy Bluetooth permissions only up to Android 11, the new ones from Android 12, no permission to switch the Wi-Fi, none to install silently) and that the biometric helper says no, without crashing, on a device with nothing enrolled.
- **A test replays the behaviour shared with the Flutter apps:** `ApiClientVectorsTest` reads the SDK's `conformance/api-client/vectors.json` (copied to the test resources) and checks which commands are numbered and how the number grows, and how every refusal of the server (status, code, revision, message and the kind of refusal) is read by `HydraApiException`.

## [0.8.0.2] - The stored session follows the rules shared with the other mobile apps: SessionFields holds the rules for the access token, the user name and the refresh token (a sign-in replaces the refresh token, a renewal keeps it, signing... (and 2 more changes)

- **The stored session follows the rules shared with the other mobile apps:** `SessionFields` holds the rules for the access token, the user name and the refresh token (a sign-in replaces the refresh token, a renewal keeps it, signing out removes the token and the refresh token) over the same encrypted store and the same keys, so nobody is signed out by the update, and a test replays the sequences all the mobile apps share.

- **The live socket is owned by its own class:** opening the socket, closing the previous one first and ignoring the close, error and expired-token signals of the server being left during a server switch moved out of the view model into `LiveSocket` (with a stand-in socket in the tests), and the callbacks of the view model became one listener; no change in behaviour.

- **A pairing link without a certificate fingerprint is refused:** the link must carry a valid SHA-256 fingerprint of the server certificate; one without it (or with an empty one) is rejected with its own message in the eight languages, as the iOS and DSI apps already do, and accepting a valid link always makes the connection require TLS and pins that certificate. The "pair anyway on a trusted network" warning of the dialog is gone. The tests of the link check every way a missing fingerprint can be spelled.

## [0.8.0.1] - The connect sequence moved out of the screen state: the first synchronisation with a server (identity answer, one renewal of an expired session, the settings, then the live socket) and the "switching server" flag now live in... (and 1 more changes)

- **The connect sequence moved out of the screen state:** the first synchronisation with a server (identity answer, one renewal of an expired session, the settings, then the live socket) and the "switching server" flag now live in `ConnectionManager`, next to the metrics poll. A second connect cancels the first one, so two taps never interleave their writes; a server that cannot be reached ends without a socket. Six JVM tests cover the order, the renewal, the failures and the cancellation; the behaviour is unchanged.

- **The writes of each robot go out in order, and a STOP drops the jogs that wait:** a new queue per robot keeps two writes of the same robot from overtaking each other on the wire, never makes one robot wait for another, and lets a STOP (or switching a robot off) skip the line and cancel the jogs of that robot that have not left yet, so a held direction cannot keep moving it after the stop. A jog that was dropped puts the screen back to what the robot really is; signing out drops everything that waits. Five JVM tests cover it.

## [0.8.0.0] - The text helper and the update gate come from the shared Kotlin module: optStr, ReleaseMetadataParser and the result types of the update check moved to the SDK (clients/kotlinappcommon) and this app compiles the pinned copy;... (and 1 more changes)

- **The text helper and the update gate come from the shared Kotlin module:** `optStr`, `ReleaseMetadataParser` and the result types of the update check moved to the SDK (`clients/kotlin_app_common`) and this app compiles the pinned copy; its own `ControlRelease` only names the asset. Nothing changes for the user: the same releases are offered and refused.

- **A test signs in against the one stand-in server of the SDK:** `FakeServerSessionTest` starts the vendored `hydra_fake_server.py` as a process (skipped without Python) and drives the real client through sign-in, a refused password and the global STOP; `tools/sync_sdk_answers.py` keeps the copy identical to the SDK.

## [0.7.9] - The first Kotlin code shared through the SDK: SemanticVersion (the stable release tag the update check compares) now lives in clients/kotlinappcommon of the SDK with its test; this app compiles a pinned copy... (and 9 more changes)

- **The first Kotlin code shared through the SDK:** `SemanticVersion` (the stable release tag the update check compares) now lives in `clients/kotlin_app_common` of the SDK with its test; this app compiles a pinned copy (`app/src/main/java/com/hydraumc/common`) and `tools/sync_sdk_kotlin.py --check` (in the CI validator) fails when a copy differs from `docs/sdk_kotlin_common.lock.json` or, when the SDK is at hand, from the SDK.

- **Sign-in, stored profile and sign-out moved out of the screen state:** the checks of the sign-in form, the call, the storing of the profile (the password is kept only when "remember me" or the fingerprint sign-in asks for it), the saving of the edited profile and the sign-out (revoking the refresh token, wiping the stored login) are in their own `AuthManager` that takes the stored login through an interface, so seven JVM tests cover them without a keystore.

- **The answers of the server are replayed against the client:** `app/src/test/resources/server-answers.json` is the file the SDK generates from the server inventory (97 routes, a minimal and a full body each), kept identical by `tools/sync_sdk_answers.py --check` in the CI validator; `ServerAnswersTest` replays the routes the app reads (server information, global STOP, ecosystem status, metrics, sign-in and its refusal) through the real client.

- **The build test of the repository compiles only on CI or when allowed:** for the stacks that compile (`rust`, `go`, `android`, `flutter`, `firmware-c`), `tools/build_test.py` prints `BUILD_TEST=SKIPPED` with exit status 2 on a plain PC, never a pass; `ALLOW_LOCAL_BUILD=1` allows it and a CI runner always may. The `python` and `node` stacks are not locked.

- **The metrics poll moved out of the screen state:** the poll of CPU, memory, temperature and uptime is in its own `ConnectionManager` (it runs while connected and in the foreground, a restart cancels the previous loop, a failed read is skipped); four tests cover it.

- **The voice assistant moved out of the screen state:** the voice turn, the reply, the busy flag and the health card for the watch are in their own `VoiceManager`; the screens and the tests read the same properties as before. A workflow now runs the JVM unit tests on every push.

- **The server routes the app calls are fixed and checked:** `tools/check_rest_routes.py` lists the `/api/...` routes found in the sources, compares them with `docs/rest_routes.json` (a route can only be added or dropped on purpose) and, when the shared contracts are next to the repository, fails if a route is not in the server contract. The CI validator runs it.
- **Session renewal moved out of the screen state:** the renewal of an expired session (refresh token first, the saved login as the fallback, a password the server refused once not repeated) now lives in its own class that takes the stored login and the server client as interfaces, so it is tested on the JVM with seven new tests; the behaviour is unchanged.

- **Compile SDK 37 and current libraries:** the project now compiles against API level 37 (the target stays at 35) with Android Gradle Plugin 9.5.0, Kotlin 2.4.21 and Gradle 9.8.2, and the androidx, Compose, Room, OkHttp 5, Retrofit 3, Firebase and test libraries moved to their current releases. The automatic updater no longer skips androidx and OkHttp updates.

- **A script for the bench:** `docs/BENCH_TESTS.md` lists the steps, the setup and the expected result of the tests that need a real phone; every step is marked as not executed.

## [0.7.8] - Continuous jog with a dead-man, a link health indicator, pairing by link, a Work selector and alerts for the watch

- **Continuous jog held by a dead-man:** hold-to-move buttons on the Control screen send `jogStart`, then a `jogHeartbeat` every 100 ms while the button is held, then `jogStop` on release. It is off by default (a switch in the Access panel) and the speed is a slider inside the range of the contract. The axis is stopped, with the stop sent as urgently as a STOP and tried again, when the button is released or slides off, on a STOP, when the screen is left, when the connection drops, after two failed heartbeats, after a hold longer than the limit, when the server refuses a heartbeat and when the link becomes bad; a stop the server did not confirm is told to the person. It is refused with a sentence of its own, in the eight languages, when the robot is playing, in safe mode or offline, while a STOP is pending, when the jog must be armed and is not, and for a role that cannot move a robot. The heartbeat period, the time the server waits, the missed heartbeats, the longest hold and the speed range live in `docs/robot_command_contract.json` (identical in HYDRA-UMC-IOS-CONTROL) and reach the app through the generated constants. The three commands are not in the server yet: everything is tested against a local fake server, and a server that does not know them is told apart and remembered.

- **Link health indicator:** the Control screen shows a dot, a word and the round-trip time of a small request sent every two seconds; the slowest of the last eight answers decides if the link is good, slow, bad or lost. A bad or silent link blocks the continuous jog and stops a running one, behind a second switch that is on by default; the indicator has its own switch. The limits are in the contract file.

- **Pairing by QR code or link:** `hydra-umc://pair?host=...&port=...&fp=...&name=...` (what the admin page of the server is meant to show as a QR code) is read from the phone camera, which opens it with this app, or from a pasted link on the sign-in screen and in the Access panel. It is off by default. The link is checked strictly (address, port, a SHA-256 fingerprint, a bounded name, no repeated field, nothing else kept), the app shows the name, the address and the fingerprint and asks before it keeps anything, and accepting makes the connection require TLS and pins that fingerprint. The admin page that draws the code is not in the server yet, and the app has no camera scanner of its own.

- **Work selector:** the Control screen shows the Work loaded in the selected robot and lists the work files the server keeps in the folder of that robot; picking one checks its points the way the server does (fields, finite numbers, up to 10000 points) and loads it with the `trajectory` command. It never starts the playback and is refused, with a sentence of its own, while the robot is playing, in safe mode or offline, while a STOP is pending, for a role that cannot do it and when the file needs an XY table the robot does not have.

- **Alerts pushed to the watch:** the paired watch gets an alert when the connection to the server is lost (and when it is back), when a robot goes offline or into safe mode and when a STOP was not confirmed (and when it was), in the language of the phone. The same event is not repeated within thirty seconds and the rest are spaced and capped at six a minute, but a critical alert is never held back. A switch in the Access panel turns it off. The status sent to the watch also carries the robots running and whether an alarm is active, for the complication of the watch.

- **Presence and two-step confirmation relayed for the watch:** the presence heartbeat of the watch and the second tap of the confirmation of a spoken order are passed on to the server, each behind its own switch that is off by default; the phone counts the heartbeats it did not receive, never prints the one-use token, answers the watch with a sentence when the relay is off or fails, and passes a confirmation token of a reply through to the watch only when it has the form the watch accepts. The two endpoints are not in the server yet: a server that answers 404 to the presence is remembered.

- **More tests:** the rules of the continuous jog and of the link meter, the runner against a fake server with a virtual clock (rhythm of the heartbeat, every way a session can end, the stop tried again), the controller of the screen, the pairing link (every way it can be refused), the work files, the alert rules and rationing, and the messages of the watch are checked on the JVM.

- **A refused HOME / RESET says so:** when the server will not send the robot home (the pose is not the home pose of its model, or a joint would leave its range) the app says the robot did not move, in the eight languages.

- **Wording aligned with STUDIO:** eight texts that read differently from STUDIO although they mean the same (arm the jog, the reservations, the unreachable server, the ended session and others) now use the words of STUDIO in every language.

## [0.7.7] - HOME/RESET with a two-step confirmation, every robot write goes through one command repository and the command names and their limits come from a contract file

- **HOME/RESET:** a button on the Control screen that takes the selected robot to its zero position through the `reset` command of the server. It is off by default (a switch in the Access panel turns it on and the choice is kept) and it asks twice, in order and within a time limit, before anything is sent. It is refused with a sentence of its own, in the eight languages, when the robot is playing, in safe mode, offline or without a position, while a STOP is being sent or was not confirmed, while a jog is on the wire, when the jog must be armed and is not, and for a session that is neither operator nor administrator (the server decides the role again); a server that does not know `reset` is told apart and remembered. The command carries a rising number like the jog. The default, the number of confirmations and their time limit live in `docs/robot_command_contract.json` (identical in HYDRA-UMC-IOS-CONTROL) and reach the app through the generated constants. Tested against a local fake server: no robot is involved.
- **Every robot write goes through one command repository:** `network/RobotCommandRepository.kt` keeps the revision (If-Match) of each robot, retries a stop when nobody answers, plays a `table` move as jogs on a server that predates it, counts the jogs on the wire, debounces a continuous command under its own name and measures how long each answer took; the view model no longer holds that code, and the global stop moved to `network/StopAllRunner.kt` the texts of the command errors to `viewmodel/CommandErrorText.kt` and the Bluetooth LE scan to `viewmodel/BleScanController.kt`. The first parts of the split of the view model are tested on the JVM against a fake server.
- **The command names and their limits come from a contract file:** `docs/robot_command_contract.json` (identical in HYDRA-UMC-IOS-CONTROL) lists the commands, which ones are numbered or continuous and the limits (speed 10 to 500, jog and jog step up to 1000); `tools/gen_command_contract.py` writes `network/RobotCommandContract.kt` from it and the validation fails when the two differ.
- **More tests:** the repository, the global stop runner and the contract constants are checked on a plain JVM; against a local HTTP server, a stop goes out while a jog is still on the wire and a second jog does not, the number of a stop is above that of the jog before it, a refused disable or enable goes back, and the time of an answered command is kept; on screen, the emergency stop button is shown and one tap starts the global stop.

## [0.7.6] - The phone answers the security status that the watch asks for, read only, and an administrator is told when the server has the remaining-useful-life card closed

- **The phone answers the security status that the watch asks for, read only:** the new path `/hydra-umc/server-security-request/v1` makes the phone read the access options of the server and its history of connections with the session of the signed-in administrator, and answer a `server_security_status` (the options on a value that lowers the protection, how many the environment fixes, the active clients, the failed sign-ins) on `/hydra-umc/server-security/v1`. It writes nothing to the server, and an account that cannot read them, or a server that does not answer, gets the watch the same "offline" card as a failed status. The shared `watch_relay_contract.json` gains the vectors of the message in both repositories.


## [0.7.5] - The app says its version to the server, the access options of the server in the security screen and the history of connections

- **The app says which version it is in `X-Hydra-Client` (`android/<version>`) and offers two more server options:** the server compares only the type, so the version changes no access and goes to the history of connections apart from the type (a version that is not a plain version string is left out). The administrator's section of the security screen now also shows the password of an IP camera handed over through the environment of its process and TLS switched on by a new installation, each with where its value comes from, locked when the server environment fixes it, and asking before the protection is lowered. Texts in the eight languages.
- **Access options of the server in the security screen (administrators only):** below the connection security of the app, the screen shows seven switches of the server read from its configuration: the health route without a sign-in, the announcement of the global stop, keeping the address of each client in the history of connections, the camera pictures without a ticket, a token in the WebSocket address, the blanking of secrets in the log and the signature of a spoken confirmation. Each shows where its value comes from (default, saved from a screen, or fixed by the environment of the server, which locks it); only what changed is sent; and every change that lowers the protection asks first, listing what it opens. A server that does not offer an option does not show it, and an account that is not an administrator does not see the section. Texts in the eight languages.

- **Who connects to the server:** a Connections button in the access panel opens a simple list of the apps the server has seen (kind, user, version, local network or from outside, the sessions open now, the sign-ins that worked or were refused, how long ago) and the latest sign-ins, refused sign-ins, sign-outs and ended sessions, read from the connection routes of the server. An administrator sees every entry and any other account only its own; no password or token is in either list, and an address appears only if the server was told to keep it. It says so plainly when the server is too old to offer them. Texts in the eight languages.

- Build version synchronized with `hydra-umc.project.json` and the repository-native version source.

## [0.7.4] - Safe mode of a robot, the connection state as an enum, a rebuild of only the robot that changed, the update checked against the digest of the release and a quick settings tile for the global stop

- **Safe mode of a robot (disable and enable):** the playback bar of the Control screen has a button that disables the selected robot, after asking; a disabled robot stops and the server refuses every command but STOP until it is enabled again. The same button enables it again, offered only to an administrator (the server answers 403 to anyone else) and also after asking. The app reads the `enabled` flag of the robot and shows a clear message when the server answers 423 `robot-disabled`. The texts are in the eight languages.

- **The connection state is an enum:** the connection indicator is no longer a translated text that the code compares; the screens and the view model use the connection state itself and translate it only to show it. Unused code of the app (the array-merging helper of the state and the settings upload call) was removed.

- **A state change rebuilds only the robot that changed:** a delta, an optimistic command, its rollback and a global stop re-derive the display row of the robots they touch and reuse the others; a full state replace still rebuilds all of them.

- **The update is checked against the digest of the release:** when the release feed publishes a SHA-256 for the APK, the downloaded file must match it before the installer is offered; a release that publishes none is accepted as before. The HTTP client library moves to 4.12.
- **A quick settings tile for the global stop:** the tile opens the app and asks it for the same global stop as the widget and the notification; only the system can bind to it.

## [0.7.3] - Numbered motion commands, a shared Watch relay contract, a cancelled installer no longer leaves the update screen on installing, Android lint in the CI and the universal README

- **Motion commands are numbered:** a jog, a table move, a joint move and a stop carry a rising number, so the server can refuse a jog that arrives after a later stop; a refusal for arriving out of order is told apart from a reservation and shown in the eight languages.

- **The Watch relay contract is a shared file:** `docs/watch_relay_contract.json` (identical in the Watch repository) lists valid and invalid messages and the limits, and a test runs them against the relay messages of this app.

- **README in the universal layout:** the README and its seven translations follow the layout that the four families share (22 sections in a fixed order), with an architecture picture and a repository-structure picture drawn from `docs/architecture.json` and `docs/structure.json`; the CI validator checks both pictures and the file tree.

- **The layout of the README is checked in the CI:** the validator runs `tools/universal_readme_check.py` and lists, as a warning, what differs from the universal layout of the README.

- **A cancelled system installer no longer leaves the update screen on "installing":** when the app comes back to the foreground after the Android installer was closed without replacing the app, the update is offered again.

- **Android lint runs in the CI** next to the unit tests.

## [0.7.2] - A quiet start-up and a new sign-in, like the other apps of the family and bluetooth commands with an acknowledgement (cl

### Firmware and protocol

- **PLAY asks first.** Before a trajectory starts the app shows the robot, its recorded points, the speed and who has it reserved (a robot reserved by someone else says the server will refuse the command). It is on by default, can be turned off under the reservations, and the choice is kept. A robot that is already playing is not asked again.
- **Audit record for administrators.** An Audit record button under the reservations opens the lines the server wrote down about the changes that matter, newest first, with a filter by type, robot and number of lines. Verifying checks the chain of SHA-256 hashes twice: the server over the whole record and the phone over the lines it was given (a changed line, a replaced line whose successor still names the old one; a filtered window only shows gaps). The lines can be shared as stored (`.jsonl`). Says why when it can not read (not an administrator, older server, expired session, no network, record switched off or locked off).
- **Bluetooth LE (GATT) for real.** The Bluetooth client is no longer a placeholder: it follows a documented contract (`docs/BLE_CONTRACT.md`: one HYDRA service with a notified state characteristic and a written command characteristic, messages cut into frames that fit the MTU). It scans filtered by the HYDRA service (a switch lists every device), negotiates the MTU, checks the service and the characteristics, turns on notifications and reports **connected** only then, reassembles the state, writes commands one frame at a time, and reconnects a dropped link after 1, 2, 4, 8 and 16 seconds before giving up. A device without the HYDRA service, a missing characteristic, refused notifications, a refused write, a damaged message and a message that is too large are each reported on their own.
- **Not verified on a device:** no HYDRA-UMC device implements the peripheral side yet. The client is tested against a simulated GATT only, the Android implementation, the scan and the permission prompts have never run on a phone, and robot commands are not sent over Bluetooth yet (the client can write them; the control screens still use the Wi-Fi session).

### Programs and windows

- **The sentence the watch relay falls back to is in the language of the phone.** The text sent to a watch that cannot resolve an error code (connection unavailable, status offline) comes from the string resources, in the eight languages, instead of English literals in the service. 57 strings that no screen uses were removed from the eight languages. Three tests keep it so: no `Text("literal")` in the screens, no unused string, the same keys in every language.
- **A quiet start-up and a new sign-in, like the other apps of the family.** The start-up picture is gone: the mark, the name and one line appear on a dark backdrop for a second and a half and the app opens. The sign-in has the same look: the mark and the name on top and the server, user and password in a rounded panel with their icons, a button to show the password and a large sign-in button. The line under the name is in the eight languages. Two lint errors found on the way are fixed (the location check works from Android 7 and the message of an unconfirmed STOP no longer receives an argument it does not use).
- **Bluetooth commands with an acknowledgement (client side).** The BLE client turns on the new ACK characteristic of the peripheral and `HydraBleClient.sendCommand` writes a command (`id`, `robotId`, `command`, `params`, `controllerId`, access token) and reports `Acknowledged` (with the status and code the server gave), `NoAnswer` (nothing within 4 seconds, or the link dropped: the command is treated as not delivered) or `NotAvailable`/`TooLarge` when nothing was sent. A device without ACK keeps showing its state and takes no commands. 16 new tests against the simulated GATT; no screen sends commands over Bluetooth yet and none of this has been tried on a phone.
- **Fixed texts are in the eight languages.** The names of PLAY, PAUSE/RESUME and STOP for screen readers, the status label, the robot picker, the valve and vacuum buttons, the 3D view title and model, the refresh button, the camera list entry of a disabled camera and the text of the safety notification were fixed English; they are now resources.
- **Conditional writes.** Every reservation, release and robot command carries the revision of the robot this app last saw (`If-Match`, kept per robot from the answers of the server and from `GET /api/robot/:id`). When someone else changed the robot in between, the server answers 412: nothing is applied, the change on screen is undone, the robot is read again so the screen shows what it is now, and the message says so in the eight languages. STOP is never conditional, a write is not conditional while another write to the same robot is on its way, and a take-over is never conditional. A choice in the list of reservations (kept on the phone) selects Standard (reservations, the table and the one-off commands; the default), Strict (everything but STOP) or Off.
- **The server pushes the reservations** (WebSocket message `claims`): the list and the arming of the jog follow at once and the 15 second poll of the Control screen rests while a push is recent.
- **Take control (administrators only).** On a robot someone else holds, an administrator's session shows a **Take control** button that asks for confirmation and then reserves the robot with `force: true`. The role comes from the session token (the server checks it again); an operator never sees the button and the app sends nothing if it is asked for anyway. Texts in the eight languages and five new unit tests.
- **Warning without TLS.** A session that is not encrypted to a host outside the local network (not private, loopback, link-local, `.local`, `.lan` or a single-label name) shows a red banner above every screen.
- **Reservations.** The Control screen shows who holds the selected robot (nobody, this phone, your account on another device, another account), reserves and releases it, and lists every reservation of the server. Reservations stay optional.
- **Controller id.** The app keeps one controller id for the installation and sends it with every reserve, release, jog-arm and command (body and `X-Controller-Id` header), so a reservation belongs to the account and to this phone. The refusals of the server read differently: another account, your account on another device or window, controller id required, and jog not armed.
- A banner above every screen warns when the robots shown come from the saved copy or the link to the server is down, with the time of that data; the time of the last save is now stored with the copy.

### Tests, builds and checks

- Tests for the three rules (`ContractV2Test`), the claims message (`HydraWebSocketClaimsTest`) and the behaviour against a local server (`RobotViewModelConditionalTest`: the header, the 412, the table, the older server, the audit record). The README counters follow (280 tests).
- **Release script.** `publish-github-release.sh` hands the GitHub token to curl through a private configuration file instead of the command line (visible to other users in `ps`), uses a private `mktemp` directory instead of fixed `/tmp` files, and ignores a Windows line ending in `.env`. A new test runs the script against a fake curl and checks that the token is never an argument.
- Tests: the viewer address (token in the fragment, encoded, absent when empty) and the plain-traffic guard (public address and name refused without connecting, local server still works, the switch lifts the rule).
- Tests: the framing and reassembly in both directions, the whole BLE client against a simulated GATT, the permission lists and the device list, the error texts in English, Spanish and Portuguese, and real TLS handshakes (pinned fingerprint, wrong fingerprint, custom CA with host name check, the three modes, no plain downgrade) against a local server with a certificate made by `keytool` when the tests start.
- The eight languages carry the new texts; 17 new unit tests cover the controller id, the reservations, the limits, the jog check and the refusals.
- Building no longer changes the version; raising it is the explicit `bump` command (`python tools/hydra_project_tool.py bump`). The build scripts only report the version they built, and CI fails if a build script runs a bump.
- Added `tools/hydra_project_tool.py`: the same `validate`, `test`, `version`, `status` and `bump` commands in every repository. The checks run with `HYDRA_UMC_CI=1` and fail if the version changes; only `bump` changes it.
- The README states how the release build is signed (private key when configured, debug key otherwise), the manifest and README describe the network setup as it is, and the architecture notes describe how incoming state is applied; the leftover store listing file and the unused project template tests are gone.

### Documentation

- The Bluetooth contract (`docs/BLE_CONTRACT.md`) now says that the peripheral side exists in HYDRA-UMC-SERVER, defines the ACK characteristic and the command fields (`id`, `token`) and keeps saying that this app does not send commands over Bluetooth yet.
- **No plain traffic outside the local network.** The app refuses unencrypted HTTP/WS to a server that is not on the local network (REST, live connection and camera) unless **Settings → Security → Allow plain traffic outside the local network** is on. It is off by default; the switch is in the eight languages. Android's network security configuration cannot name address ranges, so the rule is an interceptor on the shared client.
- **Bluetooth permissions and errors.** Android 12+ asks for scan and connect, Android 11 and older for location (and Location Services on); a permission revoked from Settings while scanning or connecting is reported instead of crashing, a failed scan is reported, scan results refresh their signal and keep a known name, and the legacy Bluetooth permissions are declared only up to Android 11. Every Bluetooth message, including the ones that were hard-coded in English, is in the eight languages.
- Texts for the new messages in the eight languages.

### Other changes

- **The XY table jog is one atomic `table` command**, relative to where the table is (checked as a whole against the limits of the model before it is sent and counted as a jog for the arming of the jog); a server that predates the command is detected and gets the jogs that did the same before.
- **Token out of the 3D view address.** The embedded 3D view now receives the session token in the address fragment (`#token=`), which is never sent to a server, instead of `?token=`: no server, proxy or access log records it any more. STUDIO already reads it from there.
- **Automatic backup off.** `allowBackup` is `false`: the saved session, the offline copy and the settings no longer leave the phone through a backup.
- **Optional, configurable TLS.** **Settings → Security** chooses Automatic (the default, exactly what the app did before), Require TLS (HTTPS and WSS only, no plain fallback) or Plain only (HTTPS never tried), and accepts a pinned SHA-256 fingerprint of the server certificate (works for self-signed servers) or a custom CA. Invalid input is never saved. The choice applies to the REST client, the live connection, the camera stream and the Wear voice relay, and to the next connection after saving.
- The embedded 3D view does not follow the new TLS rules yet; against a self-signed server it still needs the certificate installed on the phone.
- **Safety limits.** The limits of the robot's model are read from the server: a jog step above the limit is not sent (it says which limit), and when the administrator requires it the jog waits for an explicit **Arm jog** (with a countdown) that ends by itself, with STOP, or on release.
- The banner follows the animated style of the ecosystem banners, with the name, the kind and the technologies of the project.
- Comments no longer point at a private design note.
- A failed command now undoes only the fields it changed, and only while they still hold the value it wrote: a server update or a later jog that succeeded is no longer overwritten by an old copy of the robot.
- A Cartesian jog whose target is out of the arm's reach, or needs a joint outside its limits, is refused with a message instead of sending clamped joints next to a position that does not match them.
- The companion version status accepts the four component version numbers.

## [0.7.1]

- Fixed the sign-in loop against a server that predates the `hydra.bearer` subprotocol: such a server accepts the live connection, finds no token in the address and closes it with 1008 "No token provided"; the app took that for an expired session, renewed it, reconnected the same way and ended back at the sign-in. That close now makes the app send the token in the address once, right away, with no renewal; a server that rejects the token both ways is reported as a rejected session once.
- Tests for both servers: the old one that accepts and then closes, and one that rejects the token both ways.

## [0.7.0]

- Maturity is functional: nothing here has been verified on real hardware yet.
- The READMEs say, in the eight languages, what is planned and what has only been tested against fakes, emulators or fixtures, and carry a short safety and security status note.

## [0.6.9] - STOP on a server without the route, and network failures while reading an answer

- **Global STOP:** a server that does not have `POST /api/stop-all` (HTTP 404) now gets the stop of every known robot one by one, like a rate limited one (429) already did and like the tablet and iPhone apps do, instead of leaving the STOP "not confirmed".
- **Answers cut short:** a connection that timed out or broke while the body of an answer was being read escaped as a raw socket exception instead of the app's own API error, so any caller that only expects the API error was missed. It is now reported as a network failure.
- **Error codes:** an error body with `"code": null` produced the code "null"; a JSON null is now no code.
- Tests: the 404 fallback, the exact meaning of every status flag, the global STOP answer read field by field with fields of the wrong type, the client against answers that are not JSON objects, what is sent (token only in the header, app identity) and the renewal budget at its limits and under concurrency.

## [0.6.8]

- Fixed an endless sign-out / sign-in loop: when the server closed the connection with 1008 saying that a token in the address is not accepted, the app took it for an expired session, signed in again, reconnected the same way and repeated until the server's login limit stopped it. That close now only makes the app reconnect the safer way (no renewal, no new sign-in).
- The fallback to the token in the address is only tried when the server really answered and refused the upgrade, not after a network error.
- The session is renewed by itself at most three times in ten minutes and never twice within twenty seconds; past that the app goes back to the sign-in with a clear message instead of looping.
- A saved password that the server refused once is not tried again until the password is typed by hand.
- Tests for the three cases and for the renewal budget.

- Build version synchronized with `hydra-umc.project.json` and the repository-native version source.

## [0.6.7]

- Build version synchronized with `hydra-umc.project.json` and the repository-native version source.
- The licence is written as GPL-3.0-or-later in every header and in the package metadata.

## [0.6.6] - Global STOP

- **One-tap global STOP:** a red **STOP** button (caption "Stop all", translated in the 8 languages) sits in the top bar of every screen, so it is reachable from the dashboard, the control screen, the camera, the 3D view and the settings. The pulsing round STOP of the control console and of the 3D view (portrait and fullscreen, now shown even with no robot selected) is the same control. No long press and no confirmation: a tap sends `POST /api/stop-all` at once, without waiting for the robot list or a refresh, and stops every robot, including those reserved by another account.
- **The result is shown:** a banner lists how many robots stopped, which did not (with the reason), which are reserved by someone else, and warns when the server could not save the stopped state. A clean result hides itself after a few seconds; anything else stays until dismissed.
- **STOP NOT CONFIRMED:** a tap that nobody answers is sent again once (2 s timeouts); when it still fails, the red banner stays on screen, and cannot be dismissed, until a later STOP succeeds. An expired session is renewed and the stop repeated; a 429 from the server falls back to the per-robot `stop` of every known robot.
- **Widget and notification:** the home-screen widget and the notification action call the same global STOP as soon as the server connection exists (no wait for the robot list), and a stop that cannot reach the server shows STOP NOT CONFIRMED. Their labels are now translated.
- **Per-robot Stop:** the Stop button of the selected robot keeps its long-press protection; the old long-press E-STOP is replaced by the global one.
- **Texts:** 12 new strings in the 8 languages; the hold-to-confirm hint of the old E-STOP and the "no known robot" message are gone.
- **Tests:** `RobotViewModelGlobalStopTest` covers the call (path, method, bearer header, no body), a clean result, a partial one, the retry, the persistent not-confirmed state and its clearing, the 429 fallback and the no-connection case, against a local test server.

## [0.6.5] - Safer jogging and stopping, session renewal, clear server refusals

- **Jog follows the screen:** a held jog button kept calling the lambda of the moment it was pressed, so changing the step or switching to the XY table while holding left the robot moving by the old step or on the old target. The repeat now always calls the current one (`JoystickHoldTest`).
- **No more queued jogs:** while one jog is on the wire the next tick of a held button is dropped instead of piling up behind a slow link and arriving after release; nothing jogs while a trajectory is running (the Control screen and the fullscreen 3D pad are disabled too).
- **E-STOP on the 3D view:** the pulsing E-STOP button is also on the 3D viewport, portrait and fullscreen; its hint and label are translated in the 8 languages.
- **STOP that gets through:** a stop is sent with 2 s timeouts and, when nobody answers, again up to 3 times; if it still fails the message says the STOP was not confirmed and the change is reverted.
- **Global stop from the widget and the notification:** the tap reaches the running app (`launchMode="singleTop"`) instead of rebuilding it and dropping the live connection, waits for the roster that came from the server (not the offline copy) and stops every robot by id without changing the selected robot.
- **Clear refusals:** a robot reserved by another account (409), a command the server does not know (400), a missing permission (403) and an expired session each have their own message in the 8 languages; a 200 answer with warnings counts as not applied and is reverted. The acceleration slider now covers 10-500 %, the range the server applies.
- **No "Disable node" button:** the server has no such command (it answered 400, and before that a false success), so the button and the `enable`/`disable` commands are gone.
- **Session renewal:** an expired access token is renewed with the refresh token (or the saved login) once, however many requests and socket events notice it, instead of signing the user out; a failed renewal ends the session for real: logout closes the socket and the polling, revokes the refresh token and no longer leaves the password behind unless "remember me" or the fingerprint sign-in needs it. Saving the profile no longer erases the session token.
- **Token out of the address:** the WebSocket sends the token as the `hydra.bearer` subprotocol (an older server that refuses it gets `?token=` automatically); the camera stream sends the bearer header and reconnects with a growing back-off instead of freezing on the last picture.
- **Offline copy:** the cached state no longer keeps camera usernames and passwords, is serialized on the main thread, and is excluded from cloud backup and device transfer.
- **Connection:** the WebSocket is opened only after the REST sync worked and only once per connect, the system health monitor starts when the socket connects, the settings screen no longer opens on the update tab after a failed update check, a scan with no IPv4 interface stops the network discovery it started, and a failed discovery start no longer calls `stopServiceDiscovery` on a listener that is already gone.
- **Local state:** a relative XY-table jog no longer writes `pos.tx`/`pos.ty` (the server does not), the voice field stops at the 500 characters the server accepts, and `HydraApiException` carries the HTTP status, the server's `code` and its message.
- 23 new tests: the held joystick, the WebSocket subprotocol and its fallback, the typed refusals, the stop retry and the cache redaction.

## [0.6.4] - JSON null fields no longer read as the text null

- **A JSON `null` is no longer the word "null":** `JSONObject.optString` answers the four letters `null` for a field that is present but JSON `null`. Robot names, models, roles and tools, server names and ids, job names, project fields of the ecosystem screen, the release name and notes of the update check, the WebSocket `type`/`error` and the controller id of incoming messages all went through it. A release published without notes showed `null` as its notes, a robot without a name was called `null`, and a login answering `"token": null` stored the token `null`. A new helper (`util/JsonText.kt`, `optStr`) treats a JSON null like a missing key and gives the fallback; all 32 call sites use it.
- Four new Robolectric tests (`JsonTextTest`): the platform behaviour the helper avoids, missing keys and real values unchanged, a robot full of nulls showing its defaults, and release notes that are null coming out empty. `versionCode` 59.
- **Portuguese:** `values-pt/strings.xml` (193 strings, the same keys and format specifiers as English), so the app now ships in 8 languages, and `LocalizedStringsTest` fails when any language misses a key or changes a format specifier. A README_por.md joins the other seven. The unit suite is 67 tests.

## [0.6.3] - Could no longer reach a server with TLS turned on

- **Real bug, found live against the real CM5 test bench the same day HYDRA-UMC-SERVER's own optional `TLS_CERT_PATH`/`TLS_KEY_PATH` (server.ts) was turned on there:** `HydraApiClient`, `HydraWebSocket`, the camera stream URL and the embedded 3D view URL all had `http://`/`ws://` hardcoded, with no way to reach a server that switched to HTTPS/WSS-only. `HydraApiClient.getHydraInfo()` now probes the current scheme first and, only if that gets no answer at all, retries once over HTTPS - remembered for every later call through that same client (`usesTls`), and threaded into the WebSocket, the camera stream and the 3D view URLs it also opens. A plain-HTTP server (today's default, and every server that hasn't opted in) behaves exactly as before - the extra probe only ever happens once HTTP has already failed.

## [0.6.2] - Removed the disconnected experimental Filament 3D screen

`ui/NativeThreeDScreen.kt` was an unfinished Google Filament native 3D
viewer, declared but never wired into any navigation route and without a
real `.glb` asset pipeline - genuinely dead code, confirmed unreferenced
anywhere else in the app module before removal. The real, currently-shipping
3D View (`ui/ThreeDScreen.kt`) embeds HYDRA-UMC STUDIO's own web viewport in
a WebView and is unaffected. The now-unused Filament Gradle dependencies
(`filament-android`/`gltfio`/`filamat`/`utils`, declared solely for the
removed screen) were removed from `app/build.gradle.kts` and
`gradle/libs.versions.toml` alongside it.

## [0.6.1] - The real cause: a long server-selector label pushed the icon row off-screen

0.6.0's own 28dp size fix did not actually fix the live report - the
owner's own follow-up correctly traced it to the server-selector label's
real width, not the About icon's own size. `MainScreen.kt`'s Sub-header
Row had no width limit on the server-selector `Box` at all: a wide enough
translated label (Spanish `Servidores (N)` is measurably wider than
English `Servers (N)`) grew past what's left on screen, pushing the
trailing icon row (Profile/Voice/Telemetry/Settings/About) partly past
the right edge - clipping mostly the LAST one (About), which reads
exactly like "the About icon is tiny", never actually a per-icon sizing
bug at all.

Fixed with `Modifier.weight(1f, fill = false)` on the server-selector
`Box` (caps it to its fair share of the row instead of an unbounded
natural width) and on its own `Text` (`maxLines = 1` +
`TextOverflow.Ellipsis`, so a long label truncates instead of pushing
anything) - the icon row keeps its own full natural width and stays
fully on-screen regardless of label length.

New `SubHeaderRowLayoutTest.kt` (Robolectric + Compose UI testing, a real
Pixel8-width device): confirmed this actually reproduces without the fix
(2 of 3 real assertions fail against the un-weighted structure) and
passes with it, for a realistic long label, a deliberately extreme one,
and confirms the About icon keeps its real full 28dp size when there's
plenty of room.

## [0.6.0] - The About icon looked visibly smaller than its 4 neighbors

Live report: the toolbar's Profile/Voice/Telemetry/Settings/About icon
row (`MainScreen.kt`) - all 5 use the same default `IconButton`/`Icon`
size with no explicit modifier - still read as noticeably uneven, the
About (`Icons.Default.Info`) icon looking smaller than the other 4. Not
a sizing bug in this code: Material's own "Info" glyph draws with more
internal padding inside its 24x24dp viewBox than Person/Mic/Terminal/
Settings do, so the same nominal size reads smaller. Given an explicit
`Modifier.size(28.dp)` so it visually matches its neighbors; `IconButton`'s
own 48dp minimum touch target is unaffected either way.

## [0.5.9] - Watch voice-relay error text no longer stuck in English

- `WatchVoiceRelayService`'s own connection-timeout/no-session fallback
  replies (`WatchAssistantReply`/`WatchSystemStatus`) always carried
  hardcoded English text with `speak=true` for the assistant reply -
  spoken by the paired watch's own TTS engine regardless of its
  configured language, since this phone has no access to that watch's
  own locale when it generates this SYSTEM fallback text itself (as
  opposed to a real AI reply, whose own text already comes back
  correctly localized from Voice UI). Added an optional, stable, never-
  translated `errorCode` field (`"connection_unavailable"`/`"offline"`)
  the watch itself now resolves to a real localized string from its own
  strings.xml, instead of ever speaking/showing this phone's own English
  text. The English text stays as an honest fallback for an older watch
  build that doesn't recognize the code yet.
- Add real regression coverage in `WatchCompanionProtocolTest`: a reply
  with no `errorCode` still omits the field from the wire JSON
  (unchanged, backward-compatible shape), and a real `errorCode` round-
  trips exactly for both `WatchAssistantReply` and `WatchSystemStatus`.

## [0.5.8] - WebSocket send() no longer reports success against a dead socket, and 9 literal UI strings now honor the selected language

- **`HydraWebSocket.send()` could report success right after a real close/
  failure.** `webSocket` stayed non-null - pointing at the dead socket -
  for the entire reconnect window after `onClosed`/`onFailure`, since
  nothing nulled it out except a user-initiated `disconnect()`. `send()`'s
  own echo-guard (a payload matching the last one sent/received) returned
  `true` without ever checking real connectivity. Fixed with a real
  `isSocketOpen` flag, set only between a genuine `onOpen` and the next
  `onClosing`/`onClosed`/`onFailure`/`disconnect()` for that *same* socket
  instance - every listener callback now also checks `ws === webSocket`
  before touching shared state, so a stale callback from a superseded
  connection attempt can no longer flip a newer, still-open connection
  back to disconnected. 1 new test (real `MockWebServer` close, not a
  mocked `WebSocket.send`).
- **9 literal, unlocalized strings replaced with real string resources**
  across the login/main screen, the camera feed screen, biometric prompts
  and the Bluetooth settings screen - all 9 previously showed the same
  hardcoded English or Spanish text no matter which of the 7 supported
  languages was selected (`MainScreen.kt`'s server selector and 3 icon
  content descriptions, `CameraScreen.kt`'s feed selector label and
  enabled/disabled/live indicators, `BiometricHelper.kt`'s cancel button
  and failure message, `SettingsScreen.kt`'s Bluetooth-disabled notice).
  New/reused string resources added to all 7 language catalogs.
- Corrected two stale claims about server credentials and localization
  scope: the login screen's own description said "5 languages" (this app
  has shipped 7 for a long time) and said every server seeds a default
  `admin`/`admin` account - true only for a *development* server; a real
  production server (`NODE_ENV=production`) requires
  `HYDRA_UMC_BOOTSTRAP_ADMIN_USERNAME`/`HYDRA_UMC_BOOTSTRAP_ADMIN_PASSWORD`
  instead and never falls back to `admin`/`admin` silently.

52 tests passing (`./gradlew test`), 1 new.

## [0.5.7] - Real coverage for GitHubReleaseUpdater.download()

The self-update download path had zero tests of its own -
`ReleaseMetadataParserTest` only covers the pure metadata gate that runs
*before* any bytes are fetched. The "rollback after a failed install
attempt" path had no coverage of its own;
`download()`'s real job there is to make sure a rejected download leaves
no half-written or stale
APK that `cachedInstallableApk()` could later hand to Android's
installer. New `GitHubReleaseUpdaterTest` (Robolectric + a real
`MockWebServer` over loopback, `ShadowPackageManager` standing in for the
APK-manifest parse and the installed-version lookup): a valid newer APK
finalises to a ready-to-install file; a mid-stream connection drop, an
APK whose package name isn't this app's, an APK not newer than what's
installed, and a file that doesn't parse as a package are each rejected
*and* leave nothing behind - `cachedInstallableApk()` returns null in
every failure case, and returns null again once the installed app catches
up to a previously cached APK. 7 tests.

## [0.5.6] - Real coverage for cancelling an in-flight robot order

Another gap alongside :
`sendCommand("stop")` only ever had real test coverage starting from an
idle robot (every existing
`RobotViewModelSendAtomicCommandTest` scenario begins with
`isPlaying=false`) - no test exercised the real "cancel an order actually
in flight" case. 3 new tests, same real
`MockWebServer` convention as the rest of this file: cancelling an
in-flight order optimistically stops both the target robot and its
`combinedWith` sibling before the network round-trip completes, the real
POST carries `command=stop`, and a failed cancel rolls both robots back
to still-playing (the order never actually reached the robot, so the UI
must not show it as cancelled).

## [0.5.5] - silent recovery from a WS 1008 close using the stored remember-me password

A WS 1008 close (`server.ts`'s own token-expiry/revocation close, most
commonly the access token's real 30-day time-based expiry) forced this
app straight back to LoginScreen, even on a still-genuinely-logged-in
account. `RobotViewModel` now tries a silent re-login first
(`attemptSilentRelogin()`), reusing the same username/password `AuthPrefs`
already encrypts at rest for "remember me" - no new server-side mechanism
needed the way HYDRA-UMC-STUDIO's browser-only JWT model required (its
own refresh-token support, HYDRA-UMC-SERVER 0.6.2): this app already had
the real credential on hand. Falls through to today's existing forced
logout exactly when it should - no remembered password, a genuinely
revoked account (wrong password now, deleted), or the recovery attempt
itself failing for any reason (the whole function runs inside one
try/catch specifically so a failure here can never leave the app in a
silently dead, unrecoverable state instead of a visible logout).

Honesty note: the happy path (a remembered password actually recovering
the session) has no automated test - `AuthPrefs`' `EncryptedSharedPreferences`
needs a real `AndroidKeyStore`, unavailable under Robolectric's JVM, the
same real constraint every other test in this app's `viewmodel` package
already works around by never exercising `login()`/`AuthPrefs` at all.
What the fail-safe (a recovery failure still producing a real, visible
logout, never a hang) does get real coverage for.

## [0.5.4] - Hardware-accelerated MJPEG frame decode

`MjpegPlayer`'s own stream decode used `BitmapFactory.decodeByteArray()`
for every frame - always a CPU-side ARGB_8888 decode, needing a CPU-to-GPU
pixel upload on every single draw call. On API 28+, `MjpegStreamParser`
now decodes each frame through `ImageDecoder` requesting
`Bitmap.Config.HARDWARE` first - the real, documented Android platform
mechanism for hardware-accelerated bitmap decode (the same one production
image-loading libraries use for this exact reason), skipping that
per-frame upload. Falls back to the pre-existing `BitmapFactory` decode on
API <28, on any `ImageDecoder` failure for a specific frame, and - a real
safety net for a case this Windows-only dev environment cannot itself
verify against physical hardware - if a HARDWARE bitmap ever fails to
draw on a given device/window, self-heals to software decode for the
rest of that stream instead of crashing. New `MjpegStreamParserTest`
covers both branches plus the pre-existing corrupt-frame/stream-ended
contract; not verified against real Android hardware in this dev
environment (Windows-only, no physical device) - documented honestly
rather than left unimplemented.

## [0.5.3] - In-app voice/text assistant button

New mic icon in the top bar opens VoiceAssistantDialog: type a question/
order, or tap the mic for this phone's own on-device speech recognition
(`RecognizerIntent`, the same real pattern HYDRA-UMC-WATCH's voice button
already used) - never uploads audio, only the recognized transcript.
Sends it to HYDRA-UMC-VOICE-UI through the same authenticated
`POST /api/voice/turn` Server route the paired-Watch relay
(`WatchVoiceRelayService`) already used, but as this phone's own ordinary
traffic (`HydraApiClient.postVoiceTurn()`, no `X-Hydra-Client: watch`
header) - so disabling Watch access in Config never also disables this
button, and vice versa (see `postVoiceTurn()`'s own comment and
`server.ts`'s `remoteAccessAllowed()`).

`RobotViewModel.relayWatchVoiceTurn()`/`latestWatchVoiceReply` - real,
tested plumbing added ahead of the Watch relay actually landing, then
left unused once `WatchVoiceRelayService` shipped its own independent
path - repurposed as `sendVoiceTurn()`/`latestVoiceAssistantReply` for
this button instead of leaving two near-identical, dead-and-live copies.
A reply only ever carries text/status; it never invokes a robot command
directly.

New `RECORD_AUDIO` runtime permission requested only when the mic button
is first used (same flow as Bluetooth/location earlier in this app).
5 new tests (`RobotViewModelSendVoiceTurnTest`, real `MockWebServer`, same
convention as `RobotViewModelSendAtomicCommandTest`) cover the real POST,
the client-identity header, and the busy/error/success state transitions.

## [0.5.2] - Declares a real Wear OS companion capability

A real gap located in the paired HYDRA-UMC-WATCH repo:
HYDRA-UMC-WATCH's own
relay transport used to pick `connectedNodes.firstOrNull()` - any
connected node at all, with no proof it was actually running this app.
New `res/values/wear.xml` declares the real, Google-documented
`android_wear_capabilities` resource (`hydra_umc_phone_companion`) this
app already qualifies for (`WatchVoiceRelayService`'s own
`WearableListenerService` registration already makes it reachable for
messages - this capability is what makes that reachability
*discoverable* from the Watch side via `CapabilityClient`, instead of
assumed from "any connected node"). No code changed on this side; the
Watch now queries this capability instead of trusting connection order.

Verified with `./gradlew assembleDebug` (this repo's own real build
command) - the debug APK builds with the new resource in place.

## [0.5.1] - Wear relay service now cancels its own coroutines on destroy

- 
  `WatchVoiceRelayService`'s own `CoroutineScope(SupervisorJob() + Dispatchers.IO)`
  was never cancelled in `onDestroy()`, had no concurrency bound per
  requestId, and no timeout on the network round trip - a real service
  recreation left every already-launched coroutine running regardless,
  and a flood of Data Layer messages (or a retried/duplicate delivery
  for the same requestId) could pile up unboundedly. New
  `BoundedRequestScope.kt` - a plain, non-Android class that keys
  in-flight jobs by requestId (a new launch for one already in flight
  cancels the previous attempt first) and exposes `cancelAll()` - now
  wired into `onDestroy()`, plus a real 15s timeout per request and a
  logged (not silently dropped) `sendMessage()` failure. 4 new tests in
  `BoundedRequestScopeTest.kt` using `kotlinx-coroutines-test` directly
  (no mocking library needed - this class has no Android/GMS dependency
  at all).
- **New `RobotViewModelSendAtomicCommandTest.kt`** (5 tests) -
  `RobotViewModel.sendAtomicCommand()`
  (the optimistic-mutate-then-rollback-on-failure flow and its
  `combinedWith` fan-out) had no test at all, unlike other parts of this
  app (kinematics, state parsing, updates) that already do - the same
  gap HYDRA-UMC-DSI's own `robot_view_model_test.dart` already closed
  for its own Flutter port of this exact logic. Real coverage against a
  real local `MockWebServer` (new `mockwebserver`/`kotlinx-coroutines-test`
  test dependencies), never a mocked `HydraApiClient` - exercised via
  `sendCommand("play")`/`jogJ1()`, real commands real screens already
  wire to buttons. Covers: the real optimistic mutation applied to both
  the target robot and its `combinedWith` sibling before the network
  round-trip even starts, the real POST reaching
  `/api/robot/:id/command` with the right body, a successful send never
  rolling back, a failed send (real HTTP 500) rolling back BOTH the
  target and its sibling, and a non-combined command (`jogJ1`) never
  propagating to the sibling at all. `./gradlew test` passes (same
  command this repo's own CI runs).
  Real gaps this does NOT yet close, left
  for a follow-up: `HydraWebSocket.kt`'s own reconnection logic and
  `Discovery.kt`'s subnet scan still have no dedicated test, and
  `NativeThreeDScreen.kt` (an unfinished Filament experiment, not wired
  into navigation) still needs a real finish-or-remove decision.

## [0.5.0] - Fullscreen-landscape 3D view: exit orientation, hidden Refresh button, and real cross-client sync for jog step/reset

- **`ThreeDScreen.kt` - the fullscreen-landscape overlay's exit "X" did
  nothing if the phone was still held sideways**: real feedback from live
  testing. `onExit` set `SCREEN_ORIENTATION_UNSPECIFIED`, which only
  releases the forced-landscape lock and defers to whatever the physical/
  sensor orientation currently is - with the phone still held sideways,
  the screen stayed landscape and the button looked broken. Now forces
  `SCREEN_ORIENTATION_PORTRAIT` instead, matching the actual request
  ("al darle debería poner el móvil en modo vertical"); the existing
  `LaunchedEffect(isLandscape)` that already released the Fullscreen
  button's own forced-landscape lock now releases this forced-portrait
  lock the same way once portrait is actually reached, so neither button
  fights a later physical rotation back the other way.
- **`ThreeDScreen.kt` - the Refresh button became unreachable in
  landscape, silently eaten by the exit "X"**: the top control bar's
  title `Column` carried a `weight(1f)` with no `fillMaxWidth()` on the
  `Row` itself - a weighted child still forces a Row to expand to the
  full available width, which in landscape stretched the whole bar (and
  the Refresh button at its right edge) into the exit "X"'s own `TopEnd`
  corner, a higher-`zIndex` sibling that silently intercepted every tap
  meant for Refresh. `weight(1f)` removed; the bar stays compact at its
  intended top-left corner.
- **Jog step (1mm/10mm/50mm) is now a real, server-synced setting**
  (`robot.jogStep`, new atomic `jogStep` command) instead of a
  component-local default kept independently by `ThreeDScreen.kt`,
  `ControlScreen.kt`, and HYDRA-UMC-STUDIO's own step selector - a step
  chosen on one client silently never matched another. Real request from
  live multi-client testing ("Házlo un ajuste real sincronizado").
- **Reset, Reset 3D, and the XY table (joystick and position controls)
  now really sync across every connected client, in both directions** -
  real feedback from live testing found none of them did. Root cause:
  HYDRA-UMC-STUDIO's own Reset/HOME/HOME XY buttons and XY table sliders
  (rendered natively on desktop, and inside this app's own embedded 3D
  WebView in landscape mode) wrote through `updateRobot()`, an
  optimistic-local + 500ms-debounced-full-tree-save path that never
  broadcasts to any OTHER connected client. Moved to the same atomic,
  broadcasting command channel this app's own jog/play/pause/stop
  already use (`sendRobotCommand`/two new server commands, `reset` and
  `reset3D`, plus `absolute` support added to the existing `jog`
  command) - see HYDRA-UMC-STUDIO's and HYDRA-UMC-SERVER's own
  changelogs for the full server-side detail.

## [0.4.9] - Fullscreen-landscape joysticks now win touch priority over the embedded WebView

- **`ThreeDScreen.kt` - the XY/Z joysticks in fullscreen-landscape mode
  stopped responding to touch**: real feedback from live testing after
  `[0.4.8]`'s console removal - the top control bar and (before that
  removal) the native console still worked, but the two thumb-pad
  joysticks did not. `FullscreenJogOverlay` is the only place this app
  renders native Compose touch targets directly on top of the embedded
  WebView's own full-screen bounds (`ControlScreen.kt`'s joysticks live
  on a completely separate screen with no WebView underneath) - a plain
  Compose sibling of an `AndroidView` isn't always guaranteed to win
  hit-testing over that `AndroidView`'s own native touch handling
  (WebView reads raw touch itself, for the embedded 3D canvas's own
  orbit-drag). Wrapped the overlay in an explicit `Modifier.zIndex(1f)`
  Box - the standard, documented fix for a Compose overlay losing touch
  priority to an `AndroidView` sibling. Not independently reproducible
  without a physical device this pass; needs the project owner's live
  confirmation on the next build, same as several other WebView-adjacent
  fixes already on record in this file.

## [0.4.8] - 3D viewport no longer duplicates the Robot Control console; update dialog now opens the Updates screen

- **`ThreeDScreen.kt` - the 3D viewport duplicated the Robot Control menu's
  own E-STOP/play/pause/stop console**: real feedback from live testing.
  Both the portrait 3D view and the fullscreen-landscape jog overlay each
  rendered their own copy of `PlaybackConsole` - identical to, and
  redundant with, the one `ControlScreen.kt`'s Robot Control menu already
  shows. This is what the note just below (kept for its own remaining
  question about the hidden `Scaffold`) had flagged as worth a second
  look. Removed from both orientations; the 3D viewer is jog/view-only.
- **The "Update and install" button on the update dialog never opened the
  Updates screen**: tapping it only called `downloadAndInstall()` - the
  dialog's own visibility condition (`updateState is
  AppUpdateState.Available`) stops matching the instant the state moves
  on to `Downloading`, so the dialog just vanished and the operator was
  dropped back on whatever screen was underneath with no way to see
  download progress, the install-permission prompt, or a failure.
  `MainScreen.kt` now also navigates to Settings on that same tap, and
  `SettingsScreen.kt` opens directly on its own Updates tab whenever an
  update is actively in flight (`Available`/`Downloading`/
  `InstallPermissionRequired`/`Installing`/`Failed`) instead of always
  defaulting to Wi-Fi.
- **`ThreeDScreen.kt` - the fullscreen-landscape button locked orientation
  and never released it**: tapping the 3D viewport's fullscreen button set
  `requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE`, which the OS
  honors over the accelerometer - physically rotating the device back to
  portrait afterward did nothing until the user found the small
  in-viewport exit "X" (`FullscreenJogOverlay`'s own `onExit`), which is
  the only place that reset it back to `UNSPECIFIED`. Found reading the
  code directly (no live Android device available this pass - not yet
  confirmed as THE cause of a specific field report, but a real,
  independent bug on its own merits regardless). Now releases the lock
  back to `UNSPECIFIED` the moment `isLandscape` actually becomes true -
  the button only ever nudges the device into landscape once, it never
  fights a later physical rotation back to portrait.
- Separately, `MainScreen.kt`'s own `isLandscape && isOnThreeDScreen`
  early return (real, by design - see that file's own comment on why it
  was narrowed there) hides the entire `Scaffold` - all navigation and
  buttons - while on the 3D tab in landscape. Worth a second look
  together with the project owner: is that the "los botones" behavior
  being reported, or a separate issue - not changed here since it is a
  deliberate, already-reviewed design choice, not an obvious bug.

Verified: `compileDebugKotlin` passes. Live-confirmed by the project owner
after installing the previous build: the duplicated console and the
update-dialog redirect were both real, reproducible issues.

## [0.4.7] - RobotViewModel's auth token is now real Compose state

- **`RobotViewModel.apiClient` and `HydraApiClient.authToken` are plain
  Kotlin `var`s, not `mutableStateOf`** - a `@Composable` reading
  `viewModel.apiClient?.authToken` as a plain `val` has no reliable
  recomposition signal when a login or reconnect happens after that
  screen has already composed once (e.g. auto-connect on a saved profile
  while the user is already sitting on a tab, or a settings change made
  from elsewhere in the app). `ThreeDScreen.kt` did exactly this to build
  the embedded WebView's URL
  (`http://$ip:$port/?hideUI=true&robotId=...&token=...`), so a stale or
  empty token could reach STUDIO's own page, breaking that WebView's own
  independent WebSocket session while this app's native REST/WS calls
  (which read `apiClient` directly, not through Compose) kept working.
  Added `RobotViewModel.authTokenState` (`mutableStateOf<String?>`),
  updated at all 3 real token-write sites plus the BLE-only
  disconnect path, and changed `ThreeDScreen.kt` to read that instead of
  reaching into `apiClient` directly. A real, independent correctness
  fix - not a claim that it was the proximate cause of the live desync
  report `[0.4.6]` already investigated: that report's most likely real
  explanation (a stale STUDIO build deployed on the CM5, predating
  STUDIO's own `[0.2.2]`/`[0.2.8]` fixes) is still on record there and
  not yet re-confirmed against a live device.

Verified: `tools/build_test.py` (`gradlew.bat assembleDebug
-PhydraUmcReadOnly=true`) passes. Not yet re-tested on a live device -
the CM5 this would be tested against is mid-flash as of this entry.

## [0.4.6] - Watch relay calls now identify themselves distinctly to Server

- **`HydraApiClient`'s shared interceptor is no longer unconditional** -
  it used to stamp `X-Hydra-Client: android` onto every outgoing request,
  including the 2 calls `WatchVoiceRelayService` makes on the paired
  Watch's behalf (`postWatchVoiceTurn()`/`getWatchSystemStatus()`), so a
  Watch-relayed request was indistinguishable from this app's own direct
  traffic - Server's new Config > Remote Access "Watch" toggle (see that
  repo's own changelog) had no real signal to gate on. Those 2 calls now
  set `X-Hydra-Client: watch` explicitly, and the interceptor only
  applies its "android" default when a request hasn't already declared
  its own client type.

- **Investigated the reported 3D-viewport desync (the 3D robot model not
  reflecting the real robot's movement, though jog commands sent from
  this app's own native joysticks moved it for real).** `ThreeDScreen.kt`
  embeds Server's own STUDIO web viewport in a WebView
  (`http://$ip:$port/?hideUI=true&robotId=...&token=...`) - the actual
  WS-driven 3D render logic lives entirely in that web bundle's own JS,
  not in this app, so root-caused it there instead of guessing at Kotlin
  changes. Found two real, concrete bugs, both fixed in
  HYDRA-UMC-STUDIO's own `0.2.8` (see that repo's changelog): (1)
  `apiBase.ts` hardcoded `:3000` for every `fetch()`/WebSocket call
  regardless of the port this exact page was loaded on - invisible when
  Server runs on its default port, but a real misdirect for any
  deployment on a different one; (2) the Server instance this was tested
  against had been serving a STUDIO frontend build from hours earlier
  that morning - redeployed fresh, which also picks up `0.2.2`'s own
  already-shipped Camera-PIP-vs-disabled-camera fix that a stale build
  would have masked. No code change needed in THIS repo for either -
  this app's own WebView setup (cache mode, LayoutParams, console
  logging) was already reviewed and found correct in `0.4.5`.
  Not yet re-confirmed against a live device with the redeployed
  Server - the `[WS] connected`/`[WS] delta` logcat diagnostics store.tsx
  already prints (see `ThreeDScreenConsole` in this app's own logcat)
  are the way to verify live next.

Verified: `compileDebugKotlin`/`testDebugUnitTest` both pass,
`version.properties` confirmed untouched by the verification compile
(the real fix from [0.4.5] holding).
Version bump per repo versioning scheme (bump_manifest_version.py +
bump_version_code.py).

## [0.4.5] - E-STOP moved into the 3D viewport, real Gradle version-drift fix

- **E-STOP/play/pause/stop console now lives inside the 3D viewport
  itself** (`PlaybackConsole.kt`, new) - a real request from live device
  testing: this control surface used to exist only on the Control tab,
  never on the 3D tab, portrait or fullscreen-landscape. Extracted
  ControlScreen.kt's own floating console into this shared composable
  (same real long-press E-STOP/STOP protection, not a second copy of that
  safety logic) and wired it into `ThreeDScreen.kt`'s portrait view and
  its fullscreen-landscape overlay, bottom-center in both. The E-STOP
  itself now pulses continuously (animated red glow) instead of sitting
  static - meant to read as the one control that must be found instantly,
  not just another button.
- **Fullscreen-landscape joystick thumb zones re-centered** - `JoystickXYPad`/
  `JoystickZColumn` moved from `BottomStart`/`BottomEnd` to `CenterStart`/
  `CenterEnd`: anchored to the bottom, both sat noticeably lower than where
  a thumb naturally rests holding the phone landscape.
- **Fixed a real version-mirror drift bug in `app/build.gradle.kts`** -
  found live, mid-session: it wrote `version.properties` itself at Gradle
  CONFIGURATION time on *any* real task (`assembleDebug`, `compileDebugKotlin`,
  ...) unless `-PhydraUmcReadOnly=true`/`HYDRA_UMC_CI=1` was passed - two
  plain verification compiles this session silently advanced
  versionPatch/versionCode twice with the manifest never moving, exactly
  the drift class this ecosystem's version-mirror convention exists to
  prevent (same bug already fixed in HYDRA-UMC-WATCH's own
  `build.gradle.kts`). Gradle now only *reads* `version.properties`;
  `bump_manifest_version.py` + `bump_version_code.py`, run from
  `build-android.bat`/`.sh` before Gradle, are the only real source of a
  version bump - `bump_version_code.py`'s own docstring already documented
  this exact contract, the code just hadn't matched it yet.
- Extracted the GitHub Release metadata gate into the JVM-testable
  `ReleaseMetadataParser`. New tests prove that only a newer stable release
  with the exact HTTPS APK asset is offered; draft, prerelease, malformed,
  missing-asset, HTTP and non-newer metadata never initiate a download.
- Added `WatchVoiceRelayService` using Wear OS Data Layer. It accepts only
  bounded recognised text or a health-card request from the paired Watch,
  loads the phone's encrypted Server session and returns the typed reply. It
  never exposes a token to the Watch and never invokes a robot-command API.
- Added the official `play-services-wearable` dependency. Both Android and
  Watch use the same application ID/signing certificate, which Data Layer
  enforces before it accepts a message.
- New `values-zh/strings.xml` (Simplified Chinese) and `values-ja/strings.xml`
  (Japanese) - full translation of all 147 strings, matching the coverage
  of the existing values-es/values-de/values-fr/values-it resource sets.
  No in-app language switcher exists (none of the other 4 languages have
  one either) - Android's own per-app language picker (system Settings,
  API 33+) or device locale resolves these automatically, same as the
  other 4. Verified with a real `./gradlew processDebugResources` (build
  successful, all 3 string sets confirmed to carry exactly 147 entries
  each via a real XML parse).
- New `README_zho.md` / `README_jpn.md` documentation translations, plus
  the 5 existing README files' language selectors updated to link them.
- **New `Parol6Kinematics.kt`** - a faithful Kotlin port of HYDRA-UMC-STUDIO's
  own real Parol6 inverse kinematics (`src/examples/parol6Kinematics.ts`:
  the same 6-step PAROL6.urdf transform chain and multi-seeded
  Newton-Raphson j2/j3 solve, not a redesign). Verified against a real
  oracle - the actual TS source run with the real `three.js` library for
  5 test poses - via 5 new JUnit tests (`Parol6KinematicsTest.kt`), all
  matching to within 1e-3 degrees.
- `RobotViewModel.kt`'s new `jogXYZ()` resolves the joystick D-pad's
  combined dx/dy/dz delta against a Parol6-model robot's own real
  kinematics once, then sends that resolved `joints` override alongside
  each atomic `jog` command - mirroring `RobotDetail.tsx`'s own
  `handleXYZJog()`. Fixes the joystick moving a Parol6 robot (e.g. A1)
  differently from - and sometimes snapping toward a "home"-looking pose
  compared to - STUDIO's own floating joystick overlay for the same
  robot: without a client-supplied `joints` override, server.ts's own
  `jog` case falls back to `calculateJoints()`, a single generic IK
  formula that doesn't know Parol6's real (non-planar) kinematic chain.
  Every other model/target still jogs `pos.x/y/z` exactly as this app
  always has - the same gap every other non-STUDIO client (iOS, DSI,
  SUITE) still has for every model besides Parol6, not a regression.
- **Not confirmed this pass**: a live report that the fullscreen-landscape
  3D viewport only fills roughly the top half of the screen. Read through
  `ThreeDScreen.kt`'s WebView setup, `MainScreen.kt`'s fullscreen bypass,
  and STUDIO's own `viewportOnly` flex-height chain (already a real,
  verified-in-desktop-browser 100%-height chain) without finding a
  reproducible cause - needs a live device to actually pin down, same as
  the still-open Android/STUDIO 3D-sync investigation.

## [0.4.4]

- Build version synchronized with `hydra-umc.project.json` and the repository-native version source.

## [0.4.3] - Hardened the self-update APK download

- **`GitHubReleaseUpdater.kt`** - the downloaded update APK's actual byte
  count is now checked against GitHub's own declared content length when
  one is provided, and a truncated download is rejected and deleted
  instead of being handed to `packageArchiveInfo()`/the installer. An
  APK that fails any later real check (not a valid Android package,
  wrong package name, not actually newer than the installed version) is
  now deleted too, instead of being left on disk for a possible later
  install attempt with a file that was already rejected once.
- `docs/GITHUB_RELEASE_UPDATES.md` updated to match.
- Verified: `./gradlew :app:compileDebugKotlin` and
  `:app:testDebugUnitTest` both pass.

## [0.4.2] - Dedicated base-rotation (J1) buttons

- **New `BaseRotationButtons` composable** (`Joystick3D.kt`) - 2 buttons
  (rotate CW/CCW, press-and-hold repeat, same contract as every other jog
  control in this file), placed right alongside the existing XYZ jog
  D-pad in both the normal Control screen and the fullscreen landscape 3D
  overlay - mirrors STUDIO's own new base-rotation buttons in its
  floating JoystickOverlay (RobotDetail.tsx), requested directly.
- **New `RobotViewModel.jogJ1(direction, jogStep)`** - deliberately pure
  joint-space (no per-model IK needed for J1 alone, unlike `jogXYZ()`),
  so it works for every model, not just Parol6 (the only one with a real
  IK port in this app so far). Real per-model J1 limits only exist for
  Parol6 today (`PAROL6_JOINT_LIMITS_DEG`); every other model uses the
  same `[-180, 180]` generic fallback STUDIO's own `jointLimitsFor()`
  uses. Sends the exact same sanctioned `jog` command shape `jogXYZ()`
  already uses (`axis:'x', amount:0` + a `joints` override carrying the
  real desired state) - the real, established wire protocol, not a new
  command type.
- Only meaningful for the arm target, not the XY table (no J1 concept
  there) - gated the same way the Z column already is.
- Verified: `./gradlew :app:compileDebugKotlin` and
  `:app:testDebugUnitTest` both pass (no dedicated unit test for
  `jogJ1()` itself - `jogXYZ()`/`RobotViewModel` have none either, same
  existing precedent). Not yet live-verified against a real robot.

## [0.4.1] - Force the 3D-view WebView to never serve a stale build

- **`ThreeDScreen.kt`** - the embedded WebView now sets
  `settings.cacheMode = WebSettings.LOAD_NO_CACHE`. Investigated while
  chasing a reported "Android's 3D view and STUDIO's own browser tab
  don't sync with each other" - real, confirmed root cause NOT found via
  static analysis alone (the server-side broadcast, client-side delta-
  apply, and robot-selection logic all check out correct on inspection;
  a live device + a live STUDIO tab open side by side is what's actually
  needed to pin this down, see `store.tsx`'s own WS onopen/onmessage/
  onclose diagnostics for that). What WAS found and fixed while looking:
  HYDRA-UMC-SERVER's own bundled STUDIO frontend (`public/`, what this
  WebView actually loads) was stale - built from a commit several
  commits behind STUDIO's real HEAD, a real, easy-to-forget gap
  (`build-frontend.bat`/`.sh` has to be re-run by hand after every
  STUDIO change) that would show as exactly this kind of "one client
  behaves differently" symptom. Rebuilt it fresh as part of this pass.
  `LOAD_NO_CACHE` is shipped as a real, defensible fix regardless -
  correctness beats the marginal bandwidth cost on a LAN-only control
  surface - not a confirmed fix for the reported symptom.

## [0.4.0] - Real per-service live status in the Ecosystem tab

- **Telemetry > Ecosistema** now shows a real green/red "bulb" plus the
  service port next to any project whose server-side manifest declares a
  `service` object (`EcosystemProject.servicePort`/`.live`, new fields on
  `GET /api/ecosystem/status` - see HYDRA-UMC-SERVER's own changelog).
  Green means a real TCP/HTTP probe just succeeded; red means it just
  failed; no bulb at all for a library/CLI/firmware/UI that was never
  meant to run as a network service - never inferred from the existing
  `maturity` badge, which stays a separate, static manifest claim.

## [0.3.9] - Real device feedback on 0.3.8: immersive fullscreen, ecosystem tab, splash retune

Real, live feedback from testing 0.3.8 acted on directly:

- **Fixes the system gesture-nav bar staying visible in fullscreen
  landscape 3D mode**, undercutting the point of a fullscreen view.
  System bars are now hidden (swipe-to-reveal-temporarily) for exactly as
  long as that mode is active, and always restored on the way out -
  `DisposableEffect`-tied, so leaving the screen entirely can't leave them
  hidden behind it either.
- **Retuned the splash timing again** after live feedback that the
  previous 900ms+400ms felt too abrupt in the OTHER direction ("sin
  transición"). Now 2200ms hold + a genuinely visible 700ms fade - still
  nowhere near the original 7.5s, just not jarringly quick either.
- **New Ecosystem tab** (Telemetry screen, alongside the existing Logs
  tab) showing the server's own real V0 `GET /api/ecosystem/status` scan -
  every sibling repo's own manifest (role/stack/maturity/version/family),
  grouped by family. Real, not theater: explicitly labeled as unavailable
  when the server isn't running from a checkout with sibling repos beside
  it (a real future CM5 deployment), rather than pretending to show live
  health for services that mostly aren't deployed anywhere yet.
- Settings tabs (Wi-Fi/Bluetooth/Notifications/Updates) are now icon-only
  at double size, reported live as "se cortan las palabras y queda feo"
  with 4 tabs' worth of labels in the same row.

## [0.3.8] - Fullscreen landscape 3D view with an overlaid jog joystick

- **New: a fullscreen, landscape, chrome-free mode for the 3D viewport**,
  requested for real robot operation while watching the live 3D view fill
  the whole screen. A new button next to Refresh forces the Activity into
  landscape (reusing MainScreen.kt's existing isLandscape-triggered
  fullscreen bypass rather than a new code path - this button just gives
  it a manual trigger alongside the physical-rotation one it already had),
  which hides the app's own menus entirely. The jog joystick is overlaid
  semi-transparently, game-controller style: the XY pad bottom-left, the Z
  column bottom-right (`Joystick3D.kt` split into separately-usable
  `JoystickXYPad`/`JoystickZColumn` halves for this - `Joystick3D` itself
  is unchanged for ControlScreen.kt's existing portrait use), plus
  step-size chips top-center and a close button top-right to return to the
  normal app view.
- **Not yet live-verified visually** - built and confirmed compiling
  (debug and release) but the actual on-screen positioning/transparency/
  ergonomics need a real device check before calling this done.

## [0.3.7] - In-app notifications toggle, and a real update dialog redesign

- **New in-app notifications toggle** (Settings > Notifications), separate
  from Android's own per-app notification permission - gates the
  persistent safety notification (shown for as long as a WebSocket
  connection stays open) and job-completion alerts. Defaults on, matching
  this app's behavior before the preference existed. Turning it off mid-
  session hides the persistent notification immediately, not just on the
  next reconnect.
- **Redesigned the update-available dialog** (reported as "sale muy
  feo"): it used to dump the full GitHub release notes directly inside
  the AlertDialog's own fixed-size `text` slot, visually squeezing/clipping
  anything longer than a couple of lines. Now shows installed vs. new
  version as two clearly labeled lines, with a "View changes" button that
  opens a dedicated, properly scrollable full-size dialog for the release
  notes instead.

## [0.3.6] - The 10+ second black screen on startup, traced to a real 17.5s stall

- **Fixes a long black screen between the splash and the login/dashboard
  screen appearing** (reported as "sigue pasando" after 0.3.5's splash
  timer was already shortened - the timer was never the actual cause).
  Traced with live timing logs to a single call, `AuthPrefs.loadAuth()`,
  taking **17.5 seconds** on one real cold start. Two real, independent
  fixes:
  - `AuthPrefs.kt`'s `MasterKey`/`EncryptedSharedPreferences` instance was
    being rebuilt from scratch - a real AndroidKeyStore round-trip, not a
    cheap object - on every single `loadAuth()`/`saveAuth()`/`clearAuth()`
    call instead of once. Now cached for the lifetime of the (already
    singleton, per `RobotViewModel`) `AuthPrefs` instance.
  - `AuthPrefs.kt`'s own Keystore work now runs on a small dedicated
    single-thread dispatcher instead of the app-wide shared
    `Dispatchers.IO` pool - real hypothesis, not yet live-confirmed (the
    test device left before a rebuild could be verified): `Discovery.kt`'s
    subnet scan (`scanNetwork()`, launched at essentially the same instant
    from `MainActivity`'s own startup effect) could occupy up to
    `SCAN_CONCURRENCY` (was 64) real OS threads from that same shared pool
    simultaneously, each held for up to `SCAN_TIMEOUT_MS` against every
    unreachable address in a /24 subnet - plausibly starving unrelated
    `Dispatchers.IO` work queued at the same moment. `SCAN_CONCURRENCY`
    lowered to 16 regardless, so this scan can no longer fully saturate
    that shared pool even if the isolation above weren't already
    sufficient on its own.
- **Fixes STUDIO's own splash screen adding ~10s to opening the 3D
  viewport tab.** `App.tsx` (HYDRA-UMC STUDIO) showed a fixed, unconditional
  10-second branding splash on every mount, including
  `ThreeDScreen.kt`'s embedded WebView, which loads that exact page fresh
  every time the 3D tab opens. Now skipped whenever `?hideUI=true` is
  present - the same flag that already means "embedded, no chrome" for
  Dashboard.tsx's own header/sidebar.
- Also shortens this app's own native `CustomSplashScreen` from a fixed
  5000ms hold + 2500ms fade (7.5s total) down to 900ms + 400ms (1.3s) -
  pure branding-delay trim, not gating on anything real either way.
- Also fixes a real, separate bug found investigating the above:
  `RobotViewModel`'s cached-session auto-login used to flip `isLoggedIn`
  true *before* `connect()` had confirmed the cached server was actually
  reachable - if it wasn't, the full (empty) Dashboard showed first,
  reading as another stuck/black screen against this app's dark theme,
  for however long the connection attempt took to time out, before
  finally reverting to LoginScreen. `isLoggedIn` now only flips true once
  a `connect()` attempt actually succeeds (new
  `onInitialConnectSucceeded` callback) - LoginScreen (fields already
  pre-filled) shows the whole time a reconnect is pending instead.

## [0.3.5] - The 3D viewport finally renders, plus a second real login bypass and a real login crash

- **Fixes the 3D viewport rendering solid black (or white after a manual
  refresh) - it "never worked".** Root cause, confirmed by loading the
  exact same URL in a real desktop browser (identical to every part of the
  page rendering with real, correct sizes there): this WebView's own
  `layoutParams` were left to Compose's `AndroidView` modifier system
  alone, and a WebView specifically can establish its internal Chromium
  layout viewport from whatever `LayoutParams` (or lack of them) were
  present when it attached - not from whatever pixel bounds it's later
  resized to. `ThreeDScreen.kt`'s WebView now sets real
  `MATCH_PARENT`/`MATCH_PARENT` `LayoutParams` explicitly on creation, the
  standard fix for this class of bug. Also now defers the WebView's first
  `loadUrl()` until Compose has actually measured it (real nonzero size
  confirmed via `onSizeChanged`) instead of firing it unconditionally from
  `factory`, and sets `useWideViewPort`/`loadWithOverviewMode` - neither
  turned out to be the root cause, but both are correct settings for a
  WebView rendering a real responsive layout.
- **Fixes login crashing immediately with `AEADBadTagException` after a
  reinstall.** `android:allowBackup="true"` with no exclusion rules let
  Android's auto-backup restore `AuthPrefs.kt`'s `EncryptedSharedPreferences`
  ciphertext on a fresh install, but the Keystore-backed AES key it was
  encrypted with is hardware-tied and never survives that restore - every
  decrypt attempt threw, crashing any screen that touches auth (including
  login) on every launch. `AuthPrefs.kt` now recovers automatically (clears
  the corrupted prefs file AND the stale AndroidKeyStore entry, then starts
  a fresh keyset - the file alone wasn't enough, confirmed live: the retry
  failed identically until the Keystore entry itself was also cleared);
  new `data_extraction_rules.xml`/`backup_rules.xml` exclude this file from
  backup going forward so this can't recur.
- **Fixes a second, distinct login bypass**: `MainActivity`'s splash screen
  dismissed on a fixed timer alone, unrelated to whether
  `RobotViewModel`'s own cached-session check (an async coroutine reading
  `AuthPrefs`) had actually finished. A slow check could still be running
  after the timer elapsed and the real login form was already showing and
  being typed into - the cached session then resolved moments later and
  flipped straight to the dashboard, reusing the LAST session's saved
  credentials rather than whatever was just typed. New
  `RobotViewModel.authCheckComplete` flag; the splash now stays up until
  both the timer AND this are true.

## [0.3.4] - The login bypass fix actually needed to persist, not just correct memory

- **Fixes the login screen still letting the dashboard show without real
  credentials, reported as still happening after v0.3.1's own fix.** That
  earlier fix only corrected the in-memory `isLoggedIn` flag when a
  cached session's server turned out to be unreachable - it never wrote
  the correction back to the saved profile on disk. So every single cold
  start kept reading the same stale `isLoggedIn: true` from a past
  session, flashed the main screen again, and (eventually) reverted -
  the exact same cycle repeating on every launch for as long as the
  cached ip/port stayed unreachable, which read as "sometimes it still
  lets me in". `RobotViewModel.kt`'s cached-session auto-login now also
  persists `isLoggedIn: false` via `authPrefs.saveAuth()` when the
  follow-up connection fails, so the next cold start shows LoginScreen
  (fields still pre-filled) instead of repeating the same flash.
  username/password/rememberMe/token are left untouched - only whether a
  session auto-resumes on the next launch changes.

## [0.3.3] - Fixed a SECOND release-only crash on launch (v0.3.2's own Tink fix was real, but not the whole story)

- **Critical fix**: v0.3.2 still crashed identically on a real device
  despite the Tink fix, confirmed via a real `adb logcat` from a
  connected device (the first time this investigation had one available)
  showing the actual fatal exception: `Unable to get provider
  androidx.startup.InitializationProvider: Failed to create an instance
  of androidx.work.impl.WorkDatabase`. `androidx.work` (WorkManager) is
  only a TRANSITIVE dependency - nothing in this app calls WorkManager
  directly - but it self-registers via `androidx.startup
  .InitializationProvider`, a ContentProvider that runs before
  `Application.onCreate` and before any UI, so R8 stripping something it
  needed (confirmed against `usage.txt`: ~627 removed `androidx.work.**`
  lines) crashed the process before Tink's own code path was ever
  reached. Same class of bug, same standard fix: added `-keep` rules for
  `androidx.work.**` to `app/proguard-rules.pro` (removed-line count
  dropped to 1). Verified live this time, not just by static analysis -
  installed directly via `adb install -r` onto a real device, confirmed
  the process stays alive past launch with no `AndroidRuntime` fatal in
  logcat, and the owner confirmed the app opens and works normally.

## [0.3.2] - Fixed a release-only crash on launch, plus real diagnostics for the still-open "3D viewport shows no robot" report

- **Critical fix**: every release build up to and including v0.3.1 crashed
  immediately on launch (before any UI, including the splash screen, ever
  rendered) once installed from a real device instead of `gradlew
  installDebug`. Root cause: R8 (only ever exercised by a real release
  build - debug builds skip it entirely, so this was the first time it
  ran against this app's actual code) silently stripped ~4390 lines of
  `com.google.crypto.tink.**` classes as unreachable dead code, confirmed
  by reading the release build's own `app/build/outputs/mapping/release/
  usage.txt`. Tink is what `androidx.security.crypto`'s
  `EncryptedSharedPreferences`/`MasterKey` use internally (via reflection
  R8's static analysis can't see) to store the login session -
  `AuthPrefs.kt`, read at app startup inside `RobotViewModel`'s own
  `init`, so the missing classes crashed the process before a single
  screen could compose. Added the standard, widely-documented Tink keep
  rules to `app/proguard-rules.pro`; the rebuilt release now retains all
  1419 real Tink classes the encrypted-prefs path needs (re-verified
  against the same `usage.txt`/`mapping.txt`).
- Real diagnostics for the still-open "3D viewport shows no robot" report.
  `ThreeDScreen.kt`'s embedded WebView had no error surface at all - a
  real page-load failure and a page that loads fine but fails to render
  the WebGL viewport looked identical from outside (both just blank).
  `WebView.setWebContentsDebuggingEnabled` (debug builds only - a release
  never exposes this, since it lets any USB-connected desktop inspect
  this WebView's DOM/JS/network, including the auth token in its URL)
  now lets `chrome://inspect` attach to this exact WebView for a real
  console/network trace. `onReceivedError`/`onReceivedHttpError`
  overrides log a genuine main-frame navigation or HTTP failure to
  logcat, which previously reached nowhere at all.
- The WebView's custom user-agent string used to replace Chromium
  WebView's own default outright, dropping the browser-engine
  identification some WebGL vendor/driver quirk-detection code keys off
  of - now appended to the real default instead, a real (if unverified
  without a live device) candidate for why the viewport specifically
  could fail to render even on a page that loads correctly.
- Root cause still not confirmed live (matches the note left on this
  same investigation in an earlier session) - these are the concrete
  tools needed to actually see what's failing next time it's
  reproduced, not a claimed fix for the underlying render bug itself.

## [0.3.1] - First published GitHub Release, and the release build that made it possible

- Fixed the "GitHub returned HTTP 404" update-check error: root cause was
  that no GitHub Release had ever been published for this repo, and no
  release keystore existed yet to sign one with. Generated a protected
  release keystore and published the first real GitHub Release (v0.3.0).
- `app/proguard-rules.pro`: added the standard OkHttp `-dontwarn` rules
  for its optional BouncyCastle/Conscrypt/OpenJSSE TLS-provider
  integrations (none is an actual dependency here) - the very first real
  `assembleRelease` build failed `minifyReleaseWithR8` without them, since
  debug builds never exercise R8 at all.
- Added a direct `androidx.fragment:fragment-ktx` dependency to force a
  modern resolution across the graph - a transitive dependency was
  pinning it below 1.3.0, which lint's own
  `InvalidFragmentVersionForActivityResult` check fails a release build
  over (`registerForActivityResult` in `MainActivity.kt` needs it).
- New `publish-github-release.ps1`/`.sh`, called automatically by
  `prepare-github-release.bat`/`.sh` right after a successful signed
  build whenever a personal `.env`/`GITHUB_TOKEN` is configured locally
  (see new `.env.example`) - creates the GitHub Release for the current
  version (or replaces just the APK asset if it already exists) and
  uploads the signed APK to it. Deliberately never runs in CI, matching
  this project's existing release-signing security model.
- `RobotViewModel.kt`: a cached session (`rememberMe`) whose configured
  server is now unreachable or wrong (switched networks, server moved, a
  stale ip/port) used to set `isLoggedIn` true immediately and never
  revert it when the follow-up `connect()` failed - `MainActivity` gates
  purely on that flag, so the app showed the full main screen with no
  real connection behind it instead of bouncing back to LoginScreen where
  the ip/port could actually be fixed. Now only that one cached-session
  path reverts `isLoggedIn` on an initial connect failure; a manual
  reconnect or server switch on an already-active session still tolerates
  a transient error without forcing a real logout.

## [0.3.0]

- Build version synchronized with `hydra-umc.project.json` and the repository-native version source.

## [0.2.9]

- Build version synchronized with `hydra-umc.project.json` and the repository-native version source.

## [0.2.8]

- Build version synchronized with `hydra-umc.project.json` and the repository-native version source.

## [0.2.7] - Fixed a version double-bump bug in the real build script

`build-android.sh`/`build-android.bat` called `bump_manifest_version.py`
(which performs a real increment of `version.properties` *and* the
manifest together) and then ran `./gradlew assembleDebug` with no
read-only flag - so `app/build.gradle.kts`'s own configuration-time bump
also fired, silently advancing the native version twice per real build
while the manifest only ever advanced once. Fixed by adding
`bump_version_code.py` (increments Android's separate `versionCode`
counter, which `bump_manifest_version.py` intentionally doesn't touch -
see its own docstring) and passing `-PhydraUmcReadOnly=true`/`HYDRA_UMC_CI=1`
to the Gradle invocation, the same flag `build-test.sh`/`build-test.bat`
already used for their compile-only, non-mutating CI check - so there is
now exactly one real bump path, owned by the build scripts, and Gradle's
own bump code stays inert during a real build.

## [0.2.6]

- Build version synchronized with `hydra-umc.project.json` and the repository-native version source.

## [0.2.0] - Landscape no longer hijacks every screen to fullscreen 3D

- Rotating to landscape used to unconditionally force the fullscreen 3D
  view regardless of which tab was actually open - fine on a phone
  (landscape usually means "I just rotated to look at the 3D view"),
  but a real problem on a tablet, where landscape is the natural
  default orientation: rotating away from portrait while on Dashboard/
  Control/Camera/Telemetry/Settings got hijacked to 3D with no way back
  except rotating to portrait again. `MainScreen.kt` now only takes
  over fullscreen in landscape when the 3D tab was already the one
  open - every other screen now renders normally in landscape instead.
- Verified with a real `./gradlew compileDebugKotlin` (BUILD
  SUCCESSFUL, version 0.1.9 -> 0.2.0).

## [0.1.9] - Diagnostic logging for the open Pause/Stop investigation

- `RobotViewModel.kt`'s `sendAtomicCommand()` now logs a `TX OK [command]`
  line to the on-screen telemetry log right after a command POST actually
  succeeds - previously only the failure path logged anything
  (`TX Error [command]: ...`), so a command that reached the server with
  no visible effect looked identical, in this app's own log, to one that
  never sent at all. Doesn't change any control logic - pure visibility,
  meant to make the next live reproduction of the still-open Pause/Stop
  bug (Play/E-STOP work, Pause/Stop don't - investigated end-to-end
  across Android/server/STUDIO, root cause not yet confirmed live) faster
  to narrow down without needing `logcat` open just for that first split.

## [0.1.6] - Control screen's joystick is now the same one STUDIO uses

- **New `Joystick3D.kt`** - a straight Compose port of HYDRA-UMC-STUDIO's
  own `src/components/Joystick3D.tsx` (same name, same 8-direction D-pad
  layout with real diagonal buttons, same `onJog(dx,dy,dz)` signed-
  multiplier contract, same 150ms press-and-hold repeat), not a redesign.
  Replaces `ControlScreen.kt`'s old 6-button cardinal-only cross (no
  diagonals, single tap per press) - a diagonal press now sends both X and
  Y jogs in one hold, matching STUDIO exactly.
- Added `zEnabled`, separate from the D-pad's own `enabled` - this app
  (unlike STUDIO, which only ever jogs the robot arm) also jogs an XY
  table with no Z axis of its own, so the Z column is dimmed/inert rather
  than removed when that target is active, keeping the D-pad's geometry
  fixed instead of jumping around on toggle.

## [0.1.5] - Discovered-server list no longer shows duplicates

- **Same server listed twice in the server dropdown.** `Discovery.kt`
  runs the subnet HTTP scan and the mDNS (`_hydra._tcp`) listener
  concurrently and independently - the same real server routinely
  answers both (its own LAN IP gets probed directly, and it also gets
  found via mDNS moments later), each emitting its own `ServerInfo` for
  the identical host/port. `RobotViewModel.scanNetwork()`'s collector
  appended every emission unconditionally, with no de-duplication -
  unlike `connect()`'s own append a few lines below it, which already
  checks host+port identity before adding. Now `scanNetwork()` uses
  that same host+port check.

## [0.1.4] - Auto-login-on-launch now actually connects

- **Remembered-session auto-login left the app "logged in" with no live
  connection.** `RobotViewModel.init{}` restores a saved session
  (`rememberMe` + a previously-successful login) by flipping
  `isLoggedIn` to `true` directly - `MainActivity` gates LoginScreen vs.
  the main dashboard purely on that flag, so the screen swapped away
  from LoginScreen immediately, before any WebSocket/REST connection
  had been (re)established. The manual login button's own success path
  always called `connect()` right after setting `isLoggedIn`; this
  background restore never did, so the resulting dashboard looked
  logged in but had nothing behind it until the user logged out and
  back in - which incidentally exercises the working, connect()-calling
  path and "fixes" it, hence the symptom. This also explains reports of
  the dashboard appearing while credentials were still being typed on a
  fresh LoginScreen visit: the restore runs on a background coroutine
  with no ordering guarantee against user input, so it could resolve at
  any point during that visit. Now the restore calls `connect()` too,
  matching the manual login path exactly.

## [0.0.9] - Background efficiency + Bluetooth-settings live reaction

- **`saveState()` debounce** - was writing the full app state to DataStore
  on every WebSocket push and every optimistic mutation with no throttling;
  now debounced via `stateCacheJob` (1000ms).
- **Metrics loop respects app lifecycle** - `startMetricsLoop()` kept
  polling `GET /api/system/metrics` every 5s even with the app backgrounded;
  now paused/resumed via `ProcessLifecycleOwner`.
- **Live Bluetooth status in Settings** - `refreshBtStatus()` previously
  only ran once when Settings opened, missing a Bluetooth toggle made from
  system Settings while this screen stayed open; now reacts live via a
  `BroadcastReceiver` on `ACTION_STATE_CHANGED`.
- **Doc/reference cleanup** - corrected several stale references claiming
  HYDRA-UMC STUDIO hosts `server.ts` (moved to the standalone
  HYDRA-UMC-SERVER project) across the README (+4 translations),
  `docs/ARCHITECTURE.md`, `AndroidManifest.xml`, and 3 `.kt` file headers.

## Earlier history

### 0.0.0 and prior (pre-versioning-policy history)

- **Initial scaffolding** - Repo created as a placeholder, then scoped:
  native Android (Kotlin + Jetpack Compose) console for a HYDRA-UMC robot
  over Wi-Fi or Bluetooth via the CM5. Initial pass shipped
  `docs/ARCHITECTURE.md` (Wi-Fi transport real/working via the shared
  `REMOTE_API.md` contract; Bluetooth honestly marked blocked - no BLE GATT
  service existed yet on the CM5 side), a Gradle skeleton, and VS Code
  tasks. Owner built the real app himself in the sessions that followed.
- **Recon audit** - Read-only audit found README/ARCHITECTURE.md
  overclaiming vs. the real code: the 3D view was still WebView-based
  despite README claiming a native Filament migration
  (`NativeThreeDScreen.kt` was unrouted dead code); the cold-start Global
  E-STOP widget could silently do nothing; "Long-Press Protection" on
  safety buttons didn't exist; Enable/Disable didn't propagate to combined
  robots the way Play/Pause/Stop did; the atomic command endpoint existed
  server-side but was never called (a full-tree overwrite was used
  instead); the camera picker used a fixed 1-8 range with a `hasCamera`
  false-positive bug; speed/accel sliders used hardcoded ranges; JDK
  claimed 21 but was still wired to 17 in 5 places.
- **Mass fix pass** - Same-session implementation of the above: JDK 21
  aligned in all 5 places (`updateDaemonJvm` regenerated properly, not
  hand-edited); `hasCamera` fixed to drop the always-present `"camera"`
  key check; a new server-side atomic `vision` command plus a
  `CameraScreen.kt` rewrite (real robot list, "Camera Disabled" state,
  on/off switch); `RobotViewModel.kt` fully migrated to the atomic-command
  path via a new `sendAtomicCommand()`, with the old full-tree sync code
  deleted; real Long-Press Protection added to E-STOP/STOP; the cold-start
  Global E-STOP widget fixed via a `pendingGlobalEstop` flag plus a
  `LaunchedEffect` gated on a populated robot roster.
- **Security pass** - Credentials migrated from plaintext DataStore to
  Keystore-backed `EncryptedSharedPreferences` (AES-256-GCM); an
  `X-Hydra-Client: android` header added to every request; WebSocket close
  code `1008` (invalid/expired token) now stops auto-reconnect instead of
  retrying forever with the same bad token.
- **Real-server bug reports** - Owner tested against a real (renamed)
  server and reported 3 bugs, all root-caused and fixed: subnet discovery
  matched servers by a literal `product` string default instead of
  `remoteApiVersion` (the same criterion the manual-connect path used), so
  a renamed server was invisible to auto-scan; dangerous Android runtime
  permissions were declared in the manifest but never actually requested,
  silently breaking NSD/BLE scanning on API 23+; the 3D WebView's `update`
  block was empty, so a token/IP change after first load never reached an
  already-open page, sometimes showing STUDIO's own embedded login screen
  instead of the 3D viewport. The login screen gained IP/port fields
  alongside credentials.
- **README translations** - `README_spa/ita/fra/deu.md` added as full
  translations (not summaries), plus a "Repository Structure" section and
  a licensing note (code GPL-3.0-or-later, docs CC BY-SA 4.0), matching the pattern
  already used by `URTC-FLASHER`/`URTC-TESTER`.
- **Line-by-line review (5 parallel sub-agents)** - Full read of all 34
  real `.kt` files, consolidated into targeted fixes: `clearAuth()` now
  actually clears the stored token; WebSocket `send()` checks the live
  socket before its echo-dedup guard; a hardcoded English string
  comparison for the connection indicator (broken in 4 of 5 languages)
  fixed to compare against the localized resource; per-command-type
  debounce jobs (a `setSpeed()` mid-debounce no longer gets silently
  cancelled by an unrelated jog/valve command); real optimistic-update
  rollback on a failed atomic command; a `LaunchedEffect` added so failed
  jog/valve/pump/E-STOP commands actually surface a Toast;
  `ThreeDScreen.kt`'s `WebView` now destroyed on release instead of
  leaking a live `requestAnimationFrame` loop; `startMetricsLoop()` now
  cancels its previous job instead of stacking pollers across reconnects.
- **Triage close-out** - Root cause of "combined robots only ever grow"
  found: the server always sends the *full* settings tree even for its
  `"delta"`-labeled messages (never a real partial diff), but
  `HydraState.mergeArrays()` only ever appended array elements - fixed by
  replacing state wholesale on every WebSocket message instead of merging.
  Job-completion notifications no longer fire on a plain manual STOP (now
  require the server's own `isFinished` flag). `connect()`/BLE/WebSocket
  callbacks moved onto a dedicated `Main.immediate` dispatcher to avoid a
  theoretical race mutating the shared JSON state tree from background
  threads. Several BLE robustness fixes (write-confirmation callback, GATT
  close on device-initiated disconnect, double-connect guard).
  `Discovery.kt` now waits for in-flight mDNS resolutions before closing
  its scan flow. Duplicate `PendingIntent` request codes fixed
  (`NotificationHelper.kt`) that silently dropped navigation flags on one
  of two "open app" notifications. `POST_NOTIFICATIONS` runtime request
  added for API 33+. The Global E-STOP widget gained a 15s timeout instead
  of hanging forever if the robot roster never loads. `MjpegPlayer.kt`'s
  stream parser stopped treating a single corrupt JPEG frame the same as
  end-of-stream, switched from per-byte-boxed `List<Byte>` accumulation to
  a real reused `ByteArray`, and now cancels the underlying OkHttp `Call`
  (not just the coroutine) so exiting the camera screen doesn't leave a
  blocking read alive until the next network timeout.
- **Second triage pass** - Missing default-locale `e_stop` string resource
  added (existed in all 4 translations, not in English, causing a build
  warning). `docs/ARCHITECTURE.md` rewritten end-to-end to match the
  actual current source tree and behavior instead of a stale narrative of
  past discrepancies.

Every entry above was verified with `./gradlew compileDebugKotlin`/
`assembleDebug` (`JAVA_HOME` pointed at a JDK 21 install) returning
`BUILD SUCCESSFUL`, per session.
