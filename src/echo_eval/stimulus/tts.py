"""TTS backends: Windows SAPI, edge-tts (online), file lookup; http reserved."""
from __future__ import annotations

import asyncio
import math
import shutil
import struct
import subprocess
import sys
import wave
from dataclasses import dataclass
from pathlib import Path
from typing import Any


class TtsError(RuntimeError):
    pass


@dataclass
class TtsResult:
    path: str
    backend: str
    text: str
    duration_sec: float
    voice: str = ""


# Curated zh-CN voices for the console (edge-tts ShortName).
CURATED_EDGE_VOICES: list[dict[str, str]] = [
    {"id": "zh-CN-XiaoxiaoNeural", "label": "晓晓（女·温暖）", "gender": "Female", "locale": "zh-CN"},
    {"id": "zh-CN-XiaoyiNeural", "label": "晓伊（女·活泼）", "gender": "Female", "locale": "zh-CN"},
    {"id": "zh-CN-YunxiNeural", "label": "云希（男·阳光）", "gender": "Male", "locale": "zh-CN"},
    {"id": "zh-CN-YunyangNeural", "label": "云扬（男·新闻）", "gender": "Male", "locale": "zh-CN"},
    {"id": "zh-CN-YunjianNeural", "label": "云健（男·热情）", "gender": "Male", "locale": "zh-CN"},
    {"id": "zh-CN-YunxiaNeural", "label": "云夏（男·可爱）", "gender": "Male", "locale": "zh-CN"},
]

DEFAULT_EDGE_VOICE = "zh-CN-XiaoxiaoNeural"


def wav_duration_sec(path: Path) -> float:
    with wave.open(str(path), "rb") as w:
        frames = w.getnframes()
        rate = w.getframerate() or 1
        return frames / float(rate)


def _write_silence_wav(path: Path, duration_sec: float = 0.5, rate: int = 16000) -> None:
    n = max(1, int(rate * duration_sec))
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        for _ in range(n):
            w.writeframes(struct.pack("<h", 0))


def _write_beep_wav(path: Path, duration_sec: float = 0.4, rate: int = 16000, freq: float = 880.0) -> None:
    n = max(1, int(rate * duration_sec))
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        for i in range(n):
            t = i / rate
            amp = 0.3 if 0.02 < t < duration_sec - 0.02 else 0.0
            val = int(max(-32767, min(32767, amp * 32767 * math.sin(2 * math.pi * freq * t))))
            w.writeframes(struct.pack("<h", val))


def _sapi_rate_to_edge(rate: int) -> str:
    """Map SAPI-like int rate (-10..10) to edge-tts rate string."""
    # Rough: each SAPI step ≈ 10%
    pct = max(-50, min(100, int(rate) * 10))
    return f"{pct:+d}%"


def _ffmpeg_bin(ffmpeg_path: str = "") -> str:
    if ffmpeg_path:
        p = Path(ffmpeg_path)
        if p.is_file():
            return str(p)
    found = shutil.which("ffmpeg")
    if not found:
        raise TtsError("未找到 ffmpeg，无法将 edge-tts 音频转为 wav")
    return found


def _mp3_to_wav(mp3: Path, wav: Path, *, ffmpeg_path: str = "") -> None:
    ff = _ffmpeg_bin(ffmpeg_path)
    wav.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        ff,
        "-y",
        "-hide_banner",
        "-loglevel",
        "error",
        "-i",
        str(mp3),
        "-ac",
        "1",
        "-ar",
        "24000",
        "-c:a",
        "pcm_s16le",
        str(wav),
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True, check=False)
    if proc.returncode != 0 or not wav.is_file() or wav.stat().st_size <= 44:
        err = (proc.stderr or proc.stdout or "").strip()
        raise TtsError(f"edge-tts mp3→wav 失败: {err or 'empty output'}")


def synthesize_sapi(
    text: str,
    out_path: Path,
    *,
    voice: str = "",
    rate: int = 0,
) -> TtsResult:
    """Use Windows System.Speech via PowerShell → wav file."""
    if not sys.platform.startswith("win"):
        raise TtsError("SAPI TTS 仅支持 Windows")

    out_path = Path(out_path)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    safe_text = (text or "").replace("'", "''")
    safe_out = str(out_path.resolve()).replace("'", "''")
    voice_line = ""
    if voice.strip():
        v = voice.replace("'", "''")
        voice_line = f"try {{ $speak.SelectVoice('{v}') }} catch {{ }}\n"

    ps = f"""
Add-Type -AssemblyName System.Speech
$speak = New-Object System.Speech.Synthesis.SpeechSynthesizer
$speak.Rate = {int(rate)}
{voice_line}$speak.SetOutputToWaveFile('{safe_out}')
$speak.Speak('{safe_text}')
$speak.Dispose()
"""
    cmd = [
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-Command",
        ps,
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True, check=False)
    if proc.returncode != 0 or not out_path.is_file() or out_path.stat().st_size <= 44:
        err = (proc.stderr or proc.stdout or "").strip()
        raise TtsError(f"SAPI TTS 失败: {err or '未生成有效 wav'}")
    return TtsResult(
        path=str(out_path.resolve()),
        backend="sapi",
        text=text,
        duration_sec=round(wav_duration_sec(out_path), 3),
        voice=voice or "",
    )


