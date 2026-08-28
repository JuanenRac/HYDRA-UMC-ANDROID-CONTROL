@echo off
setlocal EnableExtensions EnableDelayedExpansion
rem =============================================================================
rem HYDRA-UMC-ANDROID-CONTROL - prepare-github-release.bat
rem Produces a privately signed, stable GitHub Release APK without changing
rem the manifest version or CHANGELOG. The normal build script owns bumps.
rem Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
rem GPL-3.0 - see LICENSE
rem =============================================================================

echo =============================================================================
echo  HYDRA-UMC-ANDROID-CONTROL - GitHub Release preparation
echo  Mode      : READ-ONLY SIGNED RELEASE PACKAGE
echo  Author    : JuanenRac ^(Electro Hobby 3D^)
echo  Email     : electrohobby3d@gmail.com
echo  Copyright : ^(C^) 2026 JuanenRac
echo  License   : GPL-3.0 - see LICENSE
echo =============================================================================
echo.

if not exist "keystore.properties" (
  echo ERROR: keystore.properties is required for a publishable release.
  echo Copy keystore.properties.example and set private signing values locally.
  goto :failed
)

findstr /R /C:"^hydraUmcReleaseStoreFile=.[^=]*" "keystore.properties" >nul
if errorlevel 1 goto :missingSigning
findstr /R /C:"^hydraUmcReleaseStorePassword=.[^=]*" "keystore.properties" >nul
if errorlevel 1 goto :missingSigning
findstr /R /C:"^hydraUmcReleaseKeyAlias=.[^=]*" "keystore.properties" >nul
if errorlevel 1 goto :missingSigning
findstr /R /C:"^hydraUmcReleaseKeyPassword=.[^=]*" "keystore.properties" >nul
if errorlevel 1 goto :missingSigning

echo [1/3] Building current manifest version with the private signing key...
call gradlew.bat :app:assembleRelease -PhydraUmcReadOnly=true
if errorlevel 1 goto :failed

set "APK=app\build\outputs\apk\release\app-release.apk"
if not exist "%APK%" (
  echo ERROR: Gradle did not produce %APK%.
  goto :failed
)

echo [2/3] Copying the exact GitHub Release asset name...
if not exist "dist" mkdir "dist"
copy /Y "%APK%" "dist\HYDRA-UMC-ANDROID-CONTROL-release.apk" >nul
if errorlevel 1 goto :failed

echo [3/3] Release artifact ready for a draft GitHub Release:
echo       dist\HYDRA-UMC-ANDROID-CONTROL-release.apk
echo.
echo Create a stable vMAJOR.MINOR.PATCH GitHub Release and attach that exact file.
echo Do not publish a debug-signed APK: Android will reject it as an update.
goto :done

:missingSigning
echo ERROR: keystore.properties is missing one or more private signing values.
goto :failed

:failed
echo.
echo GITHUB RELEASE PREPARATION FAILED.
set "EXIT_CODE=1"
goto :end

:done
echo.
echo GITHUB RELEASE PREPARATION COMPLETED.
set "EXIT_CODE=0"

:end
pause
exit /b %EXIT_CODE%
