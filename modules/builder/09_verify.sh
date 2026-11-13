#!/usr/bin/env bash
# 09_verify.sh - Verifies APK signature/integrity.
#
# Usage: 09_verify.sh <apk> <dev|release>
# Release verification requires AGY_RELEASE_CERT_SHA256 and fails when the
# signed certificate does not match the approved identity (A04a).
set -euo pipefail

APK="${1:?apk required}"
FLAVOR="${2:-release}"

echo "[*] [09_verify] Verifying APK signature..."
[ -f "$APK" ] || { echo "Error: APK not found: $APK" >&2; exit 1; }

apksigner verify --verbose "$APK"

if [ "$FLAVOR" = "release" ]; then
    if [ -z "${AGY_RELEASE_CERT_SHA256:-}" ]; then
        echo "[ERROR] [09_verify] AGY_RELEASE_CERT_SHA256 is required for release verification." >&2
        exit 3
    fi
    actual="$(apksigner verify --print-certs "$APK" \
        | sed -n 's/^Signer #1 certificate SHA-256 digest: *//p' | head -1)"
    expected="$(printf '%s' "$AGY_RELEASE_CERT_SHA256" | tr 'A-F' 'a-f')"
    actual_lc="$(printf '%s' "$actual" | tr 'A-F' 'a-f')"
    if [ -z "$actual_lc" ] || [ "$actual_lc" != "$expected" ]; then
        echo "[ERROR] [09_verify] release certificate digest mismatch" >&2
        echo "        expected: $expected" >&2
        echo "        actual:   ${actual_lc:-<none>}" >&2
        exit 4
    fi
    echo "[OK] [09_verify] release certificate digest matches the approved identity"
fi

echo "[SUCCESS] [09_verify] APK verification PASSED: $APK"
