#!/bin/bash

echo "================================================="
echo "   HYDRA-UMC CONTROL - ANDROID DEPLOYMENT TOOL    "
echo "================================================="
echo ""

# cd into the script's own directory - gradlew/gradlew.bat and
# app/build/... are paths relative to the repo root, not to whatever
# directory this script happens to be invoked from.
cd "$(dirname "$0")" || exit 1

# The Gradle project lives at the repo root (app/ is the Android module).
if [ ! -f "./gradlew" ]; then
    echo "❌ Error: ./gradlew not found in $(pwd)."
    echo "The Gradle wrapper (gradlew, gradlew.bat, gradle/wrapper/*) must be"
    echo "committed in the repo - if missing, please re-clone or restore these files."
    exit 1
fi

# Early Android SDK check - without this, Gradle fails with an unclear
# "SDK location not found" error. Android Studio generates
# local.properties automatically when it opens the project; building from
# a terminal without ever having opened it there needs ANDROID_HOME/
# ANDROID_SDK_ROOT instead.
if [ ! -f "./local.properties" ] && [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    echo "⚠️  Attention: local.properties not found and ANDROID_HOME/ANDROID_SDK_ROOT"
    echo "   environment variables are not set. Gradle will likely fail as it"
    echo "   won't know where the Android SDK is located."
    echo "   Simplest solution: open this project once with Android Studio"
    echo "   (generates local.properties automatically), or export ANDROID_HOME"
    echo "   pointing to your SDK installation before retrying."
    echo ""
fi

# AGP 9.3.1 (build.gradle.kts) needs JDK 21+ to run Gradle - the real
# Gradle error if you don't have it ("no variants... compatible with
# Java 8" or similar) never mentions the JDK clearly, so this is checked
# here up front instead of letting it fail with that cryptic message.
# This regex catches 1.x (Java 8 and below) and 10-20.
if java -version 2>&1 | grep -qE '1\.[5-8]\.| (1[0-9]|20)\.'; then
    echo "⚠️  Attention: The active JDK appears to be older than JDK 21."
    echo "   AGP 9.3.1 requires JDK 21 or higher to run Gradle."
    echo "   Please install JDK 21+ (or use the one bundled with Android Studio in"
    echo "   .../Android Studio/jbr) and export JAVA_HOME pointing to it."
    echo ""
fi

echo "[1/3] 🛠️  Building application (APK Debug)..."
# Make gradlew executable in case it isn't already
chmod +x gradlew
./gradlew assembleDebug

# Check whether the build failed
if [ $? -ne 0 ]; then
    echo "❌ Error: APK build failed."
    exit 1
fi
echo "✅ APK generated successfully."
echo ""

echo "[2/3] 📱 Searching for connected devices via USB/WiFi..."
# Check whether adb is installed
if ! command -v adb &> /dev/null; then
    echo "⚠️  Attention: 'adb' command not found."
    echo "The APK was generated at: app/build/outputs/apk/debug/app-debug.apk"
    echo "Please install Android Platform Tools (adb) to install it automatically."
    exit 1
fi

adb devices
echo ""

echo "[3/3] 🚀 Installing HYDRA-UMC on the device..."
# Install the APK (-r replaces an existing install, -d allows a downgrade)
adb install -r -d app/build/outputs/apk/debug/app-debug.apk

if [ $? -eq 0 ]; then
    echo ""
    echo "✨ SUCCESS! The application was installed correctly on your device."
    echo "You can now disconnect the cable and open HYDRA-UMC Control."
else
    echo ""
    echo "❌ Error during installation."
    echo "Make sure that:"
    echo " 1. Your phone is connected to the PC."
    echo " 2. 'Developer options' are enabled."
    echo " 3. 'USB debugging' is enabled."
fi
