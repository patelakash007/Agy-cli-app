#!/usr/bin/env python3
"""Offline regressions for modules/builder/06_package.sh.

R31/A07: every secondary DEX output is packaged (or the build fails).
R32/A08: runtime packaging fails closed when required assets are missing.
R30/A09: --fast really stores runtime assets instead of only claiming it.

A fake `aapt` on PATH produces the base APK, so no Android SDK is required.
"""
import os
import shutil
import stat
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
PACKAGE_SCRIPT = REPO_ROOT / "modules" / "builder" / "06_package.sh"
BUILD_TEST_ROOT = REPO_ROOT / "build-test"

FAKE_AAPT = """#!/usr/bin/env bash
set -euo pipefail
out=""
while [ $# -gt 0 ]; do
    case "$1" in
        -F) out="$2"; shift 2 ;;
        *) shift ;;
    esac
done
[ -n "$out" ] || { echo "fake aapt: no -F output" >&2; exit 1; }
python3 - "$out" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "w", compression=zipfile.ZIP_DEFLATED) as z:
    z.writestr("AndroidManifest.xml", b"<manifest/>")
    z.writestr("resources.arsc", b"fake-resources")
PY
"""

DEX_HEADER = b"dex\n035\x00"


class PackageInputTests(unittest.TestCase):
    def setUp(self):
        BUILD_TEST_ROOT.mkdir(parents=True, exist_ok=True)
        self.tmp = Path(tempfile.mkdtemp(prefix="package-", dir=BUILD_TEST_ROOT))
        self.bin = self.tmp / "bin"
        self.bin.mkdir()
        aapt = self.bin / "aapt"
        aapt.write_text(FAKE_AAPT, encoding="utf-8")
        aapt.chmod(aapt.stat().st_mode | stat.S_IXUSR)

        self.manifest = self.tmp / "AndroidManifest.xml"
        self.manifest.write_text("<manifest/>", encoding="utf-8")
        self.res = self.tmp / "res"
        self.res.mkdir()
        self.android_jar = self.tmp / "android.jar"
        self.android_jar.write_bytes(b"jar")
        self.assets = self.tmp / "staging" / "assets"
        self.dex_dir = self.tmp / "dex"
        self.dex_dir.mkdir()

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def write_dex(self, name, payload):
        path = self.dex_dir / name
        path.write_bytes(DEX_HEADER + payload)
        return path

    def add_assets(self, text_payload=b"plain text asset\n" * 256):
        (self.assets / "runtime" / "bin").mkdir(parents=True, exist_ok=True)
        (self.assets / "runtime" / "certs").mkdir(parents=True, exist_ok=True)
        (self.assets / "runtime" / "runtime.marker").write_text("native-bionic\n", encoding="utf-8")
        helper = bytearray(64)
        helper[0:4] = b"\x7fELF"
        helper[4] = 2
        helper[5] = 1
        helper[18] = 0xB7
        helper[19] = 0
        (self.assets / "runtime" / "bin" / "agy-pty").write_bytes(
            bytes(helper) + b"/system/bin/linker64\x00")
        (self.assets / "runtime" / "certs" / "ca-certificates.crt").write_text(
            "-----BEGIN CERTIFICATE-----\n", encoding="utf-8")
        (self.assets / "readme.txt").write_bytes(text_payload)
        return text_payload

    def run_package(self, dex_input=None, *, compile_only=False, fast=False):
        out = self.tmp / "out.apk"
        if out.exists():
            out.unlink()
        command = [
            "bash", str(PACKAGE_SCRIPT), str(out), str(self.manifest), str(self.res),
            str(self.android_jar), str(self.assets), str(dex_input or self.dex_dir),
        ]
        if compile_only:
            command.append("--compile-only")
        env = os.environ.copy()
        env["PATH"] = str(self.bin) + os.pathsep + env.get("PATH", "")
        env["FAST_BUILD"] = "1" if fast else "0"
        result = subprocess.run(command, capture_output=True, text=True, env=env)
        return result, out

    def test_secondary_dex_is_preserved(self):
        primary = self.write_dex("classes.dex", b"primary-payload")
        secondary = self.write_dex("classes2.dex", b"secondary-payload")
        self.add_assets()
        result, out = self.run_package()
        self.assertEqual(result.returncode, 0, result.stderr)
        with zipfile.ZipFile(out) as zf:
            self.assertEqual(zf.read("classes.dex"), primary.read_bytes())
            self.assertEqual(zf.read("classes2.dex"), secondary.read_bytes())

    def test_invalid_dex_is_rejected(self):
        (self.dex_dir / "classes.dex").write_bytes(b"not-a-dex")
        self.add_assets()
        result, out = self.run_package()
        self.assertNotEqual(result.returncode, 0, "invalid DEX was packaged")
        self.assertFalse(out.exists())

    def test_missing_runtime_assets_fail_closed(self):
        self.write_dex("classes.dex", b"payload")
        result, _ = self.run_package()
        self.assertNotEqual(result.returncode, 0, "missing assets produced a successful APK")
        self.assertIn("runtime assets directory is missing", result.stderr)

    def test_missing_required_asset_fails_closed(self):
        self.write_dex("classes.dex", b"payload")
        self.add_assets()
        (self.assets / "runtime" / "bin" / "agy-pty").unlink()
        result, _ = self.run_package()
        self.assertNotEqual(result.returncode, 0, "missing helper was accepted")
        self.assertIn("agy-pty", result.stderr)

    def test_compile_only_allows_empty_assets(self):
        self.write_dex("classes.dex", b"payload")
        shutil.rmtree(self.assets, ignore_errors=True)
        result, out = self.run_package(compile_only=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("compile-only", result.stdout)
        with zipfile.ZipFile(out) as zf:
            self.assertNotIn("runtime/runtime.marker", zf.namelist())

    def test_fast_build_stores_assets_and_normal_build_deflates(self):
        payload = self.add_assets()
        self.write_dex("classes.dex", b"payload")

        result, normal = self.run_package(fast=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        with zipfile.ZipFile(normal) as zf:
            normal_info = zf.getinfo("assets/readme.txt")
            self.assertEqual(zf.read("assets/readme.txt"), payload)
        self.assertEqual(normal_info.compress_type, zipfile.ZIP_DEFLATED)

        result, fast = self.run_package(fast=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        with zipfile.ZipFile(fast) as zf:
            fast_info = zf.getinfo("assets/readme.txt")
            self.assertEqual(zf.read("assets/readme.txt"), payload)
        self.assertEqual(fast_info.compress_type, zipfile.ZIP_STORED,
                         "FAST_BUILD did not store the plain-text asset")


if __name__ == "__main__":
    unittest.main()
