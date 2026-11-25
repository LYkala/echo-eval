from pathlib import Path

from echo_eval.capture.asr_poll import _mean_abs_diff
from echo_eval.config import load_config
from echo_eval.judge.asr import (
    evaluate_asr_images_mock,
    is_ignore_asr_text,
    judge_asr_utterance,
    normalize_asr_text,
    texts_match,
)


def test_normalize_and_match():
    assert normalize_asr_text("点击电影。") == normalize_asr_text("点击电影")
    assert texts_match("点击电影", "点击电影。")
    assert not texts_match("点击电影", "我还在听，说再见退出")
    assert is_ignore_asr_text("我还在听，说再见退出")


def test_mock_asr_pass_and_fail(tmp_path: Path):
    ok = tmp_path / "frame_asr_ok.png"
    ok.write_bytes(b"\x89PNG\r\n\x1a\n" + b"x" * 20)
    bad = tmp_path / "frame_asr_fail.png"
    bad.write_bytes(b"\x89PNG\r\n\x1a\n" + b"y" * 20)

    r1 = evaluate_asr_images_mock([str(ok)], "点击电影")
    assert r1["passed"] is True
    r2 = evaluate_asr_images_mock([str(bad)], "点击电影")
    assert r2["passed"] is False
    r3 = evaluate_asr_images_mock([], "点击电影")
    assert r3["passed"] is False


def test_judge_asr_writes(tmp_path: Path):
    img = tmp_path / "s.png"
    img.write_bytes(b"\x89PNG\r\n\x1a\n" + b"z" * 12)
    out = tmp_path / "asr.json"
    data = judge_asr_utterance(
        spoken_text="点击电影",
        image_paths=[str(img)],
        backend="mock",
        write_path=out,
    )
    assert data["passed"] is True
    assert out.is_file()


def test_asr_config_defaults(tmp_path: Path):
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
    assert cfg.asr.enabled is True
    assert cfg.asr.interval_sec == 0.5
    assert cfg.asr.window_sec == 4.0
    assert cfg.asr.early_stop is True
    assert "我还在听，说再见退出" in cfg.asr.ignore_texts


def test_mean_abs_diff():
    assert _mean_abs_diff(b"\x00\x00", b"\x00\x00") == 0.0
    assert _mean_abs_diff(b"\x00\x00", b"\x0a\x0a") == 10.0
