#!/bin/bash

echo "================================================="
echo "   HYDRA-UMC STUDIO - ANDROID DEPLOYMENT TOOL    "
echo "================================================="
echo ""

# El proyecto Gradle vive en la raíz del repo (app/ es el módulo Android) -
# ya no hay una carpeta android-app/ separada, se consolidó en agosto 2026.
echo "[1/3] 🛠️  Compilando aplicación (APK Debug)..."
# Dar permisos de ejecución a gradlew por si no los tiene
chmod +x gradlew
./gradlew assembleDebug

# Comprobar si falló la compilación
if [ $? -ne 0 ]; then
    echo "❌ Error: La compilación del APK ha fallado."
    exit 1
fi
echo "✅ APK generado correctamente."
echo ""

echo "[2/3] 📱 Buscando dispositivos conectados por USB/WiFi..."
# Comprobar si adb está instalado
if ! command -v adb &> /dev/null; then
    echo "⚠️  Atención: No se ha encontrado el comando 'adb'."
    echo "El APK se ha generado en: app/build/outputs/apk/debug/app-debug.apk"
    echo "Instala Android Platform Tools (adb) para instalarlo automáticamente."
    exit 1
fi

adb devices
echo ""

echo "[3/3] 🚀 Instalando HYDRA-UMC en el dispositivo..."
# Instalar el APK (-r reemplaza si ya existe, -d permite downgrade)
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
