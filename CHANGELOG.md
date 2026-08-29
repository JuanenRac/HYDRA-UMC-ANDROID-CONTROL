# Changelog

All notable changes to HYDRA-UMC CONTROL (Android) are summarized here.
This public changelog records release-relevant work rather than a
session-by-session diary.

Version numbers below follow the ecosystem-wide auto-bump policy described
in [README.md](README.md#-versioning). Entries recorded before that policy
existed are grouped under the pre-policy version `0.0.0` the repo carried
at the time.

## [Unreleased] - Paired Watch relay and language resources

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

## [0.3.2] - Real diagnostics for the still-open "3D viewport shows no robot" report

- `ThreeDScreen.kt`'s embedded WebView had no error surface at all - a
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
