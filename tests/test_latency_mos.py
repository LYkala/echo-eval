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


def test_normalize_dshow_audio_device():
    from echo_eval.capture.host_mic import normalize_dshow_audio_device

    assert normalize_dshow_audio_device("麦克风阵列 (测试)") == "audio=麦克风阵列 (测试)"
    assert normalize_dshow_audio_device("audio=Mic") == "audio=Mic"
    assert normalize_dshow_audio_device("video=Cam") == "video=Cam"


def test_offline_latency_detects_change(tmp_path: Path):
    """Synthetic mp4: black 1s then white 1s → latency ~1.0s after play_end=1.0."""
    import shutil
    import subprocess

    if not shutil.which("ffmpeg"):
        return
    video = tmp_path / "cap.mp4"
    # 1s black + 1s white @ 10fps
    cmd = [
        "ffmpeg",
        "-y",
        "-hide_banner",
        "-loglevel",
        "error",
        "-f",
        "lavfi",
        "-i",
        "color=c=black:s=320x240:d=1",
        "-f",
        "lavfi",
        "-i",
        "color=c=white:s=320x240:d=1",
        "-filter_complex",
        "[0:v][1:v]concat=n=2:v=1:a=0,fps=10",
        "-c:v",
        "libx264",
        "-pix_fmt",
        "yuv420p",
        str(video),
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True, check=False)
    assert proc.returncode == 0, proc.stderr
    assert video.is_file()

    from echo_eval.capture.offline_latency import measure_latency_offline
    from echo_eval.config import LatencyConfig

    out = tmp_path / "lat"
    result = measure_latency_offline(
        video,
        play_end_offset_sec=1.0,
        play_start_offset_sec=0.2,
        latency=LatencyConfig(enabled=True, fps=10.0, window_sec=2.0, change_threshold=6.0),
        out_dir=out,
        step_index=1,
    )
    assert result["status"] == "measured"
    assert result["mode"] == "offline_video"
    assert result["latency_sec"] is not None
    assert float(result["latency_sec"]) <= 0.3


def test_offline_latency_disabled(tmp_path: Path):
    from echo_eval.capture.offline_latency import measure_latency_offline
    from echo_eval.config import LatencyConfig

    result = measure_latency_offline(
        tmp_path / "missing.mp4",
        play_end_offset_sec=0.0,
        latency=LatencyConfig(enabled=False),
        out_dir=tmp_path / "lat",
    )
    assert result["status"] == "skipped"


def test_wake_and_metric_defaults(tmp_path: Path):
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
    assert cfg.run.wake_enabled is True
    assert cfg.run.wake_text == "小翼管家"
    assert cfg.run.wake_wait_sec == 3.0
    from echo_eval.config import public_run_defaults

    d = public_run_defaults(cfg)
    assert d["asr_enabled"] is True
    assert d["latency_enabled"] is True
    assert d["mos_enabled"] is True
    assert d["wake_enabled"] is True
