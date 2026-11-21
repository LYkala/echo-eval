"""Host-side audio playback (black-box mic pickup on the device)."""
from __future__ import annotations

import shutil
import subprocess
import sys
import time
import wave
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path


class AudioPlayError(RuntimeError):
    pass


@dataclass
class AudioPlayResult:
    path: str
    backend: str
    started_at: str
    finished_at: str
    duration_sec: float


def wav_duration_sec(path: Path) -> float:
    with wave.open(str(path), "rb") as w:
        frames = w.getnframes()
        rate = w.getframerate() or 1
        return frames / float(rate)


def _resolve_ffplay(ffmpeg_path: str = "") -> str | None:
    if ffmpeg_path:
        p = Path(ffmpeg_path)
        # if user pointed at ffmpeg.exe, try sibling ffplay
        if p.name.lower().startswith("ffmpeg"):
            cand = p.with_name(p.name.replace("ffmpeg", "ffplay").replace("FFMPEG", "ffplay"))
            if cand.is_file():
                return str(cand)
        if p.is_file() and "ffplay" in p.name.lower():
            return str(p)
    return shutil.which("ffplay")


def play_audio_file(
    path: Path | str,
    *,
    ffmpeg_path: str = "",
    timeout_sec: float | None = None,
) -> AudioPlayResult:
    audio = Path(path)
    if not audio.is_file():
        raise AudioPlayError(f"音频文件不存在: {audio}")

    started = datetime.now(timezone.utc).isoformat()
    t0 = time.time()
    backend = ""

    ffplay = _resolve_ffplay(ffmpeg_path)
    if ffplay:
        backend = "ffplay"
        cmd = [ffplay, "-nodisp", "-autoexit", "-loglevel", "error", str(audio)]
        proc = subprocess.run(cmd, capture_output=True, text=True, check=False)
        if proc.returncode != 0:
            err = (proc.stderr or proc.stdout or "").strip()
            raise AudioPlayError(f"ffplay 播放失败: {err}")
    elif sys.platform.startswith("win"):
        # winsound is sync for SND_FILENAME|SND_SYNC
        backend = "winsound"
        try:
            import winsound  # type: ignore
        except ImportError as e:
            raise AudioPlayError("Windows 上无 ffplay，且无法导入 winsound") from e
        # winsound only reliably plays PCM wav
        if audio.suffix.lower() != ".wav":
            raise AudioPlayError("无 ffplay 时 Windows 回退仅支持 .wav，请安装 ffmpeg/ffplay")
        flags = getattr(winsound, "SND_FILENAME", 0x00020000) | getattr(
            winsound, "SND_SYNC", 0x0000
        )
        winsound.PlaySound(str(audio), flags)
    else:
        raise AudioPlayError(
            "未找到 ffplay。请安装 ffmpeg（含 ffplay）并加入 PATH，"
            "或在 configs/device.yaml 设置 capture.ffmpeg_path。"
        )

    finished = datetime.now(timezone.utc).isoformat()
    elapsed = time.time() - t0
    if timeout_sec is not None and elapsed > timeout_sec + 1:
        # informational only; play already finished
        pass
    try:
        dur = wav_duration_sec(audio) if audio.suffix.lower() == ".wav" else elapsed
    except Exception:
        dur = elapsed
    return AudioPlayResult(
        path=str(audio.resolve()),
        backend=backend,
        started_at=started,
        finished_at=finished,
        duration_sec=round(dur, 3),
    )


def resolve_audio_path(path: str, *, repo_root: Path) -> Path:
    p = Path(path)
    if not p.is_absolute():
        p = repo_root / p
    return p.resolve()
