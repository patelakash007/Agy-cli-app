#!/usr/bin/env python3
"""Offline regressions for modules/builder/05_stage_assets.py (A03/R17).

A fake cross-compiler produces a minimal AArch64 Android ELF so the staging
policy is testable without downloading an NDK. The real compile remains a CI /
REVIEW item; what is proven here is that staging never reuses the tracked binary
and never succeeds with a missing or invalid toolchain.
"""
import os
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
STAGE_SCRIPT = REPO_ROOT / "modules" / "builder" / "05_stage_assets.py"
BUILD_TEST_ROOT = REPO_ROOT / "build-test"

FAKE_CLANG = """#!/usr/bin/env bash
set -euo pipefail
out=""
prev=""
for arg in "$@"; do
    if [ "$prev" = "-o" ]; then out="$arg"; fi
    prev="$arg"
done
[ -n "$out" ] || { echo "fake clang: no -o" >&2; exit 1; }
python3 - "$out" "${FAKE_ELF_MODE:-good}" <<'PY'
import struct, sys
path, mode = sys.argv[1], sys.argv[2]
if mode == "bad":
    data = b"NOTELF" + bytes(58)
else:
    header = bytearray(64)
    header[0:4] = b"\\x7fELF"
    header[4] = 2
    header[5] = 1
    header[6] = 1
    header[16:18] = struct.pack("<H", 3)
    header[18:20] = struct.pack("<H", 0xB7)
    header[32:40] = struct.pack("<Q", 64)   # e_phoff
    header[54:56] = struct.pack("<H", 56)   # e_phentsize
    header[56:58] = struct.pack("<H", 1)    # e_phnum
    program = bytearray(56)
    program[0:4] = struct.pack("<I", 1)      # PT_LOAD
    program[48:56] = struct.pack("<Q", 16384)
    data = (bytes(header) + bytes(program)
            + b"/system/bin/linker64\\x00")
open(path, "wb").write(data)
PY
"""


class StageAssetsTests(unittest.TestCase):
    def setUp(self):
        BUILD_TEST_ROOT.mkdir(parents=True, exist_ok=True)
        self.tmp = Path(tempfile.mkdtemp(prefix="stage-assets-", dir=BUILD_TEST_ROOT))
        self.project = self.tmp / "project"
        (self.project / "modules" / "builder").mkdir(parents=True)
        (self.project / "modules" / "core" / "pty").mkdir(parents=True)
        (self.project / "modules" / "core" / "certs").mkdir(parents=True)
        (self.project / "modules" / "core" / "seed").mkdir(parents=True)
        shutil.copy2(STAGE_SCRIPT, self.project / "modules" / "builder" / "05_stage_assets.py")
        (self.project / "modules" / "core" / "pty" / "agy-pty.c").write_text(
            "int main(void) { return 0; }\n", encoding="utf-8")
        (self.project / "modules" / "core" / "certs" / "ca-certificates.crt").write_text(
            "-----BEGIN CERTIFICATE-----\n", encoding="utf-8")
        (self.project / "modules" / "core" / "seed" / "jetski_state.pbtxt").write_text(
            "AGENT_ONBOARDING_STATE_COMPLETED\n", encoding="utf-8")

        self.bin = self.tmp / "bin"
        self.bin.mkdir()
        clang = self.bin / "fake-clang"
        clang.write_text(FAKE_CLANG, encoding="utf-8")
        clang.chmod(clang.stat().st_mode | stat.S_IXUSR)
        self.clang = clang
        self.staging = self.tmp / "build" / "staging"

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def run_stage(self, *, clang=True, mode="good"):
        env = os.environ.copy()
        for key in ("ANDROID_NDK_HOME", "ANDROID_NDK_ROOT", "AGY_CLANG"):
            env.pop(key, None)
        env["FAKE_ELF_MODE"] = mode
        if clang:
            env["AGY_CLANG"] = str(self.clang)
        else:
            empty = self.tmp / "empty-path"
            empty.mkdir(exist_ok=True)
            env["PATH"] = str(empty)
        return subprocess.run(
            [sys.executable, str(self.project / "modules" / "builder" / "05_stage_assets.py"),
             str(self.staging)],
            capture_output=True, text=True, env=env,
        )

    def runtime(self, *parts):
        return self.staging / "assets" / "runtime" / Path(*parts)

    def test_valid_helper_is_staged_and_seed_is_not(self):
        result = self.run_stage()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        helper = self.runtime("bin", "agy-pty").read_bytes()
        self.assertEqual(helper[0:4], b"\x7fELF")
        self.assertEqual(helper[18], 0xB7)
        self.assertIn(b"/system/bin/linker64", helper)
        self.assertTrue(self.runtime("certs", "ca-certificates.crt").is_file())
        self.assertTrue(self.runtime("runtime.marker").is_file())
        self.assertFalse(self.runtime("seed").exists(),
                         "fabricated seed state must not be staged")

    def test_missing_compiler_fails_closed(self):
        result = self.run_stage(clang=False)
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("no Android NDK clang found", result.stderr)
        self.assertFalse(self.runtime("bin", "agy-pty").exists())

    def test_invalid_elf_fails_closed(self):
        result = self.run_stage(mode="bad")
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("not an ELF", result.stderr)
        self.assertFalse(self.runtime("bin", "agy-pty").exists())

    def test_tracked_binary_is_never_used(self):
        tracked = self.project / "modules" / "core" / "pty" / "agy-pty"
        tracked.write_bytes(b"STALE-TRACKED-BINARY")
        result = self.run_stage(clang=False)
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(tracked.read_bytes(), b"STALE-TRACKED-BINARY",
                         "staging modified the tracked binary")
        self.assertFalse(self.runtime("bin", "agy-pty").exists())


if __name__ == "__main__":
    unittest.main()
