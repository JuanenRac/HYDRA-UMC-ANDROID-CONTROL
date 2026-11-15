#!/usr/bin/env python3
# =============================================================================
# HYDRA-UMC-ANDROID-CONTROL - tools/check_rest_routes.py
# Copyright (C) 2026 JuanenRac (Electro Hobby 3D)
# GPL-3.0-or-later - see LICENSE
# =============================================================================
"""Fixes the list of server routes this app calls and checks it against the server contract.

    python tools/check_rest_routes.py            # compare the sources with docs/rest_routes.json (and the contract when it is next to this repository)
    python tools/check_rest_routes.py --check    # the same, as the CI validator runs it
    python tools/check_rest_routes.py --record   # write docs/rest_routes.json from the sources (on purpose, after adding or dropping a route)

A route can only be added or dropped on purpose (record it again); when the shared contracts are checked out next to this repository, every route
must exist in the server contract, so an app cannot call a route the server does not have.

Two ways of calling a route exist: by hand (a string with the path in the sources) and through the typed client of the SDK
(app/src/main/java/hydra/umc/client/HydraServerClient.kt, carried by tools/sync_sdk_client.py). docs/typed_client_inventory.json says which is which: "typed" lists the
routes that go through the client, "by_hand" maps every route still written by hand to the reason it stays, so nothing is lost while the migration goes on. The vendored
client itself is the catalogue of the whole contract, not a call, and is not read as one. The relay of the server to the local services
(/api/ai/<service>/...) is one wildcard route that the inventory does not list path by path, so it is not compared.
"""
from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RECORDED = ROOT / "docs" / "rest_routes.json"
SOURCE_DIRS = ("app/src/main", "lib")
GENERATED_CLIENT = ROOT / "app" / "src" / "main" / "java" / "hydra" / "umc" / "client"
INVENTORY = ROOT / "docs" / "typed_client_inventory.json"
SOURCE_SUFFIXES = (".kt", ".dart", ".swift")
SDK_ROOT = Path(os.environ["HYDRA_UMC_SDK_DIR"]) if os.environ.get("HYDRA_UMC_SDK_DIR") else ROOT.parent / "HYDRA-UMC-SDK"
CONTRACT = SDK_ROOT / "contracts" / "openapi" / "v1" / "server.full.openapi.json"
STRING = re.compile(r'"([^"]*)"' + "|" + r"'([^']*)'")
INSIDE = re.compile(r"(/api/\S*)")


def normalize(raw: str) -> str:
    route = re.sub(r"\$\{[^}]*\}?|\\\([^)]*\)|\$\w+", "{p}", raw).split("?")[0]
    route = re.sub(r"([^/])\{p\}.*$", r"\1", route)  # '/api/audit$query' is '/api/audit'
    return route.rstrip("/")


def called_routes() -> list[str]:
    found: set[str] = set()
    for directory in SOURCE_DIRS:
        base = ROOT / directory
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*")):
            if path.suffix not in SOURCE_SUFFIXES or not path.is_file() or GENERATED_CLIENT in path.parents:
                continue
            for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
                code = re.sub(r"(^|\s)//.*$", "", line)  # comments hold examples, not calls
                if code.lstrip().startswith(("*", "/*")):
                    continue
                for string in STRING.finditer(code):
                    inside = INSIDE.search(string.group(1) or string.group(2) or "")
                    if inside:
                        route = normalize(inside.group(1))
                        if len(route) > 5:
                            found.add(route)
    return sorted(found)


def client_routes() -> set[str]:
    """The routes the vendored typed client can call (its catalogue of the contract)."""
    found: set[str] = set()
    for path in sorted(GENERATED_CLIENT.glob("*.kt")) if GENERATED_CLIENT.is_dir() else []:
        for string in re.finditer(r'"(/[^"]*)"', path.read_text(encoding="utf-8", errors="replace")):
            found.add(re.sub(r"\{[^}]+\}", "{p}", string.group(1)).split("?")[0].rstrip("/"))
    return found


def inventory_problems(by_hand: list[str]) -> tuple[list[str], list[str]]:
    """(the routes of the typed client, the problems of the inventory against the routes still written by hand)."""
    if not INVENTORY.is_file():
        return [], ["docs/typed_client_inventory.json is missing"]
    inventory = json.loads(INVENTORY.read_text(encoding="utf-8"))
    typed: list[str] = list(inventory.get("typed", []))
    kept: dict[str, str] = dict(inventory.get("by_hand", {}))
    problems = [f"{r} is called by hand but is not in by_hand of the inventory" for r in by_hand if r not in kept]
    problems += [f"{r} is in by_hand of the inventory but no longer called by hand" for r in kept if r not in by_hand]
    problems += [f"{r} is typed in the inventory but still called by hand" for r in typed if r in by_hand]
    problems += [f"{r} is by hand in the inventory without a reason" for r, why in kept.items() if not str(why).strip()]
    catalogue = client_routes()
    problems += [f"{r} is typed in the inventory but the vendored client has no such route" for r in typed if r not in catalogue]
    return typed, problems


def contract_problems(routes: list[str]) -> list[str]:
    if not CONTRACT.is_file():
        # a release check sets HYDRA_UMC_REQUIRE_WORKSPACE=1: a missing contract is then a failure, not a skip
        return ["the server contract is not available (HYDRA_UMC_SDK_DIR, or the SDK next to this repository)"] if os.environ.get("HYDRA_UMC_REQUIRE_WORKSPACE") == "1" else []
    paths = list(json.loads(CONTRACT.read_text(encoding="utf-8")).get("paths", {}))

    def matches(route: str, path: str) -> bool:
        return re.fullmatch(re.escape(route).replace(r"\{p\}", "[^/]+"), re.sub(r"\{[^}]+\}", "x", path)) is not None

    return [f"{route} is not in the server contract" for route in routes if not route.startswith("/api/ai/") and not any(matches(route, p) for p in paths)]


def main(argv: list[str]) -> int:
    by_hand = called_routes()
    typed, problems = inventory_problems(by_hand)
    routes = sorted(set(by_hand) | set(typed))
    if "--record" in argv:
        RECORDED.write_text(json.dumps(routes, indent=2) + "\n", encoding="utf-8")
        print(f"REST_ROUTES=RECORDED {len(routes)} routes")
        return 0
    problems += contract_problems(routes)
    if not RECORDED.is_file():
        problems.append("docs/rest_routes.json is missing (run tools/check_rest_routes.py --record)")
    else:
        recorded = json.loads(RECORDED.read_text(encoding="utf-8"))
        problems += [f"{r} is called but not recorded" for r in routes if r not in recorded]
        problems += [f"{r} is recorded but no longer called" for r in recorded if r not in routes]
    if problems:
        print("REST_ROUTES=FAIL " + "; ".join(problems))
        return 1
    print(f"REST_ROUTES=PASS {len(routes)} routes" + ("" if CONTRACT.is_file() else " (server contract not next to this repository: only the recorded list was compared)"))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
