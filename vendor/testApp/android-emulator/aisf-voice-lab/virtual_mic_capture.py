#!/usr/bin/env python3
"""Capture Pulse VirtualMic → adb push PCM inject into VoiceAssistant."""
from __future__ import annotations

import os
import subprocess
import sys
import time
from pathlib import Path

LAB = Path(os.environ.get("LAB", "/root/aisf-voice-lab"))
WAV = Path(os.environ.get("VMIC_WAV", str(LAB / "input" / "weather_question_16k.wav")))
PCM = Path(os.environ.get("VMIC_PCM", "/tmp/vmic_inject.pcm"))
SOURCE = os.environ.get("VMIC_SOURCE", "VirtualMic.monitor")
GUEST_DIR = os.environ.get(
    "VMIC_GUEST_DIR",
    "/sdcard/Android/data/com.testappgui.voiceassistant/files/voice_lab",
)


def main() -> int:
    os.environ.setdefault("PULSE_RUNTIME_PATH", "/tmp/pulse-root")
    os.environ.setdefault("XDG_RUNTIME_DIR", "/tmp/pulse-root")
    PCM.unlink(missing_ok=True)
    proc = subprocess.Popen(
        [
            "parec",
            f"--device={SOURCE}",
            "--rate=16000",
            "--channels=1",
            "--format=s16le",
            "--latency-msec=20",
            "--raw",
        ],
        stdout=open(PCM, "wb"),
        stderr=subprocess.DEVNULL,
    )
    try:
        time.sleep(0.3)
        subprocess.run(
            ["paplay", f"--device=VirtualMic", str(WAV)],
            check=False,
        )
        time.sleep(0.15)
        subprocess.run(
            ["paplay", f"--device=VirtualMic", str(WAV)],
            check=False,
        )
        time.sleep(0.3)
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=2)
        except Exception:
            proc.kill()
    size = PCM.stat().st_size if PCM.exists() else 0
    print(f"captured_bytes={size}", flush=True)
    if size < 3200:
        print("capture too small", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
