#!/usr/bin/env python3
"""PulseAudio virtual mic helpers for AISF voice test on Linux/EulerOS."""

from __future__ import annotations

import json
import os
import subprocess
import threading
import time
from pathlib import Path


def _run(cmd: list[str], *, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(cmd, capture_output=True, text=True, check=check)


def _adb_bin() -> str:
    sdk = os.environ.get("ANDROID_SDK_ROOT", str(Path.home() / "android-sdk-aisf"))
    candidate = Path(sdk) / "platform-tools" / "adb"
    if candidate.is_file():
        return str(candidate)
    return "adb"


def status() -> dict:
    info: dict = {"pulseaudio": False, "virtual_mic": os.environ.get("AISF_VIRTUAL_MIC", "VirtualMic"), "adb": _adb_bin()}
    try:
        proc = _run(["pactl", "info"])
        info["pulseaudio"] = proc.returncode == 0
        if proc.returncode == 0:
            src = _run(["pactl", "get-default-source"], check=False)
            info["default_source"] = (src.stdout or "").strip()
    except (FileNotFoundError, subprocess.CalledProcessError):
        pass
    return info


def is_ready() -> bool:
    data = status()
    sink = data.get("virtual_mic", "VirtualMic")
    if not data.get("pulseaudio"):
        return False
    # Multi-AVD: each clone has its own sink; default-source may point elsewhere.
    try:
        sinks = _run(["pactl", "list", "short", "sinks"], check=False)
        return sink in (sinks.stdout or "")
    except (FileNotFoundError, subprocess.CalledProcessError):
        default = data.get("default_source", "")
        return sink in default


def ensure_hostmicon_enabled(serial: str | None = None) -> None:
    serial = serial or os.environ.get("AISF_SERIAL", "emulator-5554")
    _run([_adb_bin(), "-s", serial, "emu", "avd", "hostmicon"], check=False)


def ensure_virtual_mic(*, serial: str | None = None, force: bool = False) -> dict:
    """Check Pulse VirtualMic; repair via setup_virtual_mic.sh when needed; always hostmicon.

    Call this immediately before paplay / voice inject.
    """
    lab = Path(os.environ.get("AISF_LAB_ROOT", str(Path(__file__).resolve().parent)))
    setup = lab / "setup_virtual_mic.sh"
    ready_before = is_ready()
    fixed = False
    setup_log = ""
    if force or not ready_before:
        if not setup.is_file():
            raise RuntimeError(f"missing {setup}")
        env = os.environ.copy()
        env.setdefault("PULSE_RUNTIME_PATH", "/tmp/pulse-root")
        env.setdefault("XDG_RUNTIME_DIR", "/tmp/pulse-root")
        proc = subprocess.run(
            ["bash", str(setup), "--ensure"],
            cwd=str(lab),
            capture_output=True,
            text=True,
            env=env,
            check=False,
        )
        setup_log = ((proc.stdout or "") + (proc.stderr or "")).strip()[-500:]
        if proc.returncode != 0:
            raise RuntimeError(setup_log or f"setup_virtual_mic exit {proc.returncode}")
        fixed = True
    ensure_hostmicon_enabled(serial)
    ready_after = is_ready()
    return {
        "ready_before": ready_before,
        "ready_after": ready_after,
        "fixed": fixed,
        "setup_log": setup_log,
    }


def configure_emulator_mic_inject(serial: str | None = None) -> None:
    ensure_virtual_mic(serial=serial, force=False)


def play_to_virtual_mic(
    audio_path: Path,
    *,
    sink: str | None = None,
    ensure: bool = True,
) -> None:
    if ensure:
        ensure_virtual_mic(force=False)
    audio_path = audio_path.resolve()
    if not audio_path.is_file():
        raise FileNotFoundError(audio_path)
    sink = sink or os.environ.get("AISF_VIRTUAL_MIC", "VirtualMic")
    proc = _run(["paplay", f"--device={sink}", str(audio_path)], check=False)
    if proc.returncode != 0:
        raise RuntimeError((proc.stderr or proc.stdout or "paplay failed").strip())


def play_loop_async(audio_path: Path, stop_event: threading.Event, *, sink: str | None = None) -> threading.Thread:
    ensure_virtual_mic(force=False)

    def _loop() -> None:
        while not stop_event.is_set():
            play_to_virtual_mic(audio_path, sink=sink, ensure=False)
            if stop_event.wait(0.25):
                break

    t = threading.Thread(target=_loop, daemon=True)
    t.start()
    return t


def record_default_sink(out_wav: Path, duration_sec: float) -> Path:
    out_wav.parent.mkdir(parents=True, exist_ok=True)
    proc = subprocess.run(
        [
            "ffmpeg",
            "-y",
            "-hide_banner",
            "-loglevel",
            "error",
            "-f",
            "pulse",
            "-i",
            "default",
            "-t",
            str(duration_sec),
            str(out_wav),
        ],
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0:
        raise RuntimeError((proc.stderr or proc.stdout or "ffmpeg record failed").strip())
    if not out_wav.is_file() or out_wav.stat().st_size < 1000:
        raise RuntimeError(f"record output too small: {out_wav}")
    return out_wav


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Linux virtual mic for AISF tests")
    parser.add_argument("--status", action="store_true")
    parser.add_argument("--hostmicon", action="store_true")
    parser.add_argument("--ensure", action="store_true", help="diagnose+fix VirtualMic then hostmicon")
    parser.add_argument("-i", "--input", type=Path)
    args = parser.parse_args()
    if args.status:
        print(json.dumps(status(), ensure_ascii=False, indent=2))
    elif args.ensure:
        print(json.dumps(ensure_virtual_mic(force=False), ensure_ascii=False, indent=2))
    elif args.hostmicon:
        configure_emulator_mic_inject()
        print("hostmicon enabled")
    elif args.input:
        play_to_virtual_mic(args.input)
        print("played")
    else:
        parser.print_help()
