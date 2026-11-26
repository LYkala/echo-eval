from echo_eval.capture.scrcpy_record import probe_has_audio


def test_probe_missing_file(tmp_path):
    assert probe_has_audio(tmp_path / "none.mp4") is False
