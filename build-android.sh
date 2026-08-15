#!/bin/bash

echo "================================================="
echo "   HYDRA-UMC STUDIO - ANDROID DEPLOYMENT TOOL    "
echo "================================================="
echo ""

# cd into the script's own directory - gradlew/gradlew.bat and
# app/build/... are paths relative to the repo root, not to whatever
# directory this script happens to be invoked from.
cd "$(dirname "$0")" || exit 1

# The Gradle project lives at the repo root (app/ is the Android module) -
# there's no separate android-app/ folder anymore, consolidated in August 2026.
if [ ! -f "./gradlew" ]; then
    echo "❌ Error: no se encuentra ./gradlew en $(pwd)."
    echo "El wrapper de Gradle (gradlew, gradlew.bat, gradle/wrapper/*) debe estar"
    echo "commiteado en el repo - si falta, vuelve a clonar o restaura esos archivos."
    exit 1
fi

# Early Android SDK check - without this, Gradle fails with an unclear
# "SDK location not found" error. Android Studio generates
# local.properties automatically when it opens the project; building from
# a terminal without ever having opened it there needs ANDROID_HOME/
# ANDROID_SDK_ROOT instead.
if [ ! -f "./local.properties" ] && [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    echo "⚠️  Atención: no se encuentra local.properties ni la variable de entorno"
    echo "   ANDROID_HOME/ANDROID_SDK_ROOT. Gradle probablemente falle al no saber"
    echo "   dónde está el Android SDK."
    echo "   Solución más simple: abre este proyecto una vez con Android Studio"
    echo "   (genera local.properties automáticamente), o exporta ANDROID_HOME"
    echo "   apuntando a tu instalación del SDK antes de reintentar."
    echo ""
fi

# AGP 8.1.0 (build.gradle.kts) needs JDK 17+ to run Gradle - the real
# Gradle error if you don't have it ("no variants... compatible with
# Java 8") never mentions the JDK at all, so this is checked here up
# front instead of letting it fail with that cryptic message.
if java -version 2>&1 | grep -qE '1\.[5-8]\.'; then
    echo "⚠️  Atención: el JDK activo parece ser Java 8 o anterior."
    echo "   AGP 8.1.0 necesita JDK 17 o superior para ejecutar Gradle."
    echo "   Instala un JDK 17+ (o usa el que trae Android Studio en"
    echo "   .../Android Studio/jbr) y exporta JAVA_HOME apuntando a él."
    echo ""
fi

echo "[1/3] 🛠️  Compilando aplicación (APK Debug)..."
# Make gradlew executable in case it isn't already
chmod +x gradlew
./gradlew assembleDebug

# Check whether the build failed
if [ $? -ne 0 ]; then
    echo "❌ Error: La compilación del APK ha fallado."
    exit 1
fi
echo "✅ APK generado correctamente."
echo ""

echo "[2/3] 📱 Buscando dispositivos conectados por USB/WiFi..."
# Check whether adb is installed
if ! command -v adb &> /dev/null; then
    echo "⚠️  Atención: No se ha encontrado el comando 'adb'."
    echo "El APK se ha generado en: app/build/outputs/apk/debug/app-debug.apk"
    echo "Instala Android Platform Tools (adb) para instalarlo automáticamente."
    exit 1
fi

adb devices
echo ""

echo "[3/3] 🚀 Instalando HYDRA-UMC en el dispositivo..."
# Install the APK (-r replaces an existing install, -d allows a downgrade)
adb install -r -d app/build/outputs/apk/debug/app-debug.apk

if [ $? -eq 0 ]; then
    echo ""
    echo "✨ ¡ÉXITO! La aplicación se ha instalado correctamente en tu móvil."
    echo "Ya puedes desconectar el cable y abrir HYDRA-UMC Control."
else
    echo ""
    echo "❌ Error en la instalación."
    echo "Asegúrate de que:"
    echo " 1. Tu teléfono está conectado al PC."
    echo " 2. Tienes activadas las 'Opciones de desarrollador'."
    echo " 3. Tienes activada la 'Depuración por USB'."
fi
