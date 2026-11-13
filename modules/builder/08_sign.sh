#!/usr/bin/env bash
# 08_sign.sh - Signs an aligned APK.
#
# Usage: 08_sign.sh <aligned.apk> <out.apk> <dev|release>
#
# dev     Explicitly uses the tracked public debug keystore. Development only;
#         never a release identity.
# release Fails closed unless the owner supplies an explicit release keystore,
#         alias, password files and expected certificate digest. No debug key,
#         generated key, or default password is ever used as a release fallback.
set -euo pipefail

ALIGNED_APK="${1:?aligned apk required}"
OUT_APK="${2:?output apk required}"
FLAVOR="${3:-release}"

mkdir -p "$(dirname "$OUT_APK")"

if [ "$FLAVOR" = "dev" ]; then
    KEYSTORE="${AGY_DEV_KEYSTORE:-${TOOLS_DIR:-modules/../tools}/debug.keystore}"
    KEY_ALIAS="${AGY_DEV_KEY_ALIAS:-androiddebugkey}"
    KEY_PASS="${AGY_DEV_KEY_PASS:-android}"
    STORE_PASS="${AGY_DEV_STORE_PASS:-android}"
    echo "[*] [08_sign] Signing DEV APK with the explicit debug identity (not releasable)."
    apksigner sign \
        --ks "$KEYSTORE" \
        --ks-key-alias "$KEY_ALIAS" \
        --ks-pass "pass:$STORE_PASS" \
        --key-pass "pass:$KEY_PASS" \
        --out "$OUT_APK" \
        "$ALIGNED_APK"
else
    missing=()
    [ -n "${AGY_RELEASE_KEYSTORE:-}" ] || missing+=("AGY_RELEASE_KEYSTORE")
    [ -n "${AGY_RELEASE_KEY_ALIAS:-}" ] || missing+=("AGY_RELEASE_KEY_ALIAS")
    [ -n "${AGY_RELEASE_KEY_PASS_FILE:-}" ] || missing+=("AGY_RELEASE_KEY_PASS_FILE")
    [ -n "${AGY_RELEASE_STORE_PASS_FILE:-}" ] || missing+=("AGY_RELEASE_STORE_PASS_FILE")
    [ -n "${AGY_RELEASE_CERT_SHA256:-}" ] || missing+=("AGY_RELEASE_CERT_SHA256")
    if [ ${#missing[@]} -gt 0 ]; then
        echo "[ERROR] [08_sign] release signing is not configured; refusing to sign." >&2
        echo "        Missing: ${missing[*]}" >&2
        echo "        Release identity/migration is owner decision P3/A04b. Use --dev for a" >&2
        echo "        development build, or supply the owner-controlled variables above." >&2
        exit 3
    fi
    for pass_file in "$AGY_RELEASE_KEY_PASS_FILE" "$AGY_RELEASE_STORE_PASS_FILE"; do
        if [ ! -f "$pass_file" ]; then
            echo "[ERROR] [08_sign] password file not found: $pass_file" >&2
            exit 3
        fi
    done
    echo "[*] [08_sign] Signing RELEASE APK with the owner-supplied identity."
    apksigner sign \
        --ks "$AGY_RELEASE_KEYSTORE" \
        --ks-key-alias "$AGY_RELEASE_KEY_ALIAS" \
        --ks-pass "file:$AGY_RELEASE_STORE_PASS_FILE" \
        --key-pass "file:$AGY_RELEASE_KEY_PASS_FILE" \
        --out "$OUT_APK" \
        "$ALIGNED_APK"
fi

echo "[OK] [08_sign] Signed APK created: $OUT_APK ($(stat -c%s "$OUT_APK") bytes)"
