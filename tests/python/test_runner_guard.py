#!/usr/bin/env python3
"""A06/R29 regressions for tests/run_host_tests.sh.

The zero-case and single-suite cases run a copy of the runner against a
temporary empty/minimal project so this test never recursively invokes its own
discovery.
"""
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
RUNNER = REPO_ROOT / "tests" / "run_host_tests.sh"
BUILD_TEST_ROOT = REPO_ROOT / "build-test"

PASSING_TEST = """import unittest


class PassingTest(unittest.TestCase):
    def test_ok(self):
        self.assertTrue(True)
"""


class RunnerGuardTests(unittest.TestCase):
    def setUp(self):
        BUILD_TEST_ROOT.mkdir(parents=True, exist_ok=True)
        self.tmp = Path(tempfile.mkdtemp(prefix="runner-guard-", dir=BUILD_TEST_ROOT))
        self.project = self.tmp / "project"
        (self.project / "tests" / "python").mkdir(parents=True)
        shutil.copy2(RUNNER, self.project / "tests" / "run_host_tests.sh")

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def run_runner(self, script, args):
        env = os.environ.copy()
        env["PYTHON"] = sys.executable
        return subprocess.run(
            ["bash", str(script)] + args,
            capture_output=True, text=True, env=env, cwd=str(script.parent.parent),
        )

    def test_contradictory_flags_fail_on_real_runner(self):
        result = self.run_runner(RUNNER, ["--python-only", "--java-only"])
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("cannot be combined", result.stderr)
        self.assertNotIn("PASS", result.stdout)

    def test_unknown_argument_fails(self):
        result = self.run_runner(RUNNER, ["--bogus"])
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)

    def test_help_is_not_reported_as_pass(self):
        result = self.run_runner(RUNNER, ["--help"])
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertNotIn("Host test result: PASS", result.stdout)

    def test_empty_discovery_is_not_green(self):
        result = self.run_runner(self.project / "tests" / "run_host_tests.sh",
                                 ["--python-only"])
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("zero executed cases", result.stderr)

    def test_single_suite_reports_real_counts(self):
        (self.project / "tests" / "python" / "test_ok.py").write_text(
            PASSING_TEST, encoding="utf-8")
        result = self.run_runner(self.project / "tests" / "run_host_tests.sh",
                                 ["--python-only"])
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("python=1", result.stdout)
        self.assertIn("java=0", result.stdout)


if __name__ == "__main__":
    unittest.main()
