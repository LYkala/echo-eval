"""TTS backends: Windows SAPI (default), with switchable stubs for edge/http/file."""
from __future__ import annotations

import math
import struct
import subprocess
import sys
import wave
from dataclasses import dataclass
from pathlib import Path


class TtsError(RuntimeError):
    pass


@dataclass
class TtsResult:
    path: str
    backend: str
    text: str
    duration_sec: float


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
    # Escape for PowerShell single-quoted string
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

    if backend in ("edge", "http"):
        raise TtsError(
            f"tts.backend={backend} 已预留但尚未实现。请改用 sapi 或 file，"
            "或后续接入对应实现。"
        )

    raise TtsError(f"未知 tts.backend: {backend}")
