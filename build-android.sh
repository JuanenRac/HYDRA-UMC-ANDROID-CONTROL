#!/bin/bash

echo "================================================="
echo "   HYDRA-UMC CONTROL - ANDROID DEPLOYMENT TOOL    "
echo "================================================="
echo ""

# cd into the script's own directory
cd "$(dirname "$0")" || exit 1

# The Gradle project lives at the repo root
if [ ! -f "./gradlew" ]; then
    echo "❌ Error: ./gradlew not found in $(pwd)."
    echo "Please ensure the Gradle wrapper files are present in the repository."
    exit 1
fi

# Early Android SDK check
if [ ! -f "./local.properties" ] && [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    echo "⚠️  Attention: local.properties not found and environment variables not set."
    echo "   Gradle will likely fail as it won't know where the Android SDK is."
    echo "   Tip: Open this project once in Android Studio to generate local.properties."
    echo ""
fi

# AGP 9.3.1 needs JDK 21+
if java -version 2>&1 | grep -qE '1\.[5-8]\.| (1[0-9]|20)\.'; then
    echo "⚠️  Attention: The active JDK appears to be older than JDK 21."
    echo "   AGP 9.3.1 requires JDK 21 or higher to run Gradle."
    echo "   Please install JDK 21+ or use the one bundled with Android Studio."
    echo ""
fi

echo "[1/3] 🛠️  Building application (APK Debug)..."
chmod +x gradlew
./gradlew assembleDebug

if [ $? -ne 0 ]; then
    echo "❌ Error: APK build failed. Please check the logs above."
    exit 1
fi
echo "✅ APK generated successfully."
echo ""

echo "[2/3] 📱 Searching for connected devices via USB/WiFi..."
if ! command -v adb &> /dev/null; then
    echo "⚠️  Attention: 'adb' command not found."
    echo "The APK was generated at: app/build/outputs/apk/debug/app-debug.apk"
    echo "Install Android Platform Tools or transfer the APK manually."
    exit 1
fi

adb devices
echo ""

echo "[3/3] 🚀 Installing HYDRA-UMC on the device..."
adb install -r -d app/build/outputs/apk/debug/app-debug.apk

if [ $? -eq 0 ]; then
    echo ""
    echo "✨ SUCCESS! The application was installed correctly on your device."
    echo "You can now open HYDRA-UMC Control on your phone."
else
    echo ""
    echo "❌ Error during installation."
    echo "Make sure:"
    echo " 1. Your phone is connected via cable."
    echo " 2. Developer options are enabled."
    echo " 3. USB debugging is enabled and authorized."
fi
