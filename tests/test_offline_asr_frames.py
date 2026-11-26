from pathlib import Path

from echo_eval.capture.offline_asr_frames import extract_asr_frames_from_video


def test_missing_video_returns_error(tmp_path: Path) -> None:
    out = tmp_path / "asr"
    r = extract_asr_frames_from_video(
        tmp_path / "nope.mp4",
        out,
        start_sec=1.0,
        window_sec=2.0,
        interval_sec=0.5,
    )
    assert r["ok"] is False
    assert r["mode"] == "offline_video"
    assert r["count"] == 0
    assert "不存在" in str(r.get("error") or "")
