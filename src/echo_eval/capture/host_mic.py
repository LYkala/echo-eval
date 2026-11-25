"""Host microphone capture for device TTS MOS (screenrecord has no audio)."""
from __future__ import annotations

import shutil
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any


class HostMicError(RuntimeError):
    pass


@dataclass
class HostMicRecording:
    path: str
    backend: str
    started_at: float
    duration_sec: float
    ok: bool
    error: str = ""


def resolve_ffmpeg_bin(ffmpeg_path: str = "") -> str:
    if ffmpeg_path:
        p = Path(ffmpeg_path)
        if p.is_file():
            return str(p)
    found = shutil.which("ffmpeg")
    if not found:
        raise HostMicError("未找到 ffmpeg，无法录制主机麦克风")
    return found


def _default_input(platform: str) -> tuple[str, str]:
    """Return (ffmpeg -f format, -i input)."""
    if platform.startswith("win"):
        return "dshow", "audio=default"
    if platform == "darwin":
        return "avfoundation", ":0"
    # linux
    if shutil.which("pactl") or Path("/usr/bin/pulseaudio").exists():
        return "pulse", "default"
    return "alsa", "default"


def normalize_dshow_audio_device(device: str) -> str:
    """Ensure Windows dshow mic uses audio=… (plain display names break ffmpeg)."""
    d = (device or "").strip()
    if not d:
        return d
    low = d.lower()
    if low.startswith("audio=") or low.startswith("video="):
        return d
    return f"audio={d}"


def start_host_mic_record(
    out_wav: Path,
    *,
    duration_sec: float,
    ffmpeg_path: str = "",
    input_format: str = "",
    input_device: str = "",
    sample_rate: int = 16000,
) -> tuple[subprocess.Popen[bytes], HostMicRecording]:
    """
    Start ffmpeg recording host mic to wav; caller should wait/communicate.

    Windows: set mos.input_format=dshow and mos.input_device to the device name
    from `ffmpeg -list_devices true -f dshow -i dummy` (audio=Name or bare name).
    """
    out_wav = Path(out_wav)
    out_wav.parent.mkdir(parents=True, exist_ok=True)
    if out_wav.exists():
        out_wav.unlink()
    ff = resolve_ffmpeg_bin(ffmpeg_path)
    fmt = (input_format or "").strip()
    dev = (input_device or "").strip()
    if not fmt or not dev:
        fmt, dev = _default_input(sys.platform)
    if fmt == "dshow":
        dev = normalize_dshow_audio_device(dev)
    dur = max(0.5, float(duration_sec))
    sr = max(8000, int(sample_rate))
    cmd = [
        ff,
        "-y",
        "-hide_banner",
        "-loglevel",
        "error",
        "-f",
        fmt,
        "-i",
        dev,
        "-t",
        f"{dur:.2f}",
        "-ac",
        "1",
        "-ar",
        str(sr),
        "-c:a",
        "pcm_s16le",
        str(out_wav),
    ]
    started = time.time()
    try:
        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
        )
    except OSError as e:
        raise HostMicError(f"无法启动麦克风录制: {e}") from e
    meta = HostMicRecording(
        path=str(out_wav),
        backend=f"ffmpeg:{fmt}",
        started_at=started,
        duration_sec=dur,
        ok=True,
    )
    return proc, meta


def finish_host_mic_record(
    proc: subprocess.Popen[bytes],
    meta: HostMicRecording,
    *,
    timeout_sec: float | None = None,
) -> HostMicRecording:
    wait = (timeout_sec if timeout_sec is not None else meta.duration_sec) + 15.0
    try:
        _, stderr = proc.communicate(timeout=wait)
    except subprocess.TimeoutExpired:
        proc.kill()
        meta.ok = False
        meta.error = "麦克风录制超时"
        return meta
    err = (stderr or b"").decode("utf-8", errors="replace").strip()
    path = Path(meta.path)
    if proc.returncode != 0 or not path.is_file() or path.stat().st_size < 44:
        meta.ok = False
        meta.error = err or f"麦克风录制失败 (code={proc.returncode})"
    return meta


def host_mic_meta(rec: HostMicRecording) -> dict[str, Any]:
    return {
        "path": rec.path,
        "backend": rec.backend,
        "duration_sec": rec.duration_sec,
        "ok": rec.ok,
        "error": rec.error,
    }
