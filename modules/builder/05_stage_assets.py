#!/usr/bin/env python3
"""
05_stage_assets.py - Stages runtime seeds, certificates, and manifests into build/staging/assets.
"""
import sys
import os
import shutil

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(os.path.dirname(SCRIPT_DIR))
MODULES_DIR = os.path.join(PROJECT_ROOT, "modules")


def stage_assets(staging_dir, no_bypass=True):
    assets_dir = os.path.join(staging_dir, "assets")
    runtime_dst = os.path.join(assets_dir, "runtime")
    os.makedirs(runtime_dst, exist_ok=True)

    print(f"[*] Staging lightweight runtime assets -> {runtime_dst}...")

    # Stage certificates
    certs_src = os.path.join(MODULES_DIR, "core", "certs")
    if os.path.isdir(certs_src):
        certs_dst = os.path.join(runtime_dst, "certs")
        if os.path.exists(certs_dst):
            shutil.rmtree(certs_dst)
        shutil.copytree(certs_src, certs_dst)
        print(f"  + Staged certificates: {certs_src}")

    # Stage seeds
    seed_src = os.path.join(MODULES_DIR, "core", "seed")
    if os.path.isdir(seed_src):
        seed_dst = os.path.join(runtime_dst, "seed")
        if os.path.exists(seed_dst):
            shutil.rmtree(seed_dst)
        shutil.copytree(seed_src, seed_dst)
        print(f"  + Staged seeds: {seed_src}")

    # Stage native PTY helper (agy-pty)
    pty_src = os.path.join(MODULES_DIR, "core", "pty", "agy-pty.c")
    pty_bin = os.path.join(MODULES_DIR, "core", "pty", "agy-pty")
    bin_dst = os.path.join(runtime_dst, "bin")
    os.makedirs(bin_dst, exist_ok=True)
    if os.path.isfile(pty_src):
        if not os.path.isfile(pty_bin) or os.path.getmtime(pty_src) > os.path.getmtime(pty_bin):
            import subprocess
            try:
                subprocess.check_call([
                    "clang", "-O2", "-target", "aarch64-linux-android24",
                    pty_src, "-o", pty_bin
                ])
                subprocess.call(["strip", pty_bin])
            except Exception as e:
                print(f"  ! Warning: could not compile agy-pty: {e}")
        if os.path.isfile(pty_bin):
            shutil.copy2(pty_bin, os.path.join(bin_dst, "agy-pty"))
            print(f"  + Staged native PTY helper: {pty_bin} -> {bin_dst}/agy-pty")

    # Write simple asset marker
    with open(os.path.join(runtime_dst, "runtime.marker"), "w", encoding="utf-8") as f:
        f.write("native-bionic\n")

    print(f"[OK] Staged all assets into {assets_dir}")
    return assets_dir


def main():
    staging_dir = os.path.join(PROJECT_ROOT, "build", "staging")
    if len(sys.argv) > 1 and not sys.argv[1].startswith("-"):
        staging_dir = sys.argv[1]
    stage_assets(staging_dir)


if __name__ == "__main__":
    main()
