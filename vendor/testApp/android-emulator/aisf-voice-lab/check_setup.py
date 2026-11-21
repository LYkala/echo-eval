#!/usr/bin/env python3
"""Check AISF voice lab prerequisites on EulerOS / Linux aarch64."""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

LAB_ROOT = Path(__file__).resolve().parent
EMULATOR_ROOT = LAB_ROOT.parent


def ok(msg: str) -> None:
    print(f"[ok] {msg}")


def warn(msg: str) -> None:
    print(f"[warn] {msg}")


def fail(msg: str) -> None:
    print(f"[fail] {msg}")


def have(cmd: str) -> bool:
    return shutil.which(cmd) is not None


def check_java() -> bool:
    if not have("java"):
        fail("java not found. Install: sudo yum install -y java-17-openjdk")
        return False
    proc = subprocess.run(["java", "-version"], capture_output=True, text=True)
    line = (proc.stderr or proc.stdout or "").splitlines()[0]
    ok(f"java: {line}")
    return True


def check_tools() -> bool:
    good = True
    for cmd in ("curl", "python3"):
        if have(cmd):
            ok(cmd)
        else:
            fail(f"{cmd} missing")
            good = False
    if have("unzip"):
        ok("unzip")
    else:
        warn("unzip missing (only needed for some SDK zip installs)")
    for cmd, pkg in (("pactl", "pulseaudio-utils"), ("paplay", "pulseaudio"), ("ffmpeg", "ffmpeg")):
        if have(cmd):
            ok(cmd)
        else:
            warn(f"{cmd} missing (install {pkg})")
    return good


def check_sdk(quick: bool) -> bool:
    sdk = Path(os.environ.get("ANDROID_SDK_ROOT", str(LAB_ROOT / "sdk")))
    adb = sdk / "platform-tools" / "adb"
    emu = sdk / "emulator" / "emulator"
    sdkmanager = sdk / "cmdline-tools" / "latest" / "bin" / "sdkmanager"
    good = True
    if adb.is_file() or have("adb"):
        ok(f"adb: {adb if adb.is_file() else shutil.which('adb')}")
    else:
        fail("adb missing. Run: bash download_deps.sh")
        good = False
    if quick:
        return good
    if emu.is_file():
        ok(f"emulator: {emu}")
    else:
        fail("emulator missing. Run: bash download_deps.sh")
        good = False
    if sdkmanager.is_file():
        ok(f"sdkmanager: {sdkmanager}")
    else:
        fail("sdkmanager missing. Run: bash download_deps.sh")
        good = False
    return good


def check_kvm() -> None:
    if Path("/dev/kvm").exists():
        ok("/dev/kvm present (hardware accel available)")
    else:
        warn("/dev/kvm missing (emulator will be slow)")


def check_apk() -> bool:
    candidates = [
        LAB_ROOT / "apk" / "aisf_voice_assistant.apk",
        EMULATOR_ROOT / "aisf_voice_assistant_tv-phoneRelease-20260715_103645.apk",
    ]
    for path in candidates:
        if path.is_file():
            ok(f"AISF apk: {path} ({path.stat().st_size // (1024 * 1024)} MB)")
            return True
    fail("AISF apk missing. Copy to aisf-voice-lab/apk/aisf_voice_assistant.apk")
    return False


def check_sample() -> bool:
    sample = LAB_ROOT / "input" / "weather_question.wav"
    if not sample.is_file():
        sample = EMULATOR_ROOT / "audio_samples" / "weather_question.wav"
    if sample.is_file():
        ok(f"input wav: {sample}")
        return True
    fail(f"sample wav missing: {sample}")
    return False


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--quick", action="store_true")
    args = parser.parse_args()
    print("=== AISF voice lab check ===")
    results = [
        check_java(),
        check_tools(),
        check_apk(),
        check_sample(),
        check_sdk(args.quick),
    ]
    check_kvm()
    if all(results):
        print("\nReady. Next steps:")
        print("  source ./env.sh")
        print("  bash download_deps.sh   # if sdk not complete")
        print("  bash run_e2e.sh")
        return 0
    print("\nFix the [fail] items above, then retry.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
