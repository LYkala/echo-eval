"""Extract still frames from a local mp4 via ffmpeg."""
from __future__ import annotations

import shutil
import subprocess
from pathlib import Path


class FrameExtractError(RuntimeError):
    pass


def resolve_ffmpeg(ffmpeg_path: str = "") -> str:
    bin_path = (ffmpeg_path or "").strip() or shutil.which("ffmpeg") or ""
    if not bin_path:
        raise FrameExtractError(
            "未找到 ffmpeg。请安装 ffmpeg 并加入 PATH，"
            "或在 configs/device.yaml 设置 capture.ffmpeg_path；"
            "也可使用 --no-frames 跳过抽帧。"
        )
    return bin_path


def extract_frames(
    video_path: Path,
    frames_dir: Path,
    *,
    fps: float = 1.0,
    ffmpeg_path: str = "",
) -> list[Path]:
    if fps <= 0:
        return []
    video_path = Path(video_path)
    if not video_path.is_file():
        raise FrameExtractError(f"视频不存在: {video_path}")

    frames_dir = Path(frames_dir)
    if frames_dir.exists():
        for old in frames_dir.glob("frame_*.jpg"):
            old.unlink(missing_ok=True)
    frames_dir.mkdir(parents=True, exist_ok=True)

    ffmpeg = resolve_ffmpeg(ffmpeg_path)
    pattern = str(frames_dir / "frame_%03d.jpg")
    cmd = [
        ffmpeg,
        "-y",
        "-i",
        str(video_path),
        "-vf",
        f"fps={fps}",
        "-q:v",
        "2",
        pattern,
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True, check=False)
    if proc.returncode != 0:
        err = (proc.stderr or proc.stdout or "").strip()
        raise FrameExtractError(f"ffmpeg 抽帧失败:\n{err}")

    frames = sorted(frames_dir.glob("frame_*.jpg"))
    if not frames:
        raise FrameExtractError(f"抽帧完成但未生成图片: {frames_dir}")
    return frames
