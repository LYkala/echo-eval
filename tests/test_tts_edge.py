"""edge-tts helpers (offline unit bits + optional live synth)."""
from __future__ import annotations

from pathlib import Path

import pytest

from echo_eval.stimulus.tts import (
    CURATED_EDGE_VOICES,
    DEFAULT_EDGE_VOICE,
    _sapi_rate_to_edge,
    list_edge_voices,
    synthesize,
)


def test_sapi_rate_mapping():
    assert _sapi_rate_to_edge(0) == "+0%"
    assert _sapi_rate_to_edge(1) == "+10%"
    assert _sapi_rate_to_edge(-2) == "-20%"


def test_curated_voices():
    voices = list_edge_voices(curated_only=True)
    assert len(voices) >= 4
    ids = {v["id"] for v in voices}
    assert DEFAULT_EDGE_VOICE in ids
    assert all(v["id"].startswith("zh-CN") for v in CURATED_EDGE_VOICES)


def test_unknown_backend():
    with pytest.raises(Exception):
        synthesize("hi", Path("/tmp/x.wav"), backend="not-a-backend")


@pytest.mark.integration
def test_edge_synth_live(tmp_path: Path):
    """Requires network + edge-tts + ffmpeg. Skipped unless ECHO_EVAL_LIVE_TTS=1."""
    import os

    if os.environ.get("ECHO_EVAL_LIVE_TTS") != "1":
        pytest.skip("set ECHO_EVAL_LIVE_TTS=1 to run live edge-tts")
    out = tmp_path / "edge.wav"
    result = synthesize(
        "小翼管家",
        out,
        backend="edge",
        voice=DEFAULT_EDGE_VOICE,
    )
    assert Path(result.path).is_file()
    assert result.duration_sec > 0.1
    assert result.backend == "edge"
