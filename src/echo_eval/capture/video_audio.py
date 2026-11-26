"""Extract audio segments from capture.mp4 for MOS (scrcpy internal audio)."""
from __future__ import annotations

import subprocess
from pathlib import Path

from echo_eval.capture.frames import resolve_ffmpeg
from echo_eval.capture.scrcpy_record import probe_has_audio


class VideoAudioError(RuntimeError):
    pass


def extract_wav_segment(
    video: Path | str,
    out_wav: Path | str,
    *,
    start_sec: float,
    duration_sec: float,
    sample_rate: int = 16000,
    ffmpeg_path: str = "",
) -> Path:
    """Cut [start_sec, start_sec+duration_sec) audio from video into 16-bit PCM wav."""
    video_p = Path(video)
    out_p = Path(out_wav)
    if not video_p.is_file():
        raise VideoAudioError(f"视频不存在: {video_p}")
    if not probe_has_audio(video_p, ffmpeg_path=ffmpeg_path):
        raise VideoAudioError(f"视频无音轨，无法抽取 MOS: {video_p}")
    out_p.parent.mkdir(parents=True, exist_ok=True)
    if out_p.exists():
        out_p.unlink()
    ff = resolve_ffmpeg(ffmpeg_path)
    start = max(0.0, float(start_sec))
    dur = max(0.05, float(duration_sec))
    cmd = [
        ff,
        "-y",
        "-ss",
        f"{start:.3f}",
        "-t",
        f"{dur:.3f}",
        "-i",
        str(video_p),
        "-vn",
        "-acodec",
        "pcm_s16le",
        "-ar",
        str(int(sample_rate)),
        "-ac",
        "1",
        str(out_p),
    ]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    except (OSError, subprocess.TimeoutExpired) as e:
        raise VideoAudioError(f"ffmpeg 抽音失败: {e}") from e
    if proc.returncode != 0 or not out_p.is_file() or out_p.stat().st_size < 44:
        err = (proc.stderr or proc.stdout or "").strip()
        raise VideoAudioError(f"ffmpeg 抽音失败: {err or proc.returncode}")
    return out_p
