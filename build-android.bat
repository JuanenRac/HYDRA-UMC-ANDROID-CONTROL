@echo off
setlocal

echo =================================================
echo    HYDRA-UMC CONTROL - ANDROID DEPLOYMENT TOOL
echo =================================================
echo.

:: cd into the script's own directory - gradlew.bat and app\build\... are
:: paths relative to the repo root, not to whatever directory this
:: script happens to be invoked from.
cd /d "%~dp0"

:: The Gradle project lives at the repo root (app/ is the Android module).
if not exist "gradlew.bat" (
    echo [X] Error: gradlew.bat not found in %CD%.
    echo The Gradle wrapper (gradlew, gradlew.bat, gradle\wrapper\*) must be
    echo committed in the repo - if missing, please re-clone or restore these files.
    pause
    exit /b 1
)

:: Early Android SDK check - without this, Gradle fails with an unclear
:: ("SDK location not found") error. Android Studio generates
:: local.properties automatically when it opens the project; building
:: from a terminal without ever having opened it there needs
:: ANDROID_HOME/ANDROID_SDK_ROOT instead.
if not exist "local.properties" if "%ANDROID_HOME%"=="" if "%ANDROID_SDK_ROOT%"=="" (
    echo [!] Attention: local.properties not found and ANDROID_HOME/ANDROID_SDK_ROOT
    echo     environment variables are not set. Gradle will likely fail as it
    echo     won't know where the Android SDK is located.
    echo     Simplest solution: open this project once with Android Studio
    echo     (generates local.properties automatically), or define ANDROID_HOME
    echo     pointing to your SDK installation before retrying.
    echo.
)

:: AGP 9.3.1 (build.gradle.kts) needs JDK 21+ to run Gradle - a plain
:: JDK 8 (what many Windows machines have by default) isn't enough, and
:: the real Gradle error if you don't have it ("no variants... compatible
:: with Java 8") never mentions the JDK at all, so this is checked here
:: up front instead.
:: This check uses findstr to look for "1.8", "1.7", etc. or "11.", "17.", "20."
java -version 2>&1 | findstr /r /c:"1\.[5-8]\." /c:" 1[0-9]\." /c:" 20\." >nul
if %ERRORLEVEL% equ 0 (
    echo [!] Attention: The active JDK appears to be older than JDK 21.
    echo     AGP 9.3.1 requires JDK 21 or higher to run Gradle.
    echo     Please install JDK 21+ (or use the one bundled with Android Studio in
    echo     ...\Android Studio\jbr) and define JAVA_HOME pointing to it.
    echo.
)

echo [1/3] Building application (APK Debug)...
:: Explicit path instead of a bare "call gradlew.bat" - some Windows
:: environments (NoDefaultCurrentDirectoryInExePath policy) don't
:: resolve a command name without a path against the current directory.
call ".\gradlew.bat" assembleDebug

:: Check whether the build failed
if %ERRORLEVEL% neq 0 (
    echo.
    echo [X] Error: APK build failed. Please check Gradle errors.
    pause
    exit /b %ERRORLEVEL%
)
echo [OK] APK generated successfully.
echo.

echo [2/3] Searching for connected devices via USB/WiFi...
:: Check whether adb is on PATH
where adb >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [!] Attention: 'adb' command (Android Debug Bridge) not found.
    echo The APK was generated correctly at the following path:
    echo app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo Please install ADB or transfer the APK file manually to your device.
    pause
    exit /b 1
)

adb devices
echo.

echo [3/3] Installing HYDRA-UMC on the device...
:: Install the APK (-r replaces an existing install, -d allows a downgrade)
adb install -r -d app\build\outputs\apk\debug\app-debug.apk

if %ERRORLEVEL% equ 0 (
    echo.
    echo [SUCCESS] The application was installed correctly on your device.
    echo You can now disconnect the cable and open HYDRA-UMC Control on the phone.
) else (
    echo.
    echo [X] Error during installation.
    echo Make sure that:
    echo  1. Your phone is connected to the PC via cable.
    echo  2. 'Developer options' are enabled on the device.
    echo  3. 'USB debugging' is enabled.
    echo  4. You have accepted the "Allow debugging" prompt on your phone screen.
)

echo.
pause
