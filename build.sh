#!/usr/bin/env bash
# build.sh - Convenient root wrapper for Antigravity APK builder.
# Usage:
#   bash build.sh        # Release edition (requires owner release-signing inputs; see 08_sign.sh)
#   bash build.sh --dev  # Debug edition signed with the explicit public debug key
#   bash build.sh --fast # Store runtime assets uncompressed for quick iteration (no build cache)
#
# The release route fails closed unless AGY_RELEASE_KEYSTORE, AGY_RELEASE_KEY_ALIAS,
# AGY_RELEASE_KEY_PASS_FILE, AGY_RELEASE_STORE_PASS_FILE and AGY_RELEASE_CERT_SHA256
# are supplied. Unsigned/unconfigured publication is blocked by design (P3/A04a).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec bash "$SCRIPT_DIR/modules/builder/build_apk.sh" "$@"
