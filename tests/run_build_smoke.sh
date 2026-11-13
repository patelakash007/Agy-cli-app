#!/usr/bin/env bash
# run_build_smoke.sh - Compile/package smoke test for release and dev flavors.
#
# Runs generation, resource, Java, D8, packaging, and alignment stages for both
# flavors. It does not sign, install, download anything, or invoke a live agent.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_ROOT="$PROJECT_ROOT/build-test/smoke"
PYTHON="${PYTHON:-python3}"
ANDROID_JAR="$PROJECT_ROOT/tools/android.jar"
R8_JAR="$PROJECT_ROOT/tools/r8.jar"
RES_DIR="$PROJECT_ROOT/app/src/main/res"
SRC_DIR="$PROJECT_ROOT/app/src/main/java"

for tool in "$PYTHON" aapt javac java; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "[ERROR] Missing required smoke-test tool: $tool" >&2
        exit 1
    fi
done
for jar in "$ANDROID_JAR" "$R8_JAR"; do
    if [ ! -f "$jar" ]; then
        echo "[ERROR] Missing required build jar: $jar" >&2
        exit 1
    fi
done

SMOKE_MIN_API="$(sed -n 's/^MIN_API=//p' "$PROJECT_ROOT/config/app.env" | tr -d '"' | tr -d "'" | head -1)"
SMOKE_MIN_API="${SMOKE_MIN_API:-24}"
case "$SMOKE_MIN_API" in
    ''|*[!0-9]*)
        echo "[ERROR] MIN_API in config/app.env is not a decimal integer: $SMOKE_MIN_API" >&2
        exit 1
        ;;
esac

rm -rf "$BUILD_ROOT"
mkdir -p "$BUILD_ROOT"

run_flavor() {
    local flavor="$1"
    local dev_mode="$2"
    local build_dir="$BUILD_ROOT/$flavor"

    echo "== Smoke: $flavor =="
    mkdir -p "$build_dir/staging/assets"

    DEV_MODE="$dev_mode" "$PYTHON" "$PROJECT_ROOT/modules/builder/01_gen_config.py" "$build_dir"
    bash "$PROJECT_ROOT/modules/builder/02_r_java.sh" \
        "$build_dir/gen" "$build_dir/AndroidManifest.xml" "$RES_DIR" "$ANDROID_JAR" "$build_dir/res"
    bash "$PROJECT_ROOT/modules/builder/03_compile_java.sh" \
        "$build_dir/obj" "$build_dir/gen" "$SRC_DIR" "$ANDROID_JAR"
    bash "$PROJECT_ROOT/modules/builder/04_dex.sh" \
        "$build_dir/dex" "$build_dir/obj" "$R8_JAR" "$ANDROID_JAR" "$SMOKE_MIN_API"
    # This is deliberately a compile/package smoke check with an empty runtime
    # staging directory: --compile-only keeps it from claiming asset proof.
    bash "$PROJECT_ROOT/modules/builder/06_package.sh" \
        "$build_dir/unaligned.apk" "$build_dir/AndroidManifest.xml" "$RES_DIR" "$ANDROID_JAR" \
        "$build_dir/staging/assets" "$build_dir/dex" "$build_dir/res" --compile-only
    "$PYTHON" "$PROJECT_ROOT/modules/builder/align.py" \
        "$build_dir/unaligned.apk" "$build_dir/aligned.apk" 4

    for artifact in "$build_dir/AndroidManifest.xml" "$build_dir/dex/classes.dex" "$build_dir/aligned.apk"; do
        if [ ! -s "$artifact" ]; then
            echo "[ERROR] Smoke artifact missing or empty: $artifact" >&2
            exit 1
        fi
    done
    echo "[OK] Smoke: $flavor"
}

run_flavor release 0
run_flavor dev 1

echo
echo "Build smoke result: PASS (compile/package only; runtime assets and signing not covered)"
echo "Artifacts: $BUILD_ROOT/{release,dev}/aligned.apk"
