"""Offline e2e latency from capture.mp4 (speech end → first UI change above bottom band)."""
from __future__ import annotations

import subprocess
from pathlib import Path
from typing import Any

from echo_eval.capture.frames import FrameExtractError, resolve_ffmpeg
from echo_eval.capture.post_speech import _mean_abs_diff, _upper_band_gray
from echo_eval.config import LatencyConfig


class OfflineLatencyError(RuntimeError):
    pass


def _extract_frame_at(
    video: Path,
    out_png: Path,
    *,
    time_sec: float,
    ffmpeg_path: str = "",
) -> Path:
    ff = resolve_ffmpeg(ffmpeg_path)
    out_png.parent.mkdir(parents=True, exist_ok=True)
    t = max(0.0, float(time_sec))
    cmd = [
        ff,
        "-y",
        "-hide_banner",
        "-loglevel",
        "error",
        "-ss",
        f"{t:.3f}",
        "-i",
        str(video),
        "-frames:v",
        "1",
        "-q:v",
        "2",
        str(out_png),
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True, check=False)
    if proc.returncode != 0 or not out_png.is_file():
        err = (proc.stderr or proc.stdout or "").strip()
        raise OfflineLatencyError(f"抽基线帧失败 t={t:.3f}: {err}")
    return out_png


def _extract_fps_segment(
    video: Path,
    out_dir: Path,
    *,
    start_sec: float,
    duration_sec: float,
    fps: float,
    ffmpeg_path: str = "",
    prefix: str = "lat",
) -> list[tuple[Path, float]]:
    """Return (path, offset_from_start_sec) for frames in [start, start+duration)."""
    ff = resolve_ffmpeg(ffmpeg_path)
    out_dir.mkdir(parents=True, exist_ok=True)
    for old in out_dir.glob(f"{prefix}_*.jpg"):
        old.unlink(missing_ok=True)
    pattern = str(out_dir / f"{prefix}_%03d.jpg")
    start = max(0.0, float(start_sec))
    dur = max(0.1, float(duration_sec))
    rate = max(1.0, float(fps))
    cmd = [
        ff,
        "-y",
        "-hide_banner",
        "-loglevel",
        "error",
        "-ss",
        f"{start:.3f}",
        "-i",
        str(video),
        "-t",
        f"{dur:.3f}",
        "-vf",
        f"fps={rate}",
        "-q:v",
        "2",
        pattern,
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True, check=False)
    if proc.returncode != 0:
        err = (proc.stderr or proc.stdout or "").strip()
        raise OfflineLatencyError(f"离线抽帧失败: {err}")
    frames = sorted(out_dir.glob(f"{prefix}_*.jpg"))
    # ffmpeg fps filter: frame k ≈ (k-1)/fps seconds from segment start
    out: list[tuple[Path, float]] = []
    for i, p in enumerate(frames):
        out.append((p, round(i / rate, 3)))
    return out


def measure_latency_offline(
    video_path: Path | str,
    *,
    play_end_offset_sec: float,
    play_start_offset_sec: float | None = None,
    latency: LatencyConfig,
    out_dir: Path | str,
    step_index: int = 1,
    ffmpeg_path: str = "",
) -> dict[str, Any]:
    """
    Compare post-speech frames to a pre-speech (or pre-end) baseline from the same mp4.

    Baseline: frame at play_start_offset (preferred) or just before play_end.
    Search window: [play_end, play_end + window_sec) at latency.fps.
    """
    if not latency.enabled:
        return {
            "enabled": False,
            "status": "skipped",
            "latency_sec": None,
            "action_frame": "",
            "action_offset_sec": None,
            "mode": "offline_video",
        }

    video = Path(video_path)
    if not video.is_file():
        return {
            "enabled": True,
            "status": "error",
            "latency_sec": None,
            "action_frame": "",
            "action_offset_sec": None,
            "mode": "offline_video",
            "error": f"视频不存在: {video}",
        }

    dest = Path(out_dir)
    dest.mkdir(parents=True, exist_ok=True)
    step = int(step_index)
    play_end = max(0.0, float(play_end_offset_sec))
    if play_start_offset_sec is not None:
        baseline_t = max(0.0, float(play_start_offset_sec))
    else:
        baseline_t = max(0.0, play_end - 0.05)

    try:
        baseline = _extract_frame_at(
            video,
            dest / f"step_{step:02d}_baseline.jpg",
            time_sec=baseline_t,
            ffmpeg_path=ffmpeg_path,
        )
        baseline_fp = _upper_band_gray(
            baseline,
            bottom_ratio=float(latency.bottom_band_ratio),
            ffmpeg_path=ffmpeg_path,
        )
        if baseline_fp is None:
            raise OfflineLatencyError("无法计算基线上半屏指纹")

        pairs = _extract_fps_segment(
            video,
            dest,
            start_sec=play_end,
            duration_sec=float(latency.window_sec),
            fps=float(latency.fps),
            ffmpeg_path=ffmpeg_path,
            prefix=f"step_{step:02d}",
        )
    except (OfflineLatencyError, FrameExtractError) as e:
        return {
            "enabled": True,
            "status": "error",
            "latency_sec": None,
            "action_frame": "",
            "action_offset_sec": None,
            "mode": "offline_video",
            "error": str(e),
            "play_end_offset_sec": play_end,
            "baseline_offset_sec": baseline_t,
        }

    thresh = float(latency.change_threshold)
    for path, rel in pairs:
        fp = _upper_band_gray(
            path,
            bottom_ratio=float(latency.bottom_band_ratio),
            ffmpeg_path=ffmpeg_path,
        )
        if fp is None:
            continue
        if _mean_abs_diff(baseline_fp, fp) >= thresh:
            lat = round(float(rel), 1)
            return {
                "enabled": True,
                "status": "measured",
                "latency_sec": lat,
                "action_frame": path.name,
                "action_offset_sec": round(play_end + float(rel), 3),
                "mode": "offline_video",
                "play_end_offset_sec": play_end,
                "baseline_offset_sec": baseline_t,
                "baseline_frame": baseline.name,
                "frame_count": len(pairs),
            }

    return {
        "enabled": True,
        "status": "no_action",
        "latency_sec": None,
        "action_frame": "",
        "action_offset_sec": None,
        "mode": "offline_video",
        "play_end_offset_sec": play_end,
        "baseline_offset_sec": baseline_t,
        "baseline_frame": baseline.name,
        "frame_count": len(pairs),
    }
