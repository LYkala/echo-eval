from pathlib import Path

import pytest

from echo_eval.capture.frames import FrameExtractError, extract_frames


def test_extract_frames_missing_video(tmp_path: Path):
    with pytest.raises(FrameExtractError):
        extract_frames(tmp_path / "no.mp4", tmp_path / "frames", fps=1)
