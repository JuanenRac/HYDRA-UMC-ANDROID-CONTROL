# Changelog

All notable changes to HYDRA-UMC CONTROL (Android) are summarized here.
Full session-by-session detail (including dates) lives in the private,
unpublished `SONNET/HYDRA-UMC-ANDROID-CONTROL/auditoria_historial.txt` -
this file is public, so it intentionally omits calendar dates.

Version numbers below follow the ecosystem-wide auto-bump policy described
in [README.md](README.md#-versioning). Entries recorded before that policy
existed are grouped under the pre-policy version `1.0.0` the repo carried
at the time.

## [Unreleased policy]

- **Automatic version bump on every real build.** `app/build.gradle.kts`
  now reads `app/version.properties` at Gradle **configuration** time -
  which runs on every real build (`assembleDebug`, `compileDebugKotlin`,
  etc. all evaluate the script) - bumps it, and rewrites the file before
  using the new numbers for `versionCode`/`versionName`: patch +1,
  odometer-style carry into minor once patch would exceed 9 (`1.0.9` ->
  `1.1.0`), and `versionCode` a plain monotonic counter (+1, no carry -
  Android requires it to strictly increase across every build that ever
  ships). No manual version editing from here on. `versionName` normalized
  from the old 2-part `"1.0"` to the ecosystem's standard 3-part `"1.0.0"`
  as the starting point.
- `AboutDialog.kt` now reads the live `BuildConfig.VERSION_NAME` (new
  `buildFeatures.buildConfig = true`) via a `%1$s`-formatted
  `app_version` string instead of a hardcoded version baked into
  `strings.xml` per language - previously `app_version` was a literal
  `"Version 1.0.0"` that would have gone stale the moment the first
  auto-bumped build shipped.
- `build-android.sh`/`build-android.bat` now print a real startup banner
  (project name, what the script does, author, license) matching the
  copyright header already used across this repo's `.kt` files, and pause
  for a keypress before closing on both success and failure so a
  double-clicked window doesn't vanish before the result is readable.
- This file added, seeded from the real project history below.

## 1.0.0 and prior (pre-versioning-policy history)

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
