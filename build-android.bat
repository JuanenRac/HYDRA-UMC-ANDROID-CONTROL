@echo off
setlocal

echo =================================================
echo    HYDRA-UMC STUDIO - ANDROID DEPLOYMENT TOOL    
echo =================================================
echo.

:: El proyecto Gradle vive en la raiz del repo (app/ es el modulo Android) -
:: ya no hay una carpeta android-app/ separada, se consolido en agosto 2026.
echo [1/3] Construyendo aplicacion (APK Debug)...
call gradlew.bat assembleDebug

:: Comprobar si falló la compilación
if %ERRORLEVEL% neq 0 (
    echo.
    echo [X] Error: La compilacion del APK ha fallado. Revisar errores de Gradle.
    pause
    exit /b %ERRORLEVEL%
)
echo [OK] APK generado correctamente.
echo.

echo [2/3] Buscando dispositivos conectados por USB/WiFi...
:: Comprobar si adb está en el PATH
where adb >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [!] Atencion: No se ha encontrado el comando 'adb' (Android Debug Bridge).
    echo El APK se ha generado correctamente en la siguiente ruta:
    echo app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo Instala ADB o pasa el archivo APK manualmente a tu movil.
    pause
    exit /b 1
)

adb devices
echo.

echo [3/3] Instalando HYDRA-UMC en el dispositivo...
:: Instalar el APK (-r reemplaza si ya existe, -d permite downgrade)
adb install -r -d app\build\outputs\apk\debug\app-debug.apk

if %ERRORLEVEL% equ 0 (
    echo.
    echo [EXITO] La aplicacion se ha instalado correctamente en tu movil.
    echo Ya puedes desconectar el cable y abrir HYDRA-UMC Control en el telefono.
) else (
    echo.
    echo [X] Error en la instalacion.
    echo Asegurate de que:
    echo  1. Tu telefono esta conectado al PC con cable.
    echo  2. Tienes activadas las 'Opciones de desarrollador' en el movil.
    echo  3. Tienes activada la 'Depuracion por USB'.
    echo  4. Has aceptado el mensaje de "Permitir depuracion" en la pantalla de tu telefono.
)

echo.
pause
