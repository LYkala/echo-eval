#!/usr/bin/env python3
"""Play audio into VB-Cable Input; configure CABLE Output as default mic."""

from __future__ import annotations

import json
import subprocess
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SCRIPTS = ROOT / "scripts"
UTILS = SCRIPTS / "vbcable_utils.ps1"


def _ps_path(path: Path) -> str:
    return str(path.resolve()).replace("'", "''")


def _ps_json(script: str) -> dict | list | str | bool | None:
    proc = subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if proc.returncode != 0:
        err = (proc.stderr or proc.stdout or "").strip()
        raise RuntimeError(err or f"PowerShell failed ({proc.returncode})")
    out = (proc.stdout or "").strip()
    if not out:
        return None
    return json.loads(out)


def status() -> dict:
    ps = f"""
$ErrorActionPreference = 'Stop'
. '{_ps_path(UTILS)}'
[PSCustomObject]@{{
  installed = [bool](Test-VBCableInstalled)
  devices = (Get-CableDeviceNames)
  ffmpeg = (Find-Ffmpeg)
}} | ConvertTo-Json -Depth 4
"""
    data = _ps_json(ps)
    assert isinstance(data, dict)
    return data


def is_ready() -> bool:
    try:
        return bool(status().get("installed"))
    except (RuntimeError, OSError, json.JSONDecodeError):
        return False


def configure_default_microphone() -> None:
    ps = f"""
$ErrorActionPreference = 'Stop'
. '{_ps_path(UTILS)}'
Set-VBCableDefaultMicrophone | Out-Null
"""
    subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps],
        check=True,
    )


def ensure_hostmicon_enabled() -> None:
    """Enable host mic injection (do not toggle hostmicoff; it can break routing)."""
    adb_bin = Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe"
    serial = "emulator-5554"
    subprocess.run(
        [str(adb_bin), "-s", serial, "emu", "avd", "hostmicon"],
        capture_output=True,
        text=True,
        check=False,
    )


def configure_emulator_mic_inject() -> None:
    configure_default_microphone()
    ensure_hostmicon_enabled()


def restore_default_devices() -> None:
    ps = f"""
$ErrorActionPreference = 'Stop'
. '{_ps_path(UTILS)}'
Restore-DefaultAudioDevices
"""
    subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps],
        check=False,
    )


def play_to_cable(audio_path: Path, *, temporary_default_playback: bool = True) -> None:
    audio_path = audio_path.resolve()
    if not audio_path.is_file():
        raise FileNotFoundError(audio_path)
    play_ps1 = SCRIPTS / "play_to_vbcable.ps1"
    cmd = [
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        str(play_ps1),
        "-AudioPath",
        str(audio_path),
    ]
    if temporary_default_playback:
        cmd.append("-TemporaryDefaultPlayback")
    proc = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if proc.returncode != 0:
        err = (proc.stderr or proc.stdout or "").strip()
        raise RuntimeError(err or "play_to_vbcable.ps1 failed")


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="VB-Cable play / configure")
    parser.add_argument("--status", action="store_true")
    parser.add_argument("--configure", action="store_true")
    parser.add_argument("--restore", action="store_true")
    parser.add_argument("-i", "--input", type=Path)
    args = parser.parse_args()
    if args.status:
        print(json.dumps(status(), ensure_ascii=False, indent=2))
    elif args.restore:
        restore_default_devices()
        print("restored")
    elif args.configure:
        configure_default_microphone()
        print("configured")
    elif args.input:
        play_to_cable(args.input)
        print("played")
    else:
        parser.print_help()
