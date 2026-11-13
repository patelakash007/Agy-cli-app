#!/usr/bin/env python3
"""Offline policy regressions for release signing (A04a/R18) and publication (G02a/R20).

A fake `apksigner` records the arguments it receives, so no keystore, real
signature, network access or release is involved.
"""
import os
import shutil
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SIGN_SCRIPT = REPO_ROOT / "modules" / "builder" / "08_sign.sh"
WORKFLOW = REPO_ROOT / ".github" / "workflows" / "build-and-release.yml"
BUILD_TEST_ROOT = REPO_ROOT / "build-test"

FAKE_APKSIGNER = """#!/usr/bin/env bash
set -euo pipefail
printf '%s\\n' "$@" >> "${APKSIGNER_LOG:?}"
out=""
prev=""
for arg in "$@"; do
    if [ "$prev" = "--out" ]; then out="$arg"; fi
    prev="$arg"
done
[ -n "$out" ] && printf 'fake-signed' > "$out"
exit 0
"""

RELEASE_ENV = (
    "AGY_RELEASE_KEYSTORE",
    "AGY_RELEASE_KEY_ALIAS",
    "AGY_RELEASE_KEY_PASS_FILE",
    "AGY_RELEASE_STORE_PASS_FILE",
    "AGY_RELEASE_CERT_SHA256",
)


class SigningGuardTests(unittest.TestCase):
    def setUp(self):
        BUILD_TEST_ROOT.mkdir(parents=True, exist_ok=True)
        self.tmp = Path(tempfile.mkdtemp(prefix="sign-guard-", dir=BUILD_TEST_ROOT))
        self.bin = self.tmp / "bin"
        self.bin.mkdir()
        signer = self.bin / "apksigner"
        signer.write_text(FAKE_APKSIGNER, encoding="utf-8")
        signer.chmod(signer.stat().st_mode | stat.S_IXUSR)
        self.log = self.tmp / "apksigner.log"
        self.aligned = self.tmp / "aligned.apk"
        self.aligned.write_bytes(b"aligned")
        self.out = self.tmp / "out.apk"

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def run_sign(self, flavor, extra_env=None):
        env = os.environ.copy()
        for key in RELEASE_ENV:
            env.pop(key, None)
        env["PATH"] = str(self.bin) + os.pathsep + env.get("PATH", "")
        env["APKSIGNER_LOG"] = str(self.log)
        env["AGY_DEV_KEYSTORE"] = str(self.tmp / "dev.keystore")
        self.out.unlink(missing_ok=True)
        if extra_env:
            env.update(extra_env)
        return subprocess.run(
            ["bash", str(SIGN_SCRIPT), str(self.aligned), str(self.out), flavor],
            capture_output=True, text=True, env=env,
        )

    def signer_was_invoked(self):
        return self.log.is_file() and self.log.stat().st_size > 0

    def test_release_without_identity_fails_before_signing(self):
        result = self.run_sign("release")
        self.assertEqual(result.returncode, 3, result.stdout + result.stderr)
        self.assertFalse(self.signer_was_invoked(), "signer ran without release identity")
        self.assertFalse(self.out.exists())
        self.assertIn("refusing to sign", result.stderr)

    def test_release_with_missing_password_files_fails_before_signing(self):
        result = self.run_sign("release", {
            "AGY_RELEASE_KEYSTORE": "/nope/keystore.jks",
            "AGY_RELEASE_KEY_ALIAS": "release",
            "AGY_RELEASE_KEY_PASS_FILE": "/nope/key.pass",
            "AGY_RELEASE_STORE_PASS_FILE": "/nope/store.pass",
            "AGY_RELEASE_CERT_SHA256": "0" * 64,
        })
        self.assertEqual(result.returncode, 3, result.stdout + result.stderr)
        self.assertFalse(self.signer_was_invoked(), "signer ran with missing password files")

    def test_dev_route_is_explicitly_labelled(self):
        result = self.run_sign("dev")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(self.signer_was_invoked(), "dev signing did not invoke the signer")
        logged = self.log.read_text(encoding="utf-8")
        self.assertIn("androiddebugkey", logged)
        self.assertIn("not releasable", result.stdout)


class PublicationGuardTests(unittest.TestCase):
    def setUp(self):
        self.text = WORKFLOW.read_text(encoding="utf-8")

    def test_tags_do_not_publish(self):
        self.assertNotIn("refs/tags/v", self.text,
                         "a tag trigger can still publish automatically")

    def test_publication_requires_explicit_confirmation(self):
        self.assertIn("publish_release", self.text)
        self.assertIn("inputs.publish_release == 'PUBLISH'", self.text)
        self.assertIn("environment: release", self.text)

    def test_default_permissions_are_read_only(self):
        head = self.text.split("jobs:", 1)[0]
        self.assertIn("contents: read", head)
        self.assertNotIn("contents: write", head)

    def test_release_job_does_not_publish_yet(self):
        release_job = self.text.split("  release:", 1)[1]
        self.assertIn("exit 1", release_job,
                      "release gates must still refuse publication (P1-P4 pending)")
        self.assertIn("if: false", release_job,
                      "the publish step must remain disabled until gates are approved")


if __name__ == "__main__":
    unittest.main()
