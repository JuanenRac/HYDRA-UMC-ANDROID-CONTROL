#!/usr/bin/env python3
# =============================================================================
# HYDRA-UMC-ANDROID-CONTROL - bump_version_code.py
# Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
# GPL-3.0 - see LICENSE
# =============================================================================
"""Increments Android's own versionCode counter in app/version.properties.

versionCode is a separate, simple monotonic counter Android requires to
strictly increase across every build that ever ships - it is not part of
the MAJOR.MINOR.PATCH odometer scheme bump_manifest_version.py tracks (and
that script is intentionally generic/shared across the whole ecosystem, so
this Android-specific counter is not its job - see its own docstring).

Run from build-android.sh/build-android.bat, alongside
bump_manifest_version.py, before Gradle runs - Gradle itself only reads
version.properties for a real build (it runs with -PhydraUmcReadOnly=true,
see app/build.gradle.kts), it never writes to it.
"""
from __future__ import annotations

import re
from pathlib import Path

VERSION_PROPS = Path(__file__).resolve().parent / "app" / "version.properties"


def main() -> int:
    text = VERSION_PROPS.read_text(encoding="utf-8")
    match = re.search(r"(?m)^versionCode=(\d+)\s*$", text)
    if match is None:
        print("ERROR: versionCode=<number> not found in app/version.properties")
        return 1
    old = int(match.group(1))
    new = old + 1
    text = re.sub(r"(?m)^versionCode=\d+\s*$", f"versionCode={new}", text)
    VERSION_PROPS.write_text(text, encoding="utf-8")
    print(f"versionCode: {old} -> {new}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
