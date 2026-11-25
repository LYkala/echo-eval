"""Per-utterance ASR screenshot poll (0.5s interval, early-stop on bottom-band change)."""
from __future__ import annotations

import struct
import subprocess
import time
import zlib
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from echo_eval.capture.frames import resolve_ffmpeg
from echo_eval.config import AsrConfig
from echo_eval.device.adb import AdbClient, AdbError


@dataclass
class AsrPollResult:
    paths: list[Path] = field(default_factory=list)
    offsets_sec: list[float] = field(default_factory=list)
    elapsed_sec: float = 0.0
    early_stopped: bool = False
    error: str = ""


def _png_bottom_band_gray(
    path: Path,
    *,
    band_ratio: float,
    ffmpeg_path: str = "",
    width: int = 48,
    height: int = 12,
) -> bytes | None:
    """Downsample bottom band to tiny gray raw via ffmpeg (best-effort)."""
    try:
        ff = resolve_ffmpeg(ffmpeg_path)
    except Exception:
        return _png_bottom_band_gray_stdlib(path, band_ratio=band_ratio, width=width, height=height)

    vf = (
        f"crop=iw:ih*{band_ratio:.4f}:0:ih*(1-{band_ratio:.4f}),"
        f"scale={width}:{height}:flags=area,format=gray"
    )
    try:
        proc = subprocess.run(
            [
                ff,
                "-hide_banner",
                "-loglevel",
                "error",
                "-i",
                str(path),
                "-vf",
                vf,
                "-f",
                "rawvideo",
                "-pix_fmt",
                "gray",
                "-",
            ],
            check=False,
            capture_output=True,
            timeout=20,
        )
    except (OSError, subprocess.TimeoutExpired):
        return _png_bottom_band_gray_stdlib(path, band_ratio=band_ratio, width=width, height=height)
    if proc.returncode != 0 or not proc.stdout:
        return _png_bottom_band_gray_stdlib(path, band_ratio=band_ratio, width=width, height=height)
    return proc.stdout


def _png_bottom_band_gray_stdlib(
    path: Path,
    *,
    band_ratio: float,
    width: int = 48,
    height: int = 12,
) -> bytes | None:
    """Decode PNG without Pillow; crude nearest-neighbor downsample of bottom band."""
    try:
        data = path.read_bytes()
    except OSError:
        return None
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    pos = 8
    width_img = height_img = None
    color_type: int | None = None
    idat = bytearray()
    while pos + 8 <= len(data):
        length = struct.unpack(">I", data[pos : pos + 4])[0]
        ctype = data[pos + 4 : pos + 8]
        chunk = data[pos + 8 : pos + 8 + length]
        pos += 12 + length
        if ctype == b"IHDR" and length >= 13:
            width_img, height_img, bit_depth, color_type = struct.unpack(">IIBB", chunk[:10])
            if bit_depth != 8 or color_type not in (2, 6):
                return None
        elif ctype == b"IDAT":
            idat.extend(chunk)
        elif ctype == b"IEND":
            break
    if not width_img or not height_img or not idat or color_type is None:
        return None
    try:
        raw = zlib.decompress(bytes(idat))
    except zlib.error:
        return None
    channels = 3 if color_type == 2 else 4
    stride = 1 + width_img * channels
    if len(raw) < stride * height_img:
        return None
    y0 = max(0, int(height_img * (1.0 - band_ratio)))
    out = bytearray(width * height)
    for oy in range(height):
        sy = y0 + int((height_img - y0 - 1) * oy / max(1, height - 1))
        row = raw[sy * stride + 1 : (sy + 1) * stride]
        for ox in range(width):
            sx = int((width_img - 1) * ox / max(1, width - 1))
            i = sx * channels
            r, g, b = row[i], row[i + 1], row[i + 2]
            out[oy * width + ox] = (r * 3 + g * 4 + b) // 8
    return bytes(out)


def _mean_abs_diff(a: bytes, b: bytes) -> float:
    n = min(len(a), len(b))
    if n <= 0:
        return 0.0
    total = 0
    for i in range(n):
        total += abs(a[i] - b[i])
    return total / float(n)


def poll_asr_screenshots(
    adb: AdbClient,
    out_dir: Path,
    *,
    asr: AsrConfig,
    ffmpeg_path: str = "",
    step_index: int = 1,
) -> AsrPollResult:
    """
    Capture screenshots every interval_sec for up to window_sec after speech ends.

    Early-stop (heuristic): bottom band differs from the first frame for
    `early_stop_stable_frames` consecutive captures → assume ASR text appeared.
    """
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    result = AsrPollResult()
    interval = max(0.1, float(asr.interval_sec))
    window = max(interval, float(asr.window_sec))
    max_frames = max(1, int(window / interval + 0.001) + 1)
    change_thresh = 8.0  # mean abs gray diff on downsampled bottom band
    streak = 0
    baseline: bytes | None = None
    t_start = time.time()

    for i in range(max_frames):
        target = t_start + i * interval
        now = time.time()
        if now < target:
            time.sleep(target - now)
        if time.time() - t_start > window + 0.05 and i > 0:
            break

        path = out_dir / f"step_{step_index:02d}_{i:02d}.png"
        try:
            adb.screencap_png(path, timeout=25)
        except AdbError as e:
            result.error = str(e)
            break
        offset = round(time.time() - t_start, 3)
        result.paths.append(path)
        result.offsets_sec.append(offset)

        if asr.early_stop and len(result.paths) >= 1:
            band = _png_bottom_band_gray(
                path,
                band_ratio=float(asr.bottom_band_ratio),
                ffmpeg_path=ffmpeg_path,
            )
            if band is not None:
                if baseline is None:
                    baseline = band
                else:
                    diff = _mean_abs_diff(baseline, band)
                    if diff >= change_thresh:
                        streak += 1
                    else:
                        streak = 0
                    if streak >= max(1, int(asr.early_stop_stable_frames)):
                        result.early_stopped = True
                        break

    result.elapsed_sec = round(time.time() - t_start, 3)
    return result


def asr_poll_meta(result: AsrPollResult) -> dict[str, Any]:
    return {
        "paths": [str(p) for p in result.paths],
        "offsets_sec": list(result.offsets_sec),
        "elapsed_sec": result.elapsed_sec,
        "early_stopped": result.early_stopped,
        "error": result.error,
        "count": len(result.paths),
    }
