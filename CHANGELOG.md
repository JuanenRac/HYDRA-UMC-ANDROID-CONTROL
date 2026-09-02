# Changelog

All notable changes to HYDRA-UMC CONTROL (Android) are summarized here.
This public changelog records release-relevant work rather than a
session-by-session diary.

Version numbers below follow the ecosystem-wide auto-bump policy described
in [README.md](README.md#-versioning). Entries recorded before that policy
existed are grouped under the pre-policy version `0.0.0` the repo carried
at the time.

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
  deliberate, already-audited design choice, not an obvious bug.

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

## [Unreleased policy]

- **Automatic version bump on every real build.** `app/build.gradle.kts`
  now reads `app/version.properties` at Gradle **configuration** time -
  which runs on every real build (`assembleDebug`, `compileDebugKotlin`,
  etc. all evaluate the script) - bumps it, and rewrites the file before
  using the new numbers for `versionCode`/`versionName`: patch +1,
  odometer-style carry into minor once patch would exceed 9 (`0.0.9` ->
  `0.1.0`), and `versionCode` a plain monotonic counter (+1, no carry -
  Android requires it to strictly increase across every build that ever
  ships). No manual version editing from here on. `versionName` normalized
  from the old 2-part `"0.0"` to the ecosystem's standard 3-part `"0.0.0"`
  as the starting point.
- `AboutDialog.kt` now reads the live `BuildConfig.VERSION_NAME` (new
  `buildFeatures.buildConfig = true`) via a `%1$s`-formatted
  `app_version` string instead of a hardcoded version baked into
  `strings.xml` per language - previously `app_version` was a literal
  `"Version 0.0.0"` that would have gone stale the moment the first
  auto-bumped build shipped.
- `build-android.sh`/`build-android.bat` now print a real startup banner
  (project name, what the script does, author, license) matching the
  copyright header already used across this repo's `.kt` files, and pause
  for a keypress before closing on both success and failure so a
  double-clicked window doesn't vanish before the result is readable.
- This file added, seeded from the real project history below.

## 0.0.0 and prior (pre-versioning-policy history)

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
  a licensing note (code GPL-3.0, docs CC BY-SA 4.0), matching the pattern
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
