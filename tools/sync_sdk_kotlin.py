#!/usr/bin/env python3
# =============================================================================
# HYDRA-UMC-ANDROID-CONTROL - tools/sync_sdk_kotlin.py
# Copyright (C) 2026 JuanenRac (Electro Hobby 3D)
# GPL-3.0-or-later - see LICENSE
# =============================================================================
"""Keeps the Kotlin code the HYDRA-UMC apps share identical to the one in the SDK (clients/kotlin_app_common).

    python tools/sync_sdk_kotlin.py            # copy the SDK sources next to this repository (or in HYDRA_UMC_SDK_DIR) over the vendored ones and write the lock
    python tools/sync_sdk_kotlin.py --check    # fail when a vendored file differs from the lock, or the SDK differs from the lock when it is at hand

The sources live once, in the SDK; this app compiles a copy of them (app/src/main/java/com/hydraumc/common and the matching tests) because
a pinned copy builds without the SDK checkout. `docs/sdk_kotlin_common.lock.json` holds the SHA-256 of every file, so the check also works on a plain CI
checkout, where the SDK is not next to this repository.
"""
from __future__ import annotations

import hashlib
import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ["HYDRA_UMC_SDK_DIR"]) if os.environ.get("HYDRA_UMC_SDK_DIR") else ROOT.parent / "HYDRA-UMC-SDK"
SOURCE = SDK / "clients" / "kotlin_app_common" / "src"
LOCK = ROOT / "docs" / "sdk_kotlin_common.lock.json"
# SDK folder (under src/) -> folder of this app
TARGETS = {
    "main/kotlin/com/hydraumc/common": ROOT / "app" / "src" / "main" / "java" / "com" / "hydraumc" / "common",
    "test/kotlin/com/hydraumc/common": ROOT / "app" / "src" / "test" / "java" / "com" / "hydraumc" / "common",
}


def _digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def _sdk_files() -> dict[str, Path]:
    found: dict[str, Path] = {}
    for folder in TARGETS:
        for path in sorted((SOURCE / folder).glob("*.kt")):
            found[f"{folder}/{path.name}"] = path
    return found


def _vendored(key: str) -> Path:
    folder, name = key.rsplit("/", 1)
    return TARGETS[folder] / name


def main(argv: list[str]) -> int:
    if "--check" in argv:
        if not LOCK.is_file():
            print("FAIL: docs/sdk_kotlin_common.lock.json is missing; run python tools/sync_sdk_kotlin.py", file=sys.stderr)
            return 1
        locked: dict[str, str] = json.loads(LOCK.read_text(encoding="utf-8"))["files"]
        problems = []
        for key, digest in locked.items():
            copy = _vendored(key)
            if not copy.is_file():
                problems.append(f"{key} is missing from the app")
            elif _digest(copy) != digest:
                problems.append(f"{key} differs from the lock (edit it in the SDK, then run python tools/sync_sdk_kotlin.py)")
        for folder, target in TARGETS.items():
            for path in sorted(target.glob("*.kt")):
                if f"{folder}/{path.name}" not in locked:
                    problems.append(f"{folder}/{path.name} is in the app but not in the lock")
        if SOURCE.is_dir():
            for key, path in _sdk_files().items():
                if locked.get(key) != _digest(path):
                    problems.append(f"{key} in the SDK is not what the lock records; run python tools/sync_sdk_kotlin.py")
        if problems:
            print("FAIL: " + "; ".join(problems), file=sys.stderr)
            return 1
        print("PASS: the vendored Kotlin code equals the lock" + (" and the SDK" if SOURCE.is_dir() else " (the SDK is not at hand)"))
        return 0
    if not SOURCE.is_dir():
        print("SKIP: HYDRA-UMC-SDK is not next to this repository (or has no kotlin_app_common yet)")
        return 0
    files = _sdk_files()
    for target in TARGETS.values():
        target.mkdir(parents=True, exist_ok=True)
    for key, path in files.items():
        _vendored(key).write_bytes(path.read_bytes())
    LOCK.parent.mkdir(parents=True, exist_ok=True)
    LOCK.write_text(json.dumps({"files": {key: _digest(path) for key, path in files.items()}}, indent=2) + "\n", encoding="utf-8")
    print(f"copied {len(files)} Kotlin files and wrote the lock")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
