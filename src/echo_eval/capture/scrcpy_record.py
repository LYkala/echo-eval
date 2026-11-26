"""Record device screen+audio via scrcpy into a local mp4 (no device pull)."""
from __future__ import annotations

import os
import shutil
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any


class ScrcpyError(RuntimeError):
    pass


@dataclass
class ScrcpyRecording:
    path: str
    pid: int
    started_at: float
    duration_sec: int
    cmd: list[str]


def resolve_scrcpy_bin(scrcpy_path: str = "") -> str:
    if scrcpy_path:
        p = Path(scrcpy_path)
        if p.is_file():
            return str(p.resolve())
        raise ScrcpyError(f"scrcpy_path 不是有效文件: {scrcpy_path}")
    found = shutil.which("scrcpy") or shutil.which("scrcpy.exe")
    if not found:
        raise ScrcpyError(
            "未找到 scrcpy。请安装 Genymobile scrcpy，或在 capture.scrcpy_path 填写绝对路径。"
        )
    return found


def probe_has_audio(video: Path, *, ffmpeg_path: str = "") -> bool:
    """True if mp4 contains an audio stream."""
    ffprobe = _resolve_ffprobe(ffmpeg_path)
    if not ffprobe:
        return False
    cmd = [
        ffprobe,
        "-v",
        "error",
        "-select_streams",
        "a",
        "-show_entries",
        "stream=codec_name",
        "-of",
        "csv=p=0",
        str(video),
    ]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.TimeoutExpired):
        return False
    return bool((proc.stdout or "").strip())


def _resolve_ffprobe(ffmpeg_path: str = "") -> str:
    if ffmpeg_path:
        p = Path(ffmpeg_path)
        if p.is_file():
            cand = p.with_name("ffprobe.exe" if p.suffix.lower() == ".exe" else "ffprobe")
            if cand.is_file():
                return str(cand)
    return shutil.which("ffprobe") or shutil.which("ffprobe.exe") or ""


def build_scrcpy_cmd(
    *,
    scrcpy_bin: str,
    serial: str,
    out_mp4: Path,
    duration_sec: int,
    audio_codec: str = "aac",
    audio_encoder: str = "OMX.google.aac.encoder",
    max_size: int = 1280,
    video_bit_rate: str = "8M",
    extra_args: list[str] | None = None,
    require_audio: bool = True,
) -> list[str]:
    out_mp4 = Path(out_mp4)
    out_mp4.parent.mkdir(parents=True, exist_ok=True)
    if out_mp4.exists():
        out_mp4.unlink()
    cmd = [
        scrcpy_bin,
        "--serial",
        serial,
        "--no-window",
        "--no-playback",
        f"--time-limit={max(1, int(duration_sec))}",
        f"--record={out_mp4}",
        f"--max-size={int(max_size)}",
        f"--video-bit-rate={video_bit_rate}",
    ]
    codec = (audio_codec or "").strip()
    if codec:
        cmd.append(f"--audio-codec={codec}")
    enc = (audio_encoder or "").strip()
    if enc:
        cmd.append(f"--audio-encoder={enc}")
    if require_audio:
        cmd.append("--require-audio")
    if extra_args:
        cmd.extend(str(a) for a in extra_args if str(a).strip())
    return cmd


def start_scrcpy_record(
    out_mp4: Path,
    *,
    serial: str,
    duration_sec: int,
    scrcpy_path: str = "",
    adb_path: str = "",
    audio_codec: str = "aac",
    audio_encoder: str = "OMX.google.aac.encoder",
    max_size: int = 1280,
    video_bit_rate: str = "8M",
    extra_args: list[str] | None = None,
    require_audio: bool = True,
) -> tuple[subprocess.Popen[str], ScrcpyRecording]:
    """
    Start scrcpy recording to a local file.

    Uses env ADB when adb_path is set (scrcpy 5.x has no --adbd).
    Huawei Android 12 needs AAC (default opus often fails).
    """
    bin_path = resolve_scrcpy_bin(scrcpy_path)
    cmd = build_scrcpy_cmd(
        scrcpy_bin=bin_path,
        serial=serial,
        out_mp4=out_mp4,
        duration_sec=duration_sec,
        audio_codec=audio_codec,
        audio_encoder=audio_encoder,
        max_size=max_size,
        video_bit_rate=video_bit_rate,
        extra_args=extra_args,
        require_audio=require_audio,
    )
    env = os.environ.copy()
    if adb_path:
        env["ADB"] = str(Path(adb_path))
    try:
        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            env=env,
        )
    except OSError as e:
        raise ScrcpyError(f"无法启动 scrcpy: {e}") from e
    meta = ScrcpyRecording(
        path=str(Path(out_mp4).resolve()),
        pid=int(proc.pid or 0),
        started_at=time.time(),
        duration_sec=int(duration_sec),
        cmd=cmd,
    )
    return proc, meta


def wait_scrcpy_record(
    proc: subprocess.Popen[str],
    meta: ScrcpyRecording,
    *,
    timeout_sec: float | None = None,
) -> dict[str, Any]:
    """Wait for scrcpy to finish; raise if recording file missing/empty."""
    limit = timeout_sec if timeout_sec is not None else float(meta.duration_sec) + 60.0
    try:
        stdout, stderr = proc.communicate(timeout=limit)
    except subprocess.TimeoutExpired as e:
        proc.kill()
        try:
            proc.communicate(timeout=10)
        except Exception:
            pass
        raise ScrcpyError("scrcpy 录制超时") from e

    out = Path(meta.path)
    ok = out.is_file() and out.stat().st_size > 0
    detail = {
        "ok": ok,
        "returncode": proc.returncode,
        "path": str(out),
        "stdout": (stdout or "")[-2000:],
        "stderr": (stderr or "")[-4000:],
        "cmd": meta.cmd,
    }
    if not ok:
        err = (stderr or stdout or "").strip() or f"exit={proc.returncode}"
        raise ScrcpyError(f"scrcpy 未生成有效录屏: {err}")
    # Non-zero exit can still leave a usable file (e.g. Ctrl+C finalize); only
    # fail hard when require-audio made it abort with empty/corrupt output.
    if proc.returncode not in (0, None) and not probe_has_audio(out):
        # Video-only after audio failure — still usable for GUI, but caller may care.
        detail["audio"] = False
        detail["warn"] = f"scrcpy exit={proc.returncode} and no audio track"
    else:
        detail["audio"] = probe_has_audio(out)
    return detail


def stop_scrcpy_record(proc: subprocess.Popen[str] | None) -> None:
    if proc is None:
        return
    if proc.poll() is not None:
        return
    try:
        proc.terminate()
        try:
            proc.communicate(timeout=15)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.communicate(timeout=5)
    except Exception:
        try:
            proc.kill()
        except Exception:
            pass
