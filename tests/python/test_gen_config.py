#!/usr/bin/env python3
"""Offline regression tests for modules/builder/01_gen_config.py.

Each test builds an isolated workspace under build-test/ containing a copy of
the generator, a fixture config/app.env, and the checked-in manifest. The real
config/app.env and app/src/main/AndroidManifest.xml are never rewritten.
"""
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
GEN_SCRIPT = REPO_ROOT / "modules" / "builder" / "01_gen_config.py"
SOURCE_MANIFEST = REPO_ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
BUILD_TEST_ROOT = REPO_ROOT / "build-test"

BASE_ENV = """\
PKG=com.agy
PKG_DEV=com.agy.dev
APP_NAME=Antigravity
APP_NAME_DEV=Antigravity Dev
PORT=45157
PORT_DEV=45158
GATEWAY_PORT=45160
GATEWAY_PORT_DEV=45161
OAUTH_SCHEME=antigravity
APP_VERSION_NAME=1.0.0
APP_VERSION_CODE=1
CORE_VERSION=1.0.0
MIN_API=24
TARGET_SDK=28
"""


class GenConfigTests(unittest.TestCase):
    def setUp(self):
        BUILD_TEST_ROOT.mkdir(parents=True, exist_ok=True)
        self.workspace = Path(tempfile.mkdtemp(prefix="gen-config-", dir=BUILD_TEST_ROOT))
        (self.workspace / "modules" / "builder").mkdir(parents=True)
        (self.workspace / "config").mkdir()
        (self.workspace / "app" / "src" / "main").mkdir(parents=True)
        shutil.copy2(GEN_SCRIPT, self.workspace / "modules" / "builder" / "01_gen_config.py")
        shutil.copy2(SOURCE_MANIFEST, self.workspace / "app" / "src" / "main" / "AndroidManifest.xml")
        self.write_env(BASE_ENV)

    def tearDown(self):
        shutil.rmtree(self.workspace, ignore_errors=True)

    def write_env(self, text):
        (self.workspace / "config" / "app.env").write_text(text, encoding="utf-8")

    def run_gen(self, build_name="build", dev=False):
        env = os.environ.copy()
        env.pop("DEV_MODE", None)
        if dev:
            env["DEV_MODE"] = "1"
        build_dir = self.workspace / build_name
        result = subprocess.run(
            [sys.executable, str(self.workspace / "modules" / "builder" / "01_gen_config.py"), str(build_dir)],
            cwd=self.workspace,
            env=env,
            capture_output=True,
            text=True,
        )
        return result, build_dir

    def read_build_config(self, build_dir):
        return (build_dir / "gen" / "com" / "agy" / "BuildConfig.java").read_text(encoding="utf-8")

    def parse_xml(self, path):
        return ET.parse(path).getroot()

    def manifest_uses_sdk(self, build_dir):
        root = self.parse_xml(build_dir / "AndroidManifest.xml")
        return root.find("uses-sdk")

    def test_release_generates_expected_constants(self):
        result, build_dir = self.run_gen()
        self.assertEqual(result.returncode, 0, result.stderr)
        build_config = self.read_build_config(build_dir)
        for expected in (
            'public static final String APPLICATION_ID = "com.agy";',
            "public static final int PORT = 45157;",
            "public static final int PORT_DEV = 45158;",
            "public static final int GATEWAY_PORT = 45160;",
            'public static final String CORE_VERSION = "1.0.0";',
            'public static final String APP_VERSION_NAME = "1.0.0";',
            "public static final int APP_VERSION_CODE = 1;",
            'public static final String OAUTH_SCHEME = "antigravity";',
            'public static final String APP_NAME = "Antigravity";',
            "public static final int MIN_API = 24;",
            "public static final int TARGET_SDK = 28;",
            "public static final boolean DEBUG = false;",
        ):
            self.assertIn(expected, build_config)

        gen_xml = self.parse_xml(build_dir / "res" / "values" / "gen.xml")
        self.assertEqual(gen_xml.find("./string[@name='app_name']").text, "Antigravity")
        self.assertEqual(gen_xml.find("./integer[@name='https_server_port']").text, "45157")
        self.assertEqual(gen_xml.find("./integer[@name='gateway_port']").text, "45160")
        self.assertEqual(gen_xml.find("./string[@name='oauth_scheme']").text, "antigravity")

        manifest = (build_dir / "AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn('package="com.agy"', manifest)
        self.assertIn('android:versionName="1.0.0"', manifest)
        self.assertIn('android:versionCode="1"', manifest)

    def test_dev_generates_dev_identity(self):
        result, build_dir = self.run_gen(dev=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        build_config = self.read_build_config(build_dir)
        self.assertIn('public static final String APPLICATION_ID = "com.agy.dev";', build_config)
        self.assertIn('public static final String APP_NAME = "Antigravity Dev";', build_config)
        self.assertIn("public static final int PORT = 45158;", build_config)
        self.assertIn("public static final int GATEWAY_PORT = 45161;", build_config)
        self.assertIn("public static final boolean DEBUG = true;", build_config)

        manifest = (build_dir / "AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn('package="com.agy.dev"', manifest)
        self.assertIn('android:debuggable="true"', manifest)
        self.assertIn('authorities="com.agy.dev.fileprovider"', manifest)

    def test_manifest_uses_configured_sdk_levels(self):
        """R16: generated manifest must use MIN_API/TARGET_SDK, not literals."""
        self.write_env(BASE_ENV.replace("MIN_API=24", "MIN_API=26").replace("TARGET_SDK=28", "TARGET_SDK=31"))
        result, build_dir = self.run_gen()
        self.assertEqual(result.returncode, 0, result.stderr)
        build_config = self.read_build_config(build_dir)
        self.assertIn("public static final int MIN_API = 26;", build_config)
        self.assertIn("public static final int TARGET_SDK = 31;", build_config)

        uses_sdk = self.manifest_uses_sdk(build_dir)
        self.assertIsNotNone(uses_sdk, "R16: generated manifest has no <uses-sdk> element")
        self.assertEqual(
            uses_sdk.get("{http://schemas.android.com/apk/res/android}minSdkVersion"),
            "26",
            "R16: manifest minSdkVersion does not follow MIN_API",
        )
        self.assertEqual(
            uses_sdk.get("{http://schemas.android.com/apk/res/android}targetSdkVersion"),
            "31",
            "R16: manifest targetSdkVersion does not follow TARGET_SDK",
        )

    def test_invalid_numeric_sdk_is_rejected(self):
        """R16: invalid numeric input must fail instead of generating bad Java."""
        self.write_env(BASE_ENV.replace("MIN_API=24", "MIN_API=not-a-number"))
        result, _ = self.run_gen()
        self.assertNotEqual(result.returncode, 0, "R16: invalid MIN_API was accepted")
        self.assertIn("MIN_API", result.stderr + result.stdout)

    def test_control_characters_are_rejected(self):
        """R16: control characters must not be emitted into Java/XML literals."""
        self.write_env(BASE_ENV.replace("APP_NAME=Antigravity", 'APP_NAME="Antigravity\x01Injected"'))
        result, _ = self.run_gen()
        self.assertNotEqual(result.returncode, 0, "R16: control character in APP_NAME was accepted")

    def test_xml_and_java_escaping(self):
        self.write_env(
            BASE_ENV.replace("APP_NAME=Antigravity", 'APP_NAME=Antigravity & "Studio" <ARM64> \\ test')
            .replace("OAUTH_SCHEME=antigravity", 'OAUTH_SCHEME=antigravity&dev')
        )
        result, build_dir = self.run_gen()
        self.assertEqual(result.returncode, 0, result.stderr)
        gen_xml = self.parse_xml(build_dir / "res" / "values" / "gen.xml")
        self.assertEqual(gen_xml.find("./string[@name='app_name']").text, 'Antigravity & "Studio" <ARM64> \\ test')
        self.assertEqual(gen_xml.find("./string[@name='oauth_scheme']").text, 'antigravity&dev')

        build_config = self.read_build_config(build_dir)
        self.assertIn(r'public static final String APP_NAME = "Antigravity & \"Studio\" <ARM64> \\ test";', build_config)
        self.assertIn(r'public static final String OAUTH_SCHEME = "antigravity&dev";', build_config)

    def test_generation_does_not_modify_sources(self):
        source_manifest_before = SOURCE_MANIFEST.read_bytes()
        workspace_manifest = self.workspace / "app" / "src" / "main" / "AndroidManifest.xml"
        workspace_manifest_before = workspace_manifest.read_bytes()
        env_before = (self.workspace / "config" / "app.env").read_bytes()
        result, _ = self.run_gen()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(SOURCE_MANIFEST.read_bytes(), source_manifest_before)
        self.assertEqual(workspace_manifest.read_bytes(), workspace_manifest_before)
        self.assertEqual((self.workspace / "config" / "app.env").read_bytes(), env_before)


if __name__ == "__main__":
    unittest.main()
