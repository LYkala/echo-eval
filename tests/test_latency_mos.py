import math
import struct
import wave
from pathlib import Path

from echo_eval.capture.post_speech import _mean_abs_diff
from echo_eval.config import load_config
from echo_eval.judge.mos import analyze_reply_wav, judge_mos_utterance


def _write_tone_wav(path: Path, *, seconds: float = 1.0, freq: float = 440.0, rate: int = 16000):
    n = int(rate * seconds)
    frames = bytearray()
    for i in range(n):
        # speech-like amplitude with a quiet gap in the middle
        t = i / rate
        amp = 0.25 if 0.15 < t < 0.85 else 0.02
        sample = int(amp * 32767 * math.sin(2 * math.pi * freq * t))
        frames += struct.pack("<h", sample)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(bytes(frames))


def _write_silence_wav(path: Path, *, seconds: float = 1.0, rate: int = 16000):
    n = int(rate * seconds)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(b"\x00\x00" * n)


def test_mos_scores_speech(tmp_path: Path):
    wav = tmp_path / "reply.wav"
    _write_tone_wav(wav, seconds=1.2)
    result = analyze_reply_wav(wav, min_speech_sec=0.3)
    assert result["status"] == "scored"
    assert result["mos"] is not None
    assert 1.0 <= float(result["mos"]) <= 5.0


def test_mos_no_speech(tmp_path: Path):
    wav = tmp_path / "silent.wav"
    _write_silence_wav(wav, seconds=1.0)
    result = analyze_reply_wav(wav, min_speech_sec=0.3)
    assert result["status"] == "no_speech"
    assert result["mos"] is None


def test_judge_mos_mock(tmp_path: Path):
    wav = tmp_path / "ok.wav"
    _write_tone_wav(wav, seconds=0.8)
    data = judge_mos_utterance(wav_path=wav, backend="mock", write_path=tmp_path / "m.json")
    assert data["status"] == "scored"
    assert (tmp_path / "m.json").is_file()


def test_latency_mos_config_defaults(tmp_path: Path):
    p = tmp_path / "device.yaml"
    p.write_text(
        """
adb:
  host: "10.0.0.1"
capture: {}
""",
        encoding="utf-8",
    )
    cfg = load_config(p)
    assert cfg.latency.enabled is True
    assert cfg.latency.fps == 10.0
    assert cfg.latency.window_sec == 8.0
    assert cfg.mos.enabled is True
    assert cfg.mos.window_sec == 5.0


def test_mean_abs_diff_import():
    assert _mean_abs_diff(b"\x01\x02", b"\x01\x02") == 0.0
