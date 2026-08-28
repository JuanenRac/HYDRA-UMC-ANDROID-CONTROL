@echo off
REM HYDRA_UMC_SCRIPT_STANDARD_HEADER_BEGIN
REM *****************************************************************************
REM Project   : HYDRA-UMC-ANDROID-CONTROL
REM Script    : build-android.bat
REM Purpose   : Incremental Android build and optional connected-device installation workflow.
REM Author    : JuanenRac (Electro Hobby 3D)
REM Email     : electrohobby3d@gmail.com
REM Copyright : (C) 2026 JuanenRac
REM License   : GPL-3.0 - see LICENSE
REM *****************************************************************************
REM HYDRA_UMC_SCRIPT_STANDARD_HEADER_END
REM HYDRA_UMC_SCRIPT_STANDARD_BANNER_BEGIN
echo.
echo *****************************************************************************
echo * HYDRA-UMC-ANDROID-CONTROL - build-android.bat
echo * Mode      : INCREMENTAL BUILD
echo * Author    : JuanenRac (Electro Hobby 3D)
echo * Email     : electrohobby3d@gmail.com
echo * Copyright : (C) 2026 JuanenRac
echo * License   : GPL-3.0 - see LICENSE
echo * ------------------------------------------------------------------------- *
echo * 1. Increment the project version and synchronise its manifest.
echo * 2. Run this project's declared build, verification and packaging commands.
echo * 3. Report the result and keep an interactive terminal open.
echo *****************************************************************************
echo.
REM HYDRA_UMC_SCRIPT_STANDARD_BANNER_END
setlocal
REM HYDRA_UMC_SCRIPT_STANDARD_VERSION_CAPTURE_BEFORE
for /f "usebackq delims=" %%V in (`python -c "import json; print(json.load(open(r'%~dp0hydra-umc.project.json', encoding='utf-8'))['version'])"`) do set "HYDRA_UMC_VERSION_BEFORE=%%V"
python "%~dp0bump_manifest_version.py"
if errorlevel 1 ( echo VERSION BUMP FAILED. & pause & exit /b 1 )
REM HYDRA_UMC_SCRIPT_STANDARD_VERSION_CAPTURE_AFTER
for /f "usebackq delims=" %%V in (`python -c "import json; print(json.load(open(r'%~dp0hydra-umc.project.json', encoding='utf-8'))['version'])"`) do set "HYDRA_UMC_VERSION_AFTER=%%V"
if not defined HYDRA_UMC_VERSION_BEFORE set "HYDRA_UMC_VERSION_BEFORE=unknown"
if not defined HYDRA_UMC_VERSION_AFTER set "HYDRA_UMC_VERSION_AFTER=unknown"
echo.
echo *****************************************************************************
echo * VERSION INCREMENT COMPLETED
echo * v%HYDRA_UMC_VERSION_BEFORE% ^> v%HYDRA_UMC_VERSION_AFTER%
echo * Project manifest has been synchronised by the project build flow.
echo *****************************************************************************
echo.
echo.
python "%~dp0bump_version_code.py"
if errorlevel 1 ( echo VERSION BUMP FAILED. & pause & exit /b 1 )
cd /d "%~dp0"

:: Check for gradlew.bat
if not exist "gradlew.bat" (
    echo [X] Error: gradlew.bat not found in %CD%.
    echo Please ensure the Gradle wrapper files are present in the repository.
    pause
    exit /b 1
)

:: Early Android SDK check
if not exist "local.properties" if "%ANDROID_HOME%"=="" if "%ANDROID_SDK_ROOT%"=="" (
    echo [!] Attention: local.properties not found and environment variables not set.
    echo     Gradle will likely fail as it won't know where the Android SDK is.
    echo     Tip: Open this project once in Android Studio to generate local.properties.
    echo.
)

:: AGP 9.3.1 needs JDK 21+
java -version 2>&1 | findstr /r /c:"1\.[5-8]\." /c:" 1[0-9]\." /c:" 20\." >nul
if %ERRORLEVEL% equ 0 (
    echo [!] Attention: The active JDK appears to be older than JDK 21.
    echo     AGP 9.3.1 requires JDK 21 or higher to run Gradle.
    echo     Please install JDK 21+ or use the one bundled with Android Studio.
    echo.
)

echo [1/3] Building application (APK Debug)...
:: -PhydraUmcReadOnly=true / HYDRA_UMC_CI=1 tell app/build.gradle.kts not to
:: bump version.properties itself - the two scripts above already did the
:: one real bump for this build; without this flag Gradle's own
:: configuration-time bump would double it (the same flag build-test.bat
:: uses for its compile-only, non-mutating CI check).
set HYDRA_UMC_CI=1
call ".\gradlew.bat" assembleDebug -PhydraUmcReadOnly=true

if %ERRORLEVEL% neq 0 (
    echo.
    echo [X] Error: APK build failed. Please check the logs above.
    pause
    exit /b %ERRORLEVEL%
)
echo [OK] APK generated successfully.
echo.

echo [2/3] Searching for connected devices via USB/WiFi...
where adb >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [!] Attention: 'adb' command not found.
    echo The APK was generated at: app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo Install Android Platform Tools or transfer the APK manually.
    pause
    exit /b 1
)

adb devices
echo.

echo [3/3] Installing HYDRA-UMC on the device...
adb install -r -d app\build\outputs\apk\debug\app-debug.apk

if %ERRORLEVEL% equ 0 (
    echo.
    echo [SUCCESS] The application was installed correctly on your device.
    echo You can now open HYDRA-UMC Control on your phone.
) else (
    echo.
    echo [X] Error during installation.
    echo Make sure:
    echo  1. Your phone is connected via cable.
    echo  2. Developer options are enabled.
    echo  3. USB debugging is enabled and authorized.
)

echo.
pause
