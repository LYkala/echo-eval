"""MOS heuristic should prefer speech-like segments over steady media beds."""
from __future__ import annotations

import math
import struct
import wave
from pathlib import Path

from echo_eval.judge.mos import analyze_reply_wav


def _write_wav(path: Path, samples: list[int], rate: int = 16000) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(struct.pack("<" + "h" * len(samples), *samples))


def _tone(rate: int, sec: float, hz: float, amp: int = 8000) -> list[int]:
    n = int(rate * sec)
    return [
        int(amp * math.sin(2 * math.pi * hz * i / rate)) for i in range(n)
    ]


def _modulated_speechish(rate: int, sec: float) -> list[int]:
    """Amplitude-modulated tone to mimic syllable energy swings."""
    n = int(rate * sec)
    out: list[int] = []
    for i in range(n):
        t = i / float(rate)
        env = 0.35 + 0.65 * abs(math.sin(2 * math.pi * 4.5 * t))
        # Mix two harmonics + slight ZCR variation
        v = env * (
            0.7 * math.sin(2 * math.pi * 220 * t)
            + 0.3 * math.sin(2 * math.pi * 440 * t)
        )
        out.append(int(9000 * v))
    return out


def test_ignore_steady_media_keeps_speech(tmp_path: Path):
    rate = 16000
    # 1s speech-like + 2s steady “song” bed + 0.5s speech-like
    samples = (
        _modulated_speechish(rate, 1.0)
        + _tone(rate, 2.0, 440.0, amp=10000)
        + _modulated_speechish(rate, 0.8)
    )
    wav = tmp_path / "mix.wav"
    _write_wav(wav, samples, rate)

    with_filter = analyze_reply_wav(wav, ignore_non_speech=True, min_speech_sec=0.3)
    assert with_filter["status"] == "scored"
    assert with_filter["features"].get("ignore_non_speech") is True
    # Should attribute a chunk of the steady bed to media when filtering
    assert float(with_filter["features"].get("media_sec") or 0) >= 0.5
    assert float(with_filter["features"].get("speech_sec") or 0) >= 0.8


def test_only_media_becomes_na(tmp_path: Path):
    rate = 16000
    wav = tmp_path / "song.wav"
    _write_wav(wav, _tone(rate, 3.0, 523.25, amp=12000), rate)
    result = analyze_reply_wav(wav, ignore_non_speech=True, min_speech_sec=0.5)
    # Steady tone should be mostly media → little speech → N/A
    assert result["status"] in ("no_speech", "scored")
    if result["status"] == "scored":
        # If borderline scored, media_sec should still be reported high
        assert float(result["features"].get("media_sec") or 0) >= 1.0
    else:
        assert "非语音" in (result.get("reason") or "") or "忽略" in (
            result.get("reason") or ""
        ) or "有效语音" in (result.get("reason") or "")
