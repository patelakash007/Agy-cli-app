#!/usr/bin/env python3
"""
05_stage_assets.py - Stages verified runtime certificates and a freshly built
native PTY helper into <staging>/assets/runtime.

A03/R17: the helper is always compiled from modules/core/pty/agy-pty.c into
build/native/arm64-v8a. The tracked source-tree binary is never used as input or
mutated, and a missing/failed toolchain fails the build instead of warning and
shipping stale bytes. Fabricated vendor seed state is no longer staged (B01b).
"""
import os
import shutil
import subprocess
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(os.path.dirname(SCRIPT_DIR))
MODULES_DIR = os.path.join(PROJECT_ROOT, "modules")
HELPER_SRC = os.path.join(MODULES_DIR, "core", "pty", "agy-pty.c")
CERTS_SRC = os.path.join(MODULES_DIR, "core", "certs")

MAX_ELF_BYTES = 32 * 1024 * 1024
EM_AARCH64 = 0xB7
ANDROID_LINKER = b"/system/bin/linker64"


def fail(message):
    print("[ERROR] [05_stage_assets] " + message, file=sys.stderr)
    raise SystemExit(1)


def find_clang():
    explicit = os.environ.get("AGY_CLANG")
    if explicit:
        return explicit
    for key in ("ANDROID_NDK_HOME", "ANDROID_NDK_ROOT"):
        root = os.environ.get(key)
        if not root:
            continue
        prebuilt = os.path.join(root, "toolchains", "llvm", "prebuilt")
        if not os.path.isdir(prebuilt):
            continue
        for host in sorted(os.listdir(prebuilt)):
            candidate = os.path.join(
                prebuilt, host, "bin", "aarch64-linux-android24-clang")
            if os.path.isfile(candidate):
                return candidate
    for name in ("aarch64-linux-android24-clang", "clang"):
        path = shutil.which(name)
        if path:
            return path
    return None


def verify_aarch64_elf(path):
    if not os.path.isfile(path):
        fail("native helper output is missing: %s" % path)
    size = os.path.getsize(path)
    if size <= 20 or size > MAX_ELF_BYTES:
        fail("native helper has an implausible size: %d bytes" % size)
    with open(path, "rb") as handle:
        data = handle.read(MAX_ELF_BYTES)
    if data[0:4] != b"\x7fELF":
        fail("native helper is not an ELF file: %s" % path)
    if data[4] != 2:
        fail("native helper is not ELF64: %s" % path)
    if data[5] != 1:
        fail("native helper is not little-endian: %s" % path)
    if data[18] != EM_AARCH64 or data[19] != 0:
        fail("native helper is not AArch64 (e_machine=%02x%02x)"
             % (data[19], data[18]))
    if ANDROID_LINKER not in data:
        fail("native helper is not linked for Android (%s missing)" % ANDROID_LINKER)
    verify_page_alignment(data, path)
    return True


def verify_page_alignment(data, path):
    """Require every PT_LOAD segment to be 16 KB page compatible (A03)."""
    if len(data) < 64:
        fail("native helper ELF header is truncated: %s" % path)
    phoff = int.from_bytes(data[32:40], "little")
    phentsize = int.from_bytes(data[54:56], "little")
    phnum = int.from_bytes(data[56:58], "little")
    if phentsize < 56 or phnum == 0:
        fail("native helper has no usable program headers: %s" % path)
    if phoff + phnum * phentsize > len(data):
        fail("native helper program headers are truncated: %s" % path)
    saw_load = False
    for index in range(phnum):
        offset = phoff + index * phentsize
        p_type = int.from_bytes(data[offset:offset + 4], "little")
        p_align = int.from_bytes(data[offset + 48:offset + 56], "little")
        if p_type == 1:  # PT_LOAD
            saw_load = True
            if p_align < 16384:
                fail("native helper is not 16 KB page compatible (p_align=%d): %s"
                     % (p_align, path))
    if not saw_load:
        fail("native helper has no PT_LOAD segment: %s" % path)


def build_pty_helper(native_dir):
    if not os.path.isfile(HELPER_SRC):
        fail("PTY helper source is missing: %s" % HELPER_SRC)
    clang = find_clang()
    if not clang:
        fail("no Android NDK clang found. Set AGY_CLANG or ANDROID_NDK_HOME; "
             "this build refuses to reuse an unchecked tracked binary.")
    os.makedirs(native_dir, exist_ok=True)
    output = os.path.join(native_dir, "agy-pty")
    if os.path.exists(output):
        os.remove(output)
    command = [
        clang, "-O2", "-fPIE", "-pie", "-Wall", "-Wextra",
        "-Wl,-z,max-page-size=16384",
        "-target", "aarch64-linux-android24",
        HELPER_SRC, "-o", output,
    ]
    print("  + Compiling PTY helper: %s" % " ".join(command))
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        print(result.stdout, file=sys.stderr)
        print(result.stderr, file=sys.stderr)
        fail("PTY helper compilation failed (exit %d)" % result.returncode)
    verify_aarch64_elf(output)
    os.chmod(output, 0o755)
    print("  + Verified AArch64 Android ELF: %s" % output)
    return output


def stage_assets(staging_dir, no_bypass=True):
    assets_dir = os.path.join(staging_dir, "assets")
    runtime_dst = os.path.join(assets_dir, "runtime")
    os.makedirs(runtime_dst, exist_ok=True)

    print("[*] Staging verified runtime assets -> %s..." % runtime_dst)

    ca_source = os.path.join(CERTS_SRC, "ca-certificates.crt")
    if not os.path.isfile(ca_source) or os.path.getsize(ca_source) == 0:
        fail("bundled CA bundle is missing or empty: %s" % ca_source)
    certs_dst = os.path.join(runtime_dst, "certs")
    if os.path.exists(certs_dst):
        shutil.rmtree(certs_dst)
    shutil.copytree(CERTS_SRC, certs_dst)
    print("  + Staged certificates: %s" % CERTS_SRC)

    native_dir = os.path.join(PROJECT_ROOT, "build", "native", "arm64-v8a")
    helper = build_pty_helper(native_dir)
    bin_dst = os.path.join(runtime_dst, "bin")
    os.makedirs(bin_dst, exist_ok=True)
    shutil.copy2(helper, os.path.join(bin_dst, "agy-pty"))
    os.chmod(os.path.join(bin_dst, "agy-pty"), 0o755)
    print("  + Staged native PTY helper: %s -> %s/agy-pty" % (helper, bin_dst))

    # Vendor onboarding state is user-owned; no fabricated seed assets (B01b).
    with open(os.path.join(runtime_dst, "runtime.marker"), "w", encoding="utf-8") as handle:
        handle.write("native-bionic\n")

    print("[OK] Staged runtime assets into %s" % assets_dir)
    return assets_dir


def main():
    staging_dir = os.path.join(PROJECT_ROOT, "build", "staging")
    if len(sys.argv) > 1 and not sys.argv[1].startswith("-"):
        staging_dir = sys.argv[1]
    stage_assets(staging_dir)


if __name__ == "__main__":
    main()
