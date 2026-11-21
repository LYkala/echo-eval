from pathlib import Path

from echo_eval.judge.gui_poll import subsample_image_paths
from echo_eval.judge.mock import evaluate_gui_poll_images_mock
from echo_eval.judge.schema import parse_evaluation_passed
from echo_eval.judge.service import judge_images


def test_subsample_keeps_ends():
    paths = [f"f{i}.jpg" for i in range(20)]
    got = subsample_image_paths(paths, max_images=5)
    assert got[0] == "f0.jpg"
    assert got[-1] == "f19.jpg"
    assert len(got) == 5


def test_mock_pass(tmp_path: Path):
    img = tmp_path / "frame_001.jpg"
    img.write_bytes(b"fake")
    result = evaluate_gui_poll_images_mock([str(img)], ["出现天气"])
    data = {"result_parsed": result["parsed"]}
    assert parse_evaluation_passed(data) is True


def test_mock_fail_no_image():
    result = evaluate_gui_poll_images_mock([], ["出现天气"])
    data = {"result_parsed": result["parsed"]}
    assert parse_evaluation_passed(data) is False


def test_judge_images_writes_evaluation(tmp_path: Path):
    frames = tmp_path / "frames"
    frames.mkdir()
    (frames / "frame_001.jpg").write_bytes(b"x")
    (frames / "frame_002.jpg").write_bytes(b"y")
    data = judge_images(
        exceptions=["界面有结果"],
        run_dir=tmp_path,
        backend="mock",
        write=True,
    )
    assert data["passed"] is True
    assert (tmp_path / "evaluation.json").is_file()
