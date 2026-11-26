"""Offline ASR frame grab from capture.mp4 (for short-lived on-screen transcripts)."""
from __future__ import annotations

import subprocess
from pathlib import Path
from typing import Any

from echo_eval.capture.frames import resolve_ffmpeg


def extract_asr_frames_from_video(
    video: Path | str,
    out_dir: Path | str,
    *,
    start_sec: float,
    window_sec: float = 8.0,
    interval_sec: float = 0.3,
    ffmpeg_path: str = "",
    step_index: int = 1,
    max_frames: int = 40,
) -> dict[str, Any]:
    """
    Sample stills from [start_sec, start_sec+window_sec) at interval_sec.

    Returns paths + offsets relative to video start (same clock as play_end_offset_sec).
    """
    video_p = Path(video)
    out_p = Path(out_dir)
    out_p.mkdir(parents=True, exist_ok=True)
    if not video_p.is_file():
        return {
            "enabled": True,
            "mode": "offline_video",
            "ok": False,
            "error": f"视频不存在: {video_p}",
            "paths": [],
            "offsets_sec": [],
            "count": 0,
        }

    ff = resolve_ffmpeg(ffmpeg_path)
    start = max(0.0, float(start_sec))
    window = max(0.5, float(window_sec))
    interval = max(0.15, float(interval_sec))
    paths: list[str] = []
    offsets: list[float] = []
    t = 0.0
    i = 0
    while t < window - 1e-6 and i < int(max_frames):
        abs_t = start + t
        out_file = out_p / f"asr_offline_step{int(step_index):02d}_{i:03d}.jpg"
        cmd = [
            ff,
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-ss",
            f"{abs_t:.3f}",
            "-i",
            str(video_p),
            "-frames:v",
            "1",
            "-q:v",
            "3",
            str(out_file),
        ]
        try:
            proc = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        except (OSError, subprocess.TimeoutExpired) as e:
            return {
                "enabled": True,
                "mode": "offline_video",
                "ok": False,
                "error": str(e),
                "paths": paths,
                "offsets_sec": offsets,
                "count": len(paths),
            }
        if proc.returncode == 0 and out_file.is_file() and out_file.stat().st_size > 0:
            paths.append(str(out_file.resolve()))
            offsets.append(round(abs_t, 3))
            i += 1
        t += interval

    return {
        "enabled": True,
        "mode": "offline_video",
        "ok": bool(paths),
        "error": "" if paths else "未抽出 ASR 帧",
        "paths": paths,
        "offsets_sec": offsets,
        "count": len(paths),
        "early_stopped": False,
        "start_sec": start,
        "window_sec": window,
        "interval_sec": interval,
    }
