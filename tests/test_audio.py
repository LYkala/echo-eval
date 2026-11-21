from pathlib import Path

import pytest

from echo_eval.stimulus.audio import AudioPlayError, play_audio_file, wav_duration_sec


def test_wav_duration(repo_beep: Path):
    assert 0.9 <= wav_duration_sec(repo_beep) <= 1.1


def test_missing_audio(tmp_path: Path):
    with pytest.raises(AudioPlayError):
        play_audio_file(tmp_path / "no.wav")


@pytest.fixture
def repo_beep() -> Path:
    p = Path(__file__).resolve().parents[1] / "assets" / "audio" / "beep_1s.wav"
    assert p.is_file()
    return p
