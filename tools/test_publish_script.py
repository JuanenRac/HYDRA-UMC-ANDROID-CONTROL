#!/usr/bin/env python3
# =============================================================================
# HYDRA-UMC-ANDROID-CONTROL - tools/test_publish_script.py
# Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
# GPL-3.0-or-later - see LICENSE
# =============================================================================
"""publish-github-release.sh never puts the GitHub token on a command line.

Runs the script against a fake `curl` that records every argument it receives and
answers like the GitHub API, then checks that the token reached curl only through a
private configuration file and that no fixed scratch path under /tmp is used.
"""
from __future__ import annotations

import os
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOKEN = "ghp_TESTTOKEN_0123456789abcdef"

FAKE_CURL = r"""#!/usr/bin/env bash
# Records the arguments and the content of any -K file, then answers like the GitHub API.
log="$FAKE_CURL_LOG"
printf 'ARGS:%s\n' "$*" >> "$log"
out=""
prev=""
for a in "$@"; do
  if [[ "$prev" == "-K" ]]; then
    printf 'CONFIG_MODE:%s\n' "$(stat -c %a "$a" 2>/dev/null || echo unknown)" >> "$log"
    printf 'CONFIG:%s\n' "$(cat "$a")" >> "$log"
  fi
  if [[ "$prev" == "-o" ]]; then out="$a"; fi
  prev="$a"
done
case "$*" in
  *"-w"*) [[ -n "$out" ]] && echo '{"message":"Not Found"}' > "$out"; printf '404' ;;
  *"/releases -o"*|*"POST"*"/releases"*) [[ -n "$out" ]] && echo '{"id": 4242}' > "$out" ;;
esac
if [[ -n "$out" && ! -s "$out" ]]; then echo '{"id": 4242}' > "$out"; fi
exit 0
"""


def _have(tool: str) -> bool:
    return shutil.which(tool) is not None


@unittest.skipUnless(_have("bash"), "needs bash")
class PublishScriptTokenTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = Path(tempfile.mkdtemp(prefix="publish_test_"))
        (self.tmp / "app").mkdir()
        (self.tmp / "dist").mkdir()
        (self.tmp / "bin").mkdir()
        shutil.copy(ROOT / "publish-github-release.sh", self.tmp / "publish-github-release.sh")
        (self.tmp / "app" / "version.properties").write_bytes(b"versionMajor=0\nversionMinor=1\nversionPatch=0\n")
        (self.tmp / "dist" / "HYDRA-UMC-ANDROID-CONTROL-release.apk").write_bytes(b"apk")
        (self.tmp / "CHANGELOG.md").write_bytes(b"## [0.1.0]\n- x\n")
        (self.tmp / ".env").write_bytes(f"GITHUB_TOKEN={TOKEN}\n".encode("utf-8"))
        curl = self.tmp / "bin" / "curl"
        curl.write_bytes(FAKE_CURL.encode("utf-8"))
        curl.chmod(curl.stat().st_mode | stat.S_IEXEC)
        if sys.platform == "win32":
            # The script calls `python3`; point it at the interpreter running this test (through
            # wslpath when bash is the WSL one, as is, for Git Bash).
            exe = Path(sys.executable).as_posix()
            shim = self.tmp / "bin" / "python3"
            lines = [
                "#!/usr/bin/env bash",
                f"exec \"$(wslpath -u '{exe}' 2>/dev/null || echo '{exe}')\" \"$@\"",
                "",
            ]
            shim.write_bytes("\n".join(lines).encode("utf-8"))
            shim.chmod(shim.stat().st_mode | stat.S_IEXEC)
        self.log = self.tmp / "curl.log"

    def tearDown(self) -> None:
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _run(self) -> str:
        env = dict(os.environ)
        result = subprocess.run(
            ["bash", "-c", 'export PATH="$PWD/bin:$PATH" FAKE_CURL_LOG="$PWD/curl.log"; exec bash publish-github-release.sh'],
            cwd=self.tmp, env=env, capture_output=True, text=True, timeout=60,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return self.log.read_text(encoding="utf-8")

    def test_token_is_never_an_argument_of_curl(self) -> None:
        log = self._run()
        arg_lines = [line for line in log.splitlines() if line.startswith("ARGS:")]
        self.assertTrue(arg_lines, "curl was never called")
        for line in arg_lines:
            self.assertNotIn(TOKEN, line)
            self.assertNotIn("Authorization", line)
            self.assertIn("-K ", line)

    def test_token_reaches_curl_through_a_private_config_file(self) -> None:
        log = self._run()
        configs = [line for line in log.splitlines() if line.startswith("CONFIG:")]
        self.assertTrue(configs)
        self.assertTrue(all(TOKEN in line for line in configs))
        modes = [line.split(":", 1)[1] for line in log.splitlines() if line.startswith("CONFIG_MODE:")]
        self.assertTrue(all(mode in ("600", "unknown") for mode in modes), modes)

    def test_no_fixed_scratch_path_is_used(self) -> None:
        script = (ROOT / "publish-github-release.sh").read_text(encoding="utf-8")
        code = "\n".join(line for line in script.splitlines() if not line.lstrip().startswith("#"))
        self.assertNotIn("/tmp/", code)
        self.assertIn("mktemp", code)
        self.assertNotIn("Bearer ${token}", code.replace('"Authorization: Bearer %s"', ""))


if __name__ == "__main__":
    unittest.main()