def synthesize_file_lookup(
    text: str,
    out_path: Path,
    *,
    lookup_dir: Path,
    case_id: str,
    step_index: int,
) -> TtsResult:
    """file backend: copy prebuilt wav named by case/step or hashed text."""
    candidates = [
        lookup_dir / f"{case_id}_{step_index}.wav",
        lookup_dir / f"{case_id}_{step_index:02d}.wav",
    ]
    src = next((p for p in candidates if p.is_file()), None)
    if not src:
        raise TtsError(
            f"file 后端未找到预生成音频: 试过 {[str(p) for p in candidates]}"
        )
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_bytes(src.read_bytes())
    return TtsResult(
        path=str(out_path.resolve()),
        backend="file",
        text=text,
        duration_sec=round(wav_duration_sec(out_path), 3),
    )


async def _edge_save_mp3(text: str, voice: str, mp3_path: Path, *, rate: str) -> None:
    try:
        import edge_tts
    except ImportError as e:
        raise TtsError("未安装 edge-tts，请执行: pip install edge-tts") from e

    mp3_path.parent.mkdir(parents=True, exist_ok=True)
    communicate = edge_tts.Communicate(text, voice, rate=rate)
    await communicate.save(str(mp3_path))


def synthesize_edge(
    text: str,
    out_path: Path,
    *,
    voice: str = "",
    rate: int = 0,
    ffmpeg_path: str = "",
) -> TtsResult:
    """
    Microsoft Edge online neural TTS via edge-tts (external network required).
    Writes wav (mp3 intermediate converted with ffmpeg).
    """
    out_path = Path(out_path)
    if out_path.suffix.lower() != ".wav":
        out_path = out_path.with_suffix(".wav")
    voice_id = (voice or "").strip() or DEFAULT_EDGE_VOICE
    rate_str = _sapi_rate_to_edge(int(rate))
    mp3_path = out_path.with_suffix(".mp3")
    try:
        asyncio.run(_edge_save_mp3(text, voice_id, mp3_path, rate=rate_str))
    except TtsError:
        raise
    except Exception as e:
        raise TtsError(f"edge-tts 合成失败（需可访问 speech.platform.bing.com）: {e}") from e

    if not mp3_path.is_file() or mp3_path.stat().st_size < 100:
        raise TtsError("edge-tts 未生成有效音频")

    try:
        _mp3_to_wav(mp3_path, out_path, ffmpeg_path=ffmpeg_path)
    finally:
        try:
            mp3_path.unlink(missing_ok=True)
        except OSError:
            pass

    return TtsResult(
        path=str(out_path.resolve()),
        backend="edge",
        text=text,
        duration_sec=round(wav_duration_sec(out_path), 3),
        voice=voice_id,
    )


def list_edge_voices(*, locale_prefix: str = "zh-CN", curated_only: bool = True) -> list[dict[str, Any]]:
    """Return voice descriptors for the console. curated_only avoids a network round-trip."""
    if curated_only:
        return [dict(v) for v in CURATED_EDGE_VOICES if v["locale"].startswith(locale_prefix or "")]

    try:
        import edge_tts
    except ImportError as e:
        raise TtsError("未安装 edge-tts，请执行: pip install edge-tts") from e

    async def _list() -> list[dict[str, Any]]:
        raw = await edge_tts.list_voices()
        out: list[dict[str, Any]] = []
        for v in raw:
            loc = str(v.get("Locale") or "")
            if locale_prefix and not loc.startswith(locale_prefix):
                continue
            sid = str(v.get("ShortName") or "")
            if not sid:
                continue
            out.append(
                {
                    "id": sid,
                    "label": sid,
                    "gender": str(v.get("Gender") or ""),
                    "locale": loc,
                }
            )
        return out

    try:
        return asyncio.run(_list())
    except Exception as e:
        raise TtsError(f"拉取 edge-tts 音色列表失败: {e}") from e


def synthesize(
    text: str,
    out_path: Path,
    *,
    backend: str = "sapi",
    voice: str = "",
    rate: int = 0,
    lookup_dir: Path | None = None,
    case_id: str = "",
    step_index: int = 1,
    ffmpeg_path: str = "",
) -> TtsResult:
    backend = (backend or "sapi").strip().lower()
    text = (text or "").strip()
    if not text:
        raise TtsError("TTS 文本为空")

    if backend == "sapi":
        if sys.platform.startswith("win"):
            return synthesize_sapi(text, out_path, voice=voice, rate=rate)
        # Non-Windows CI/dev fallback: beep placeholder (not for real device eval)
        _write_beep_wav(out_path)
        (out_path.with_suffix(".txt")).write_text(text, encoding="utf-8")
        return TtsResult(
            path=str(out_path.resolve()),
            backend="sapi_fallback_beep",
            text=text,
            duration_sec=round(wav_duration_sec(out_path), 3),
            voice=voice or "",
        )

    if backend == "file":
        if not lookup_dir:
            raise TtsError("tts.backend=file 时需要配置 tts.lookup_dir")
        return synthesize_file_lookup(
            text,
            out_path,
            lookup_dir=Path(lookup_dir),
            case_id=case_id,
            step_index=step_index,
        )

    if backend == "edge":
        return synthesize_edge(
            text,
            out_path,
            voice=voice,
            rate=rate,
            ffmpeg_path=ffmpeg_path,
        )

    if backend == "http":
        raise TtsError(
            "tts.backend=http 已预留但尚未实现。请改用 edge / sapi / file。"
        )

    raise TtsError(f"未知 tts.backend: {backend}")
