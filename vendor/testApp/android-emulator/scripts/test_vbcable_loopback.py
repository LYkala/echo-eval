#!/usr/bin/env python3
"""Quick VB-Cable play+record loopback check (host wav -> CABLE Input -> CABLE Output)."""

from __future__ import annotations

import os
import struct
import subprocess
import sys
import time
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UTILS = ROOT / "scripts" / "vbcable_utils.ps1"
SAMPLE = ROOT / "audio_samples" / "weather_question.wav"
OUT = ROOT / "app_apk_test" / "_loopback_check.wav"


def main() -> int:
    ps = f"""
$ErrorActionPreference = 'Stop'
. '{UTILS.as_posix()}'
$ffmpeg = Find-Ffmpeg
if (-not $ffmpeg) {{ throw 'ffmpeg missing' }}
Ensure-AudioDeviceCmdlets
Save-DefaultAudioDevices
$dev = Get-CableDeviceNames
$pb = Get-AudioDevice -List | Where-Object {{ $_.Type -eq 'Playback' -and $_.Name -eq $dev.Playback }} | Select-Object -First 1
Set-AudioDevice -Index $pb.Index | Out-Null
Write-Output "FFMPEG=$ffmpeg"
Write-Output "CAP=$($dev.Capture)"
"""
    proc = subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=True,
    )
    ffmpeg = None
    capture_name = None
    for ln in (proc.stdout or "").splitlines():
        ln = ln.strip()
        if ln.startswith("FFMPEG="):
            ffmpeg = Path(ln.split("=", 1)[1])
        elif ln.startswith("CAP="):
            capture_name = ln.split("=", 1)[1]
    if not ffmpeg or not ffmpeg.is_file() or not capture_name:
        raise RuntimeError(f"setup failed: {(proc.stdout or '')}")
    device = f"audio={capture_name}"

    env = os.environ.copy()
    env["PATH"] = str(ffmpeg.parent) + os.pathsep + env.get("PATH", "")
    OUT.parent.mkdir(parents=True, exist_ok=True)

    rec = subprocess.Popen(
        [
            str(ffmpeg),
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-f",
            "dshow",
            "-i",
            device,
            "-t",
            "8",
            str(OUT),
        ],
        stderr=subprocess.PIPE,
        env=env,
    )
    time.sleep(1)
    play_ps = f"""
$ErrorActionPreference = 'Stop'
. '{UTILS.as_posix()}'
Play-WavToCableInput -AudioPath '{SAMPLE.as_posix()}'
"""
    subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", play_ps],
        check=True,
    )
    rc = rec.wait(timeout=15)
    if rc != 0:
        err = (rec.stderr.read() if rec.stderr else b"").decode("utf-8", errors="replace")
        print(err, file=sys.stderr)
        return 1

    subprocess.run(
        [
            "powershell",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-Command",
            f". '{UTILS.as_posix()}'; Restore-DefaultAudioDevices",
        ],
        check=False,
    )

    with wave.open(str(OUT), "rb") as w:
        raw = w.readframes(w.getnframes())
        samples = struct.unpack("<" + "h" * (len(raw) // 2), raw)
    peak = max(abs(s) for s in samples) if samples else 0
    print(f"peak={peak} file={OUT}")
    return 0 if peak > 5000 else 2


if __name__ == "__main__":
    raise SystemExit(main())
