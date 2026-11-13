#!/usr/bin/env python3
"""Offline permission regressions for the host manifest (F02/R35).

The app works on app-internal storage and SAF grants. Requesting all-files
access, legacy shared-storage permissions, or unused notification and battery
exemptions would over-ask the user and contradict the permission contract.
"""
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
MANIFEST = REPO_ROOT / "app" / "src" / "main" / "AndroidManifest.xml"

FORBIDDEN_PERMISSIONS = (
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
)


class ManifestPermissionTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.manifest = MANIFEST.read_text(encoding="utf-8")

    def test_no_broad_or_unused_permissions(self):
        for permission in FORBIDDEN_PERMISSIONS:
            self.assertNotIn(
                permission,
                self.manifest,
                "manifest requests an unused/broad permission: %s" % permission,
            )

    def test_no_legacy_shared_storage_mode(self):
        self.assertNotIn(
            "requestLegacyExternalStorage",
            self.manifest,
            "legacy shared-storage mode re-enables raw external paths",
        )

    def test_share_provider_stays_unexported(self):
        start = self.manifest.find("com.agy.util.AppFileProvider")
        self.assertGreaterEqual(start, 0, "share provider declaration is missing")
        block = self.manifest[start:start + 400]
        self.assertIn(
            'android:exported="false"',
            block,
            "share provider must not be exported",
        )


if __name__ == "__main__":
    unittest.main()
