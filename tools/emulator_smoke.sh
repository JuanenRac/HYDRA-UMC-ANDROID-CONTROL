#!/usr/bin/env bash
# =============================================================================
# HYDRA-UMC-ANDROID-CONTROL - tools/emulator_smoke.sh
# Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
# GPL-3.0-or-later - see LICENSE
# =============================================================================
# Run by .github/workflows/android-emulator.yml inside a running Android emulator (adb is connected): installs the debug APK, starts the app, waits, and checks
# that the process is alive and that the log has no crash of the app. Takes a screenshot into $SHOTS (default ./emulator-shots). Needs no server: the app starts on
# its own sign-in screen. Usage: emulator_smoke.sh APK PACKAGE LABEL
set -uo pipefail
APK="${1:?apk}"
PACKAGE="${2:?package}"
LABEL="${3:-emulator}"
SHOTS="${SHOTS:-emulator-shots}"
FAIL=0
ok() { echo "PASS  $*"; }
bad() { echo "FAIL  $*"; FAIL=1; }

mkdir -p "$SHOTS"
echo "device: $(adb shell getprop ro.product.model | tr -d '\r') api $(adb shell getprop ro.build.version.sdk | tr -d '\r') abi $(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
adb logcat -c
if adb install -r "$APK" 2>&1 | tee /tmp/install.log | grep -q Success; then ok "the debug APK installs"; else bad "the APK did not install"; cat /tmp/install.log; fi
adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/tmp/monkey.log 2>&1
sleep 20
if [ -n "$(adb shell pidof "$PACKAGE" | tr -d '\r')" ]; then ok "the app is running 20 seconds after it started"; else bad "the app is not running"; fi
adb logcat -d >/tmp/logcat.txt 2>&1
if grep -E "FATAL EXCEPTION|Process: ${PACKAGE//./\\.}.*PID" /tmp/logcat.txt >/dev/null; then bad "the app crashed"; grep -A12 "FATAL EXCEPTION" /tmp/logcat.txt | head -n 30; else ok "no crash of the app in the log"; fi
if grep -E "ANR in ${PACKAGE//./\\.}" /tmp/logcat.txt >/dev/null; then bad "the app did not respond (ANR)"; else ok "no ANR"; fi
adb exec-out screencap -p >"$SHOTS/${LABEL}.png" 2>/dev/null && ok "screenshot saved as ${LABEL}.png" || echo "SKIP  no screenshot"
adb shell am force-stop "$PACKAGE"
echo "evidence=simulator (Android emulator on the CI runner, no robot, no server)"
exit "$FAIL"
