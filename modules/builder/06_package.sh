#!/usr/bin/env bash
# 06_package.sh - Packages resources, every DEX output, and runtime assets.
#
# Usage:
#   06_package.sh <apk> <manifest> <res> <android.jar> <assets> <dex-file-or-dir> [build-res] [--compile-only]
#
# Runtime packaging fails closed when the staged assets directory or a required
# runtime marker/helper/CA file is missing (R32/A08). --compile-only is the only
# mode that may package an empty asset set, and it is labelled as such.
set -euo pipefail

export ANDROID_DATA="${ANDROID_DATA:-/data}"

POSITIONAL=()
COMPILE_ONLY=0
while [ $# -gt 0 ]; do
    case "$1" in
        --compile-only)
            COMPILE_ONLY=1
            shift
            ;;
        --*)
            echo "[ERROR] [06_package] unknown option: $1" >&2
            exit 2
            ;;
        *)
            POSITIONAL+=("$1")
            shift
            ;;
    esac
done
if [ ${#POSITIONAL[@]} -gt 0 ]; then
    set -- "${POSITIONAL[@]}"
else
    set --
fi

UNALIGNED_APK="${1:-$BUILD_DIR/unaligned.apk}"
MANIFEST="${2:-$APP_DIR/src/main/AndroidManifest.xml}"
RES_DIR="${3:-$APP_DIR/src/main/res}"
ANDROID_JAR="${4:-$TOOLS_DIR/android.jar}"
STAGE_ASSETS="${5:-$BUILD_DIR/staging/assets}"
DEX_INPUT="${6:-$BUILD_DIR/dex}"
BUILD_RES_DIR="${7:-${BUILD_DIR:-}/res}"

RES_ARGS=()
if [ -n "${BUILD_RES_DIR:-}" ] && [ -d "$BUILD_RES_DIR" ]; then
    RES_ARGS+=(-S "$BUILD_RES_DIR")
fi
RES_ARGS+=(-S "$RES_DIR" --auto-add-overlay)

echo "[*] [06_package] Packaging base resources into unaligned APK..."
aapt package -f \
    -M "$MANIFEST" \
    "${RES_ARGS[@]}" \
    -I "$ANDROID_JAR" \
    -F "$UNALIGNED_APK"

if [ "$COMPILE_ONLY" = "1" ]; then
    echo "[*] [06_package] compile-only mode: no runtime assets are required or packaged"
else
    echo "[*] [06_package] Injecting DEX files and validated runtime assets..."
fi

if ! python3 - "$UNALIGNED_APK" "$DEX_INPUT" "$STAGE_ASSETS" "$COMPILE_ONLY" "${FAST_BUILD:-0}" <<'PY'
import glob
import os
import sys
import zipfile

apk_path, dex_input, assets_dir, compile_only, fast_build = sys.argv[1:6]
compile_only = compile_only == "1"
fast_build = fast_build == "1"

REQUIRED_ASSETS = (
    "runtime/runtime.marker",
    "runtime/bin/agy-pty",
    "runtime/certs/ca-certificates.crt",
)


def fail(message):
    print("[ERROR] [06_package] " + message, file=sys.stderr)
    sys.exit(1)


# --- collect every DEX output; never silently drop secondary dex -----------
if os.path.isdir(dex_input):
    dex_files = sorted(
        p for p in glob.glob(os.path.join(dex_input, "classes*.dex"))
        if os.path.isfile(p)
    )
else:
    dex_files = [dex_input]
    parent = os.path.dirname(dex_input) or "."
    for extra in sorted(glob.glob(os.path.join(parent, "classes*.dex"))):
        if os.path.abspath(extra) != os.path.abspath(dex_input) and os.path.isfile(extra):
            dex_files.append(extra)

if not dex_files or not os.path.isfile(dex_files[0]):
    fail("classes.dex missing at %s" % dex_input)

for dex in dex_files:
    if os.path.getsize(dex) == 0:
        fail("DEX output is empty: %s" % dex)
    with open(dex, "rb") as handle:
        if handle.read(4) != b"dex\n":
            fail("DEX output has an invalid header: %s" % dex)

# --- validate runtime assets ----------------------------------------------
asset_files = []
if not compile_only:
    if not os.path.isdir(assets_dir):
        fail("runtime assets directory is missing: %s (use --compile-only for an "
             "intentionally asset-free build)" % assets_dir)
    for required in REQUIRED_ASSETS:
        path = os.path.join(assets_dir, required)
        if not os.path.isfile(path) or os.path.getsize(path) == 0:
            fail("required runtime asset is missing or empty: %s" % path)
    helper = os.path.join(assets_dir, "runtime/bin/agy-pty")
    with open(helper, "rb") as handle:
        # 1 MiB window: .interp/linker strings are not guaranteed in the first
        # page for every toolchain link layout.
        elf = handle.read(1024 * 1024)
    if (len(elf) < 20 or elf[0:4] != b"\x7fELF" or elf[4] != 2
            or elf[5] != 1 or elf[18] != 0xB7 or elf[19] != 0
            or b"/system/bin/linker64" not in elf):
        fail("runtime helper is not a verified AArch64 Android ELF: %s" % helper)
    staging_parent = os.path.dirname(assets_dir)
    for root, _dirs, files in os.walk(assets_dir):
        for name in files:
            full = os.path.join(root, name)
            if os.path.getsize(full) == 0:
                fail("staged runtime asset is empty: %s" % full)
            asset_files.append((full, os.path.relpath(full, staging_parent)))

with zipfile.ZipFile(apk_path, "a", compression=zipfile.ZIP_DEFLATED) as z:
    for dex in dex_files:
        z.write(dex, os.path.basename(dex))

    for full, rel in asset_files:
        if fast_build:
            # --fast advertises stored (uncompressed) assets for quick iteration.
            comp = zipfile.ZIP_STORED
        elif full.endswith((".gz", ".tgz", ".zip", ".png", ".jpg")):
            comp = zipfile.ZIP_STORED
        else:
            comp = zipfile.ZIP_DEFLATED
        z.write(full, rel, compress_type=comp)

print("[OK] [06_package] Packaged %d DEX file(s) and %d asset(s)%s"
      % (len(dex_files), len(asset_files), " [fast: stored assets]" if fast_build else ""))
PY
then
    # Never leave a half-populated APK that could be mistaken for a build result.
    rm -f "$UNALIGNED_APK"
    echo "[ERROR] [06_package] packaging validation failed; no APK was produced" >&2
    exit 1
fi

echo "[OK] [06_package] Created $(stat -c%s "$UNALIGNED_APK") bytes unaligned.apk"
