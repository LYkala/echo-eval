"""Unit tests for scrcpy capture config and command building."""
from __future__ import annotations

from pathlib import Path

from echo_eval.capture.scrcpy_record import build_scrcpy_cmd
from echo_eval.config import load_config
from echo_eval.runner.excel_runner import _mos_use_video_deferred


def test_build_scrcpy_cmd_includes_aac_for_huawei():
    out = Path("/tmp/echo_eval_scrcpy_test.mp4")
    cmd = build_scrcpy_cmd(
        scrcpy_bin="scrcpy",
        serial="192.168.1.2:5555",
        out_mp4=out,
        duration_sec=20,
        audio_codec="aac",
        audio_encoder="OMX.google.aac.encoder",
        require_audio=True,
    )
    assert "--no-window" in cmd
    assert "--no-playback" in cmd
    assert "--require-audio" in cmd
    assert "--audio-codec=aac" in cmd
    assert "--audio-encoder=OMX.google.aac.encoder" in cmd
    assert any(c.startswith("--record=") for c in cmd)
    assert "--time-limit=20" in cmd
    assert not any(c.startswith("--audio-source=") for c in cmd)


def test_device_example_defaults_scrcpy(tmp_path: Path):
    example = Path("configs/device.yaml.example")
    assert example.is_file()
    dest = tmp_path / "device.yaml"
    dest.write_text(example.read_text(encoding="utf-8"), encoding="utf-8")
    cfg = load_config(dest)
    assert cfg.capture.backend == "scrcpy"
    assert cfg.capture.use_scrcpy
    assert cfg.capture.scrcpy_audio_codec == "aac"
    assert cfg.mos.source == "video"
    assert cfg.mos.ignore_non_speech is True
    assert _mos_use_video_deferred(cfg) is True

    cfg.capture.backend = "screenrecord"
    # source=video still defers even without scrcpy
    assert _mos_use_video_deferred(cfg) is True
    cfg.mos.source = "auto"
    assert _mos_use_video_deferred(cfg) is False
    cfg.mos.source = "host_mic"
    assert _mos_use_video_deferred(cfg) is False
