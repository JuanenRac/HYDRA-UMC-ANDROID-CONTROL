# =============================================================================
# HYDRA-UMC-ANDROID-CONTROL - tools/kotlin_coverage.py
# Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
# GPL-3.0-or-later - see LICENSE
# =============================================================================
"""Reads the JaCoCo XML report of the unit tests and says which files are least covered.

Usage: python tools/kotlin_coverage.py [REPORT.xml] [--top N] [--floor PERCENT]

The report is the one `createDebugUnitTestCoverageReport` writes (run Gradle with -PhydraUmcCoverage=true). The total is the share of lines run by at least
one test; the list shows the files with the most lines that no test runs, which is where a new test pays most. With --floor the exit status is 1 when the total is
below the given percent (no floor is applied unless it is given: a floor comes from a measured value, never from a guess).
"""
from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

DEFAULT_REPORT = Path("app/build/reports/coverage/test/debug/report.xml")


def line_counter(element: ET.Element) -> tuple[int, int]:
    """(missed, covered) lines of a report element."""
    for counter in element.findall("counter"):
        if counter.get("type") == "LINE":
            return int(counter.get("missed", "0")), int(counter.get("covered", "0"))
    return 0, 0


def read_report(path: Path) -> tuple[tuple[int, int], list[tuple[str, int, int]]]:
    """The total (missed, covered) and, for every source file, (name, missed, covered)."""
    root = ET.parse(path).getroot()
    files: list[tuple[str, int, int]] = []
    for package in root.findall("package"):
        for source in package.findall("sourcefile"):
            missed, covered = line_counter(source)
            files.append((f"{package.get('name', '')}/{source.get('name', '')}", missed, covered))
    return line_counter(root), files


def percent(missed: int, covered: int) -> float:
    total = missed + covered
    return 100.0 * covered / total if total else 100.0


def summary(path: Path, top: int = 15) -> str:
    (missed, covered), files = read_report(path)
    lines = [f"lines covered: {percent(missed, covered):.1f} % ({covered} of {missed + covered}) in {len(files)} files", "", "files with the most lines no test runs:"]
    for name, miss, cov in sorted(files, key=lambda f: -f[1])[:top]:
        lines.append(f"  {miss:5d} missed  {percent(miss, cov):5.1f} %  {name}")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("report", nargs="?", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--top", type=int, default=15)
    parser.add_argument("--floor", type=float, default=None, help="fail when the total is below this percent")
    arguments = parser.parse_args(argv)
    if not arguments.report.is_file():
        print(f"no report at {arguments.report}: run Gradle with -PhydraUmcCoverage=true first", file=sys.stderr)
        return 2
    print(summary(arguments.report, arguments.top))
    if arguments.floor is not None:
        (missed, covered), _ = read_report(arguments.report)
        if percent(missed, covered) < arguments.floor:
            print(f"COVERAGE=FAIL below the floor of {arguments.floor:g} %")
            return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
