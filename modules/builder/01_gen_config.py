#!/usr/bin/env python3
"""
01_gen_config.py - Generates BuildConfig.java, gen.xml, and dev-aware AndroidManifest.xml.

All numeric build fields are validated before anything is written. Configured
MIN_API/TARGET_SDK values are applied to the generated manifest so compile, D8
and manifest metadata cannot silently disagree (R16/A02).
"""
import html
import os
import re
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(os.path.dirname(SCRIPT_DIR))

# Escapes that would be interpreted later (Java/XML) instead of being literal text.
_ESCAPE_LITERAL = re.compile(r"\\[xu][0-9a-fA-F]{2,4}")
_PACKAGE_ID = re.compile(r"^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$")


class ConfigError(Exception):
    """A config value that must not be turned into generated source."""


def parse_env(path):
    data = {}
    if os.path.isfile(path):
        with open(path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                key, value = line.split("=", 1)
                data[key.strip()] = value.strip().strip('"').strip("'")
    return data


def require_int(env, key, default, minimum, maximum):
    raw = str(env.get(key, str(default))).strip()
    if not re.fullmatch(r"[0-9]{1,10}", raw):
        raise ConfigError("%s must be a decimal integer (got %r)" % (key, raw))
    value = int(raw)
    if value < minimum or value > maximum:
        raise ConfigError("%s must be between %d and %d (got %d)"
                          % (key, minimum, maximum, value))
    return value


def require_string(env, key, default):
    value = env.get(key, default)
    if value is None:
        value = default
    if not value:
        raise ConfigError("%s must not be empty" % key)
    for ch in value:
        if ord(ch) < 0x20 or ord(ch) == 0x7F:
            raise ConfigError("%s contains a control character" % key)
    if _ESCAPE_LITERAL.search(value):
        raise ConfigError("%s contains an escape sequence instead of literal text" % key)
    return value


def require_package(env, key, default):
    value = require_string(env, key, default).strip()
    if not _PACKAGE_ID.match(value):
        raise ConfigError("%s is not a valid Android package id: %r" % (key, value))
    return value


def java_string(value):
    escaped = (
        str(value)
        .replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    )
    return '"' + escaped + '"'


def apply_uses_sdk(content, min_api, target_sdk):
    """Replace or insert <uses-sdk> so the manifest follows MIN_API/TARGET_SDK."""
    sdk = '<uses-sdk android:minSdkVersion="%d" android:targetSdkVersion="%d" />' % (
        min_api,
        target_sdk,
    )
    if re.search(r"<uses-sdk\b", content):
        content = re.sub(
            r'android:minSdkVersion\s*=\s*"[^"]*"',
            'android:minSdkVersion="%d"' % min_api,
            content,
            count=1,
        )
        content = re.sub(
            r'android:targetSdkVersion\s*=\s*"[^"]*"',
            'android:targetSdkVersion="%d"' % target_sdk,
            content,
            count=1,
        )
        return content
    match = re.search(r"<manifest\b[^>]*>", content)
    if match is None:
        raise ConfigError("AndroidManifest.xml has no <manifest> element")
    return content[: match.end()] + "\n    " + sdk + content[match.end():]


def main():
    build_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(PROJECT_ROOT, "build")
    env_file = os.path.join(PROJECT_ROOT, "config", "app.env")
    env = parse_env(env_file)

    is_dev = os.environ.get("DEV_MODE") == "1"

    try:
        pkg_base = require_package(env, "PKG", "com.agy")
        pkg_dev = require_package(env, "PKG_DEV", "com.agy.dev")
        app_id = pkg_dev if is_dev else pkg_base
        port = require_int(env, "PORT_DEV" if is_dev else "PORT",
                           45158 if is_dev else 45157, 1, 65535)
        port_dev = require_int(env, "PORT_DEV", 45158, 1, 65535)
        gw_port = require_int(env, "GATEWAY_PORT_DEV" if is_dev else "GATEWAY_PORT",
                              45161 if is_dev else 45160, 1, 65535)
        core_version = require_string(env, "CORE_VERSION", "1.0.0")
        app_version_name = require_string(env, "APP_VERSION_NAME", "1.0.0")
        app_version_code = require_int(env, "APP_VERSION_CODE", 1, 1, 2100000000)
        oauth_scheme = require_string(env, "OAUTH_SCHEME", "antigravity")
        app_name = require_string(env, "APP_NAME_DEV" if is_dev else "APP_NAME",
                                  "Antigravity Dev" if is_dev else "Antigravity")
        min_api = require_int(env, "MIN_API", 24, 21, 35)
        target_sdk = require_int(env, "TARGET_SDK", 28, 21, 35)
        if min_api > target_sdk:
            raise ConfigError("MIN_API (%d) must not exceed TARGET_SDK (%d)"
                              % (min_api, target_sdk))
    except ConfigError as error:
        print("[ERROR] %s" % error, file=sys.stderr)
        sys.exit(2)

    is_debug = "true" if is_dev else "false"

    pkg_dir = os.path.join(build_dir, "gen", *pkg_base.split("."))
    res_values_dir = os.path.join(build_dir, "res", "values")
    os.makedirs(pkg_dir, exist_ok=True)
    os.makedirs(res_values_dir, exist_ok=True)

    # 1. BuildConfig.java (always in com.agy package for source compatibility)
    build_config_path = os.path.join(pkg_dir, "BuildConfig.java")
    with open(build_config_path, "w", encoding="utf-8") as f:
        f.write("package %s;\n\n" % pkg_base)
        f.write("public final class BuildConfig {\n")
        f.write("    private BuildConfig() {}\n\n")
        f.write("    public static final String APPLICATION_ID = %s;\n" % java_string(app_id))
        f.write("    public static final int PORT = %d;\n" % port)
        f.write("    public static final int PORT_DEV = %d;\n" % port_dev)
        f.write("    public static final int GATEWAY_PORT = %d;\n" % gw_port)
        f.write("    public static final String CORE_VERSION = %s;\n" % java_string(core_version))
        f.write("    public static final String APP_VERSION_NAME = %s;\n" % java_string(app_version_name))
        f.write("    public static final int APP_VERSION_CODE = %d;\n" % app_version_code)
        f.write("    public static final String OAUTH_SCHEME = %s;\n" % java_string(oauth_scheme))
        f.write("    public static final String APP_NAME = %s;\n" % java_string(app_name))
        f.write("    public static final int MIN_API = %d;\n" % min_api)
        f.write("    public static final int TARGET_SDK = %d;\n" % target_sdk)
        f.write("    public static final boolean DEBUG = %s;\n" % is_debug)
        f.write("}\n")

    # 2. gen.xml
    gen_xml_path = os.path.join(res_values_dir, "gen.xml")
    with open(gen_xml_path, "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n')
        f.write("<resources>\n")
        f.write('    <string name="app_name">%s</string>\n' % html.escape(app_name, quote=True))
        f.write('    <integer name="https_server_port">%d</integer>\n' % port)
        f.write('    <integer name="gateway_port">%d</integer>\n' % gw_port)
        f.write('    <string name="oauth_scheme">%s</string>\n' % html.escape(oauth_scheme, quote=True))
        f.write("</resources>\n")

    # 3. AndroidManifest.xml (dev-aware, configured SDK levels)
    src_manifest = os.path.join(PROJECT_ROOT, "app", "src", "main", "AndroidManifest.xml")
    target_manifest = os.path.join(build_dir, "AndroidManifest.xml")
    with open(src_manifest, "r", encoding="utf-8") as f:
        content = f.read()

    content = content.replace("${applicationId}", app_id)
    content = re.sub(r'android:versionName="[^"]*"',
                     'android:versionName="%s"' % app_version_name, content, count=1)
    content = re.sub(r'android:versionCode="[^"]*"',
                     'android:versionCode="%d"' % app_version_code, content, count=1)
    content = apply_uses_sdk(content, min_api, target_sdk)

    if is_dev:
        content = content.replace('package="com.agy"', 'package="com.agy.dev"')
        content = content.replace('com.agy.permission.INTERNAL_BROADCAST',
                                  'com.agy.dev.permission.INTERNAL_BROADCAST')
        content = content.replace('<application', '<application android:debuggable="true"', 1)
        print("[*] Configured DEV manifest: package=com.agy.dev, debuggable=true")

    with open(target_manifest, "w", encoding="utf-8") as f:
        f.write(content)

    print("[OK] Generated BuildConfig.java -> %s" % build_config_path)
    print("[OK] Generated gen.xml -> %s" % gen_xml_path)
    print("[OK] Generated manifest -> %s (minSdk=%d targetSdk=%d)"
          % (target_manifest, min_api, target_sdk))


if __name__ == "__main__":
    main()
