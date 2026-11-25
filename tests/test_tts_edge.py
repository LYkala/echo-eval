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


def test_edge_fallback_sapi_without_network(tmp_path: Path, monkeypatch):
    """When edge fails and fallback_sapi=True on Windows, use SAPI path."""
    import sys
    from echo_eval.stimulus import tts as tts_mod

    def _boom(*_a, **_k):
        raise tts_mod.TtsError("Cannot connect to host speech.platform.bing.com")

    monkeypatch.setattr(tts_mod, "synthesize_edge", _boom)

    if sys.platform.startswith("win"):
        called = {}

        def _sapi(text, out_path, *, voice="", rate=0):
            out_path = Path(out_path)
            out_path.parent.mkdir(parents=True, exist_ok=True)
            out_path.write_bytes(b"RIFF" + b"\x00" * 40)
            # minimal valid-enough for duration helper may fail; stub duration via write silence
            from echo_eval.stimulus.tts import _write_beep_wav

            _write_beep_wav(out_path, duration_sec=0.2)
            called["ok"] = True
            return tts_mod.TtsResult(
                path=str(out_path),
                backend="sapi",
                text=text,
                duration_sec=0.2,
                voice="",
            )

        monkeypatch.setattr(tts_mod, "synthesize_sapi", _sapi)
        out = tmp_path / "fb.wav"
        result = synthesize(
            "小翼管家",
            out,
            backend="edge",
            fallback_sapi=True,
        )
        assert called.get("ok")
        assert result.backend == "edge_fallback_sapi"
    else:
        with pytest.raises(tts_mod.TtsError):
            synthesize(
                "小翼管家",
                tmp_path / "fb.wav",
                backend="edge",
                fallback_sapi=True,
            )


def test_edge_no_fallback_reraises(tmp_path: Path, monkeypatch):
    from echo_eval.stimulus import tts as tts_mod

    def _boom(*_a, **_k):
        raise tts_mod.TtsError("network down")

    monkeypatch.setattr(tts_mod, "synthesize_edge", _boom)
    with pytest.raises(tts_mod.TtsError, match="network down"):
        synthesize(
            "小翼管家",
            tmp_path / "x.wav",
            backend="edge",
            fallback_sapi=False,
        )


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
