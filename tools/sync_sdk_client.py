#!/usr/bin/env python3
# =============================================================================
# HYDRA-UMC-ANDROID-CONTROL - tools/sync_sdk_client.py
# Copyright (C) 2026 JuanenRac (Electro Hobby 3D)
# GPL-3.0-or-later - see LICENSE
# =============================================================================
"""Keeps app/src/main/java/hydra/umc/client/HydraServerClient.kt identical to the typed server client the SDK generates from the server contract.

    python tools/sync_sdk_client.py            # copy the SDK file over the vendored one
    python tools/sync_sdk_client.py --check    # fail when the vendored copy differs; skipped when no SDK checkout is found

The SDK is the folder named by HYDRA_UMC_SDK_DIR (the continuous integration fetches the pinned tag there) or the one next to this repository.
The file is a pure Kotlin file (no Android and no JSON library): every object of the contract is a data class with fromJson and toJson, and the
transport is a `fun interface` the app implements with its own HTTP client (see `OkHttpHydraTransport`).
"""
from __future__ import annotations

import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VENDORED = ROOT / "app" / "src" / "main" / "java" / "hydra" / "umc" / "client" / "HydraServerClient.kt"
RELATIVE = Path("generated") / "HydraServerClient.kt"


def _source() -> Path:
    env = os.environ.get("HYDRA_UMC_SDK_DIR")
    return (Path(env) if env else ROOT.parent / "HYDRA-UMC-SDK") / RELATIVE


def _text(path: Path) -> bytes:
    return path.read_bytes().replace(b"\r\n", b"\n")


def main(argv: list[str]) -> int:
    source = _source()
    if not source.is_file():
        print("SKIP: no HYDRA-UMC-SDK checkout with the generated Kotlin server client was found")
        return 0
    if "--check" in argv:
        if not VENDORED.is_file() or _text(VENDORED) != _text(source):
            print("FAIL: app/src/main/java/hydra/umc/client/HydraServerClient.kt differs from the SDK file; run python tools/sync_sdk_client.py", file=sys.stderr)
            return 1
        print("PASS: the vendored Kotlin server client equals the SDK file")
        return 0
    VENDORED.parent.mkdir(parents=True, exist_ok=True)
    VENDORED.write_bytes(_text(source))
    print("copied the SDK Kotlin server client")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
