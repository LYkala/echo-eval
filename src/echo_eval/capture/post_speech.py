"""Post-utterance capture: high-fps screenshots for ASR + e2e latency (ignore bottom text)."""
from __future__ import annotations

import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from echo_eval.capture.asr_poll import _mean_abs_diff, _png_bottom_band_gray
from echo_eval.config import AsrConfig, LatencyConfig
from echo_eval.device.adb import AdbClient, AdbError


def _upper_band_gray(
    path: Path,
    *,
    bottom_ratio: float,
    ffmpeg_path: str = "",
) -> bytes | None:
    """Gray fingerprint of the region *above* the bottom ASR band (for latency)."""
    # Reuse bottom helper by inverting: crop top (1-bottom) via ffmpeg path inside helper
    # Implement as full-frame gray then zero out bottom conceptually — use ffmpeg crop top.
    from echo_eval.capture.frames import resolve_ffmpeg
    import subprocess

    top_ratio = max(0.5, 1.0 - float(bottom_ratio))
    try:
        ff = resolve_ffmpeg(ffmpeg_path)
    except Exception:
        # Fallback: use full-frame bottom helper on a "fake" — approximate with
        # bottom helper on inverted logic unavailable; use bottom of inverted image N/A.
        # Stdlib: decode and take top rows only.
        return _top_band_gray_stdlib(path, top_ratio=top_ratio)

    width, height = 64, 36
    vf = (
        f"crop=iw:ih*{top_ratio:.4f}:0:0,"
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
        return _top_band_gray_stdlib(path, top_ratio=top_ratio)
    if proc.returncode != 0 or not proc.stdout:
        return _top_band_gray_stdlib(path, top_ratio=top_ratio)
    return proc.stdout


def _top_band_gray_stdlib(path: Path, *, top_ratio: float) -> bytes | None:
    """Decode PNG and downsample the top region (no Pillow)."""
    import struct
    import zlib

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
    y_end = max(1, int(height_img * top_ratio))
    out_w, out_h = 64, 36
    out = bytearray(out_w * out_h)
    for oy in range(out_h):
        sy = int((y_end - 1) * oy / max(1, out_h - 1))
        row = raw[sy * stride + 1 : (sy + 1) * stride]
        for ox in range(out_w):
            sx = int((width_img - 1) * ox / max(1, out_w - 1))
            i = sx * channels
            r, g, b = row[i], row[i + 1], row[i + 2]
            out[oy * out_w + ox] = (r * 3 + g * 4 + b) // 8
    return bytes(out)


@dataclass
class PostSpeechPollResult:
    paths: list[Path] = field(default_factory=list)
    offsets_sec: list[float] = field(default_factory=list)
    elapsed_sec: float = 0.0
    # ASR
    asr_paths: list[Path] = field(default_factory=list)
    asr_offsets_sec: list[float] = field(default_factory=list)
    asr_early_stopped: bool = False
    # Latency (speech_end → first UI change above bottom band)
    latency_sec: float | None = None
    latency_status: str = "skipped"  # measured | no_action | skipped | error
    action_frame: str = ""
    action_offset_sec: float | None = None
    error: str = ""


def poll_post_speech(
    adb: AdbClient,
    out_dir: Path,
    *,
    asr: AsrConfig,
    latency: LatencyConfig,
    ffmpeg_path: str = "",
    step_index: int = 1,
) -> PostSpeechPollResult:
    """
    Capture screenshots after speech ends for ASR and/or e2e latency.

    - Latency uses the region *above* bottom_band (ignore ASR bubble text changes).
    - ASR picks frames every asr.interval_sec; early-stop when bottom band changes.
    """
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    result = PostSpeechPollResult()

    need_asr = bool(asr.enabled)
    need_lat = bool(latency.enabled)
    if not need_asr and not need_lat:
        result.latency_status = "skipped"
        return result

    band_ratio = float(asr.bottom_band_ratio if need_asr else latency.bottom_band_ratio)
    fps = float(latency.fps) if need_lat else max(2.0, 1.0 / max(0.1, float(asr.interval_sec)))
    fps = max(2.0, min(20.0, fps))
    interval = 1.0 / fps
    window = 0.0
    if need_asr:
        window = max(window, float(asr.window_sec))
    if need_lat:
        window = max(window, float(latency.window_sec))
    window = max(interval, window)
    max_frames = max(1, int(window / interval + 0.001) + 1)

    change_thresh = float(latency.change_threshold)
    bottom_thresh = 8.0
    bottom_streak = 0
    bottom_baseline: bytes | None = None
    upper_baseline: bytes | None = None
    next_asr_t = 0.0
    asr_done = not need_asr
    lat_done = not need_lat
    if need_lat:
        result.latency_status = "no_action"

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
            if need_lat and result.latency_status == "no_action":
                result.latency_status = "error"
            break
        offset = round(time.time() - t_start, 3)
        result.paths.append(path)
        result.offsets_sec.append(offset)

        # --- ASR frame pick + early stop (bottom band) ---
        if need_asr and not asr_done:
            if offset + 1e-6 >= next_asr_t or not result.asr_paths:
                result.asr_paths.append(path)
                result.asr_offsets_sec.append(offset)
                next_asr_t = round(len(result.asr_paths) * float(asr.interval_sec), 3)
            if asr.early_stop:
                band = _png_bottom_band_gray(
                    path, band_ratio=band_ratio, ffmpeg_path=ffmpeg_path
                )
                if band is not None:
                    if bottom_baseline is None:
                        bottom_baseline = band
                    else:
                        if _mean_abs_diff(bottom_baseline, band) >= bottom_thresh:
                            bottom_streak += 1
                        else:
                            bottom_streak = 0
                        if bottom_streak >= max(1, int(asr.early_stop_stable_frames)):
                            result.asr_early_stopped = True
                            asr_done = True

        # --- Latency: first change above bottom band ---
        if need_lat and not lat_done:
            upper = _upper_band_gray(
                path, bottom_ratio=band_ratio, ffmpeg_path=ffmpeg_path
            )
            if upper is not None:
                if upper_baseline is None:
                    upper_baseline = upper
                else:
                    diff = _mean_abs_diff(upper_baseline, upper)
                    if diff >= change_thresh:
                        result.latency_sec = round(offset, 1)  # 0.1s precision display
                        result.action_offset_sec = offset
                        result.action_frame = path.name
                        result.latency_status = "measured"
                        lat_done = True

        if asr_done and lat_done:
            # Still allow a tiny settle if ASR wants one more sample — already done
            break

    result.elapsed_sec = round(time.time() - t_start, 3)
    if need_asr and not result.asr_paths and result.paths:
        # ensure at least first/last for ASR
        result.asr_paths = [result.paths[0]]
        result.asr_offsets_sec = [result.offsets_sec[0]]
        if len(result.paths) > 1:
            result.asr_paths.append(result.paths[-1])
            result.asr_offsets_sec.append(result.offsets_sec[-1])
    return result


def post_speech_meta(result: PostSpeechPollResult) -> dict[str, Any]:
    return {
        "paths": [str(p) for p in result.paths],
        "offsets_sec": list(result.offsets_sec),
        "elapsed_sec": result.elapsed_sec,
        "count": len(result.paths),
        "error": result.error,
        "asr": {
            "paths": [str(p) for p in result.asr_paths],
            "offsets_sec": list(result.asr_offsets_sec),
            "early_stopped": result.asr_early_stopped,
            "count": len(result.asr_paths),
        },
        "latency": {
            "latency_sec": result.latency_sec,
            "status": result.latency_status,
            "action_frame": result.action_frame,
            "action_offset_sec": result.action_offset_sec,
        },
    }
