#!/usr/bin/env bash
# =============================================================================
# HYDRA-UMC-ANDROID-CONTROL - prepare-github-release.sh
# Produces a privately signed, stable GitHub Release APK without changing the
# manifest version or CHANGELOG. The normal build script owns version bumps.
# Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
# GPL-3.0 - see LICENSE
# =============================================================================
set -euo pipefail

echo "============================================================================="
echo " HYDRA-UMC-ANDROID-CONTROL - GitHub Release preparation"
echo " Mode      : READ-ONLY SIGNED RELEASE PACKAGE"
echo " Author    : JuanenRac (Electro Hobby 3D)"
echo " Email     : electrohobby3d@gmail.com"
echo " Copyright : (C) 2026 JuanenRac"
echo " License   : GPL-3.0 - see LICENSE"
echo "============================================================================="

if [[ ! -f keystore.properties ]]; then
  echo "ERROR: keystore.properties is required for a publishable release." >&2
  echo "Copy keystore.properties.example and set private signing values locally." >&2
  exit 1
fi

required_keys=(
  hydraUmcReleaseStoreFile
  hydraUmcReleaseStorePassword
  hydraUmcReleaseKeyAlias
  hydraUmcReleaseKeyPassword
)
for key in "${required_keys[@]}"; do
  if ! grep -Eq "^${key}=.+$" keystore.properties; then
    echo "ERROR: keystore.properties is missing a value for ${key}." >&2
    exit 1
  fi
done

echo "[1/3] Building current manifest version with the private signing key..."
./gradlew :app:assembleRelease -PhydraUmcReadOnly=true

apk="app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$apk" ]]; then
  echo "ERROR: Gradle did not produce $apk." >&2
  exit 1
fi

echo "[2/3] Copying the exact GitHub Release asset name..."
mkdir -p dist
cp "$apk" "dist/HYDRA-UMC-ANDROID-CONTROL-release.apk"

echo "[3/3] Release artifact ready for a draft GitHub Release:"
echo "      dist/HYDRA-UMC-ANDROID-CONTROL-release.apk"
echo "Create a stable vMAJOR.MINOR.PATCH GitHub Release and attach that exact file."
echo "Do not publish a debug-signed APK: Android will reject it as an update."
