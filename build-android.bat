@echo off
setlocal

echo =================================================
echo    HYDRA-UMC STUDIO - ANDROID DEPLOYMENT TOOL
echo =================================================
echo.

:: cd into the script's own directory - gradlew.bat and app\build\... are
:: paths relative to the repo root, not to whatever directory this
:: script happens to be invoked from.
cd /d "%~dp0"

:: The Gradle project lives at the repo root (app/ is the Android module) -
:: there's no separate android-app/ folder anymore, consolidated in August 2026.
if not exist "gradlew.bat" (
    echo [X] Error: no se encuentra gradlew.bat en %CD%.
    echo El wrapper de Gradle ^(gradlew, gradlew.bat, gradle\wrapper\*^) debe estar
    echo commiteado en el repo - si falta, vuelve a clonar o restaura esos archivos.
    pause
    exit /b 1
)

:: Early Android SDK check - without this, Gradle fails with an unclear
:: ^("SDK location not found"^) error. Android Studio generates
:: local.properties automatically when it opens the project; building
:: from a terminal without ever having opened it there needs
:: ANDROID_HOME/ANDROID_SDK_ROOT instead.
if not exist "local.properties" if "%ANDROID_HOME%"=="" if "%ANDROID_SDK_ROOT%"=="" (
    echo [!] Atencion: no se encuentra local.properties ni la variable de entorno
    echo     ANDROID_HOME/ANDROID_SDK_ROOT. Gradle probablemente falle al no saber
    echo     donde esta el Android SDK.
    echo     Solucion mas simple: abre este proyecto una vez con Android Studio
    echo     ^(genera local.properties automaticamente^), o define ANDROID_HOME
    echo     apuntando a tu instalacion del SDK antes de reintentar.
    echo.
)

:: AGP 8.2.0 (build.gradle.kts) needs JDK 17+ to run Gradle - a plain
:: JDK 8 (what many Windows machines have by default) isn't enough, and
:: the real Gradle error if you don't have it ^("no variants... compatible
:: with Java 8"^) never mentions the JDK at all, so this is checked here
:: up front instead.
java -version 2>&1 | findstr /r "1\.[5-8]\." >nul
if %ERRORLEVEL% equ 0 (
    echo [!] Atencion: el JDK activo parece ser Java 8 o anterior.
    echo     AGP 8.2.0 necesita JDK 17 o superior para ejecutar Gradle.
    echo     Instala un JDK 17+ ^(o usa el que trae Android Studio en
    echo     ...\Android Studio\jbr^) y define JAVA_HOME apuntando a el.
    echo.
)

echo [1/3] Construyendo aplicacion (APK Debug)...
:: Explicit path instead of a bare "call gradlew.bat" - some Windows
:: environments (NoDefaultCurrentDirectoryInExePath policy) don't
:: resolve a command name without a path against the current directory.
call ".\gradlew.bat" assembleDebug

:: Check whether the build failed
if %ERRORLEVEL% neq 0 (
    echo.
    echo [X] Error: La compilacion del APK ha fallado. Revisar errores de Gradle.
    pause
    exit /b %ERRORLEVEL%
)
echo [OK] APK generado correctamente.
echo.

echo [2/3] Buscando dispositivos conectados por USB/WiFi...
:: Check whether adb is on PATH
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
:: Install the APK (-r replaces an existing install, -d allows a downgrade)
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
