"""TTS on-disk library: index, lookup, runtime fallback."""
from __future__ import annotations

from pathlib import Path

import pytest

from echo_eval.stimulus import tts as tts_mod
from echo_eval.stimulus.tts_library import (
    add_utterance,
    entry_key,
    find_entry,
    load_index,
    prebuild_from_excel,
    resolve_wav,
    text_hash,
    voice_profile,
)
from echo_eval.stimulus.tts import synthesize_with_library, _write_beep_wav


def test_voice_profile_and_hash():
    assert voice_profile("edge", "zh-CN-XiaoxiaoNeural") == "edge:zh-CN-XiaoxiaoNeural"
    assert voice_profile("sapi", "") == "sapi:default"
    assert len(text_hash("小翼管家")) == 12
    assert text_hash("  a  ") == text_hash("a")


def test_add_and_resolve(tmp_path: Path, monkeypatch):
    def _fake_synth(text, out_path, **_kw):
        out_path = Path(out_path)
        _write_beep_wav(out_path, duration_sec=0.15)
        return tts_mod.TtsResult(
            path=str(out_path),
            backend="edge",
            text=text,
            duration_sec=0.15,
            voice="zh-CN-XiaoxiaoNeural",
        )

    monkeypatch.setattr("echo_eval.stimulus.tts_library.synthesize", _fake_synth)
    root = tmp_path / "lib"
    r1 = add_utterance(
        root,
        text="打开爱奇艺",
        case_id="IQ-001",
        step_index=1,
        backend="edge",
        voice="zh-CN-XiaoxiaoNeural",
        source="dataset",
    )
    assert r1["status"] == "created"
    r2 = add_utterance(
        root,
        text="打开爱奇艺",
        case_id="IQ-001",
        step_index=1,
        backend="edge",
        voice="zh-CN-XiaoxiaoNeural",
    )
    assert r2["status"] == "exists"
    hit = resolve_wav(
        root,
        case_id="IQ-001",
        step_index=1,
        text="打开爱奇艺",
        profile=voice_profile("edge", "zh-CN-XiaoxiaoNeural"),
    )
    assert hit is not None and hit.is_file()
    data = load_index(root)
    assert len(data["entries"]) == 1
    key = entry_key(
        case_id="IQ-001",
        step_index=1,
        text="打开爱奇艺",
        profile="edge:zh-CN-XiaoxiaoNeural",
    )
    assert find_entry(
        root,
        case_id="IQ-001",
        step_index=1,
        text="打开爱奇艺",
        profile="edge:zh-CN-XiaoxiaoNeural",
    )["key"] == key


def test_find_entry_is_profile_strict(tmp_path: Path, monkeypatch):
    """Different prebuild voice must not satisfy another profile lookup."""

    def _fake_synth(text, out_path, **_kw):
        out_path = Path(out_path)
        _write_beep_wav(out_path, duration_sec=0.1)
        return tts_mod.TtsResult(
            path=str(out_path),
            backend="edge",
            text=text,
            duration_sec=0.1,
            voice="zh-CN-XiaoxiaoNeural",
        )

    monkeypatch.setattr("echo_eval.stimulus.tts_library.synthesize", _fake_synth)
    root = tmp_path / "lib"
    add_utterance(
        root,
        text="打开设置",
        case_id="C1",
        step_index=1,
        backend="edge",
        voice="zh-CN-XiaoxiaoNeural",
    )
    miss = resolve_wav(
        root,
        case_id="C1",
        step_index=1,
        text="打开设置",
        profile=voice_profile("edge", "zh-CN-YunxiNeural"),
    )
    assert miss is None
    hit = resolve_wav(
        root,
        case_id="C1",
        step_index=1,
        text="打开设置",
        profile=voice_profile("edge", "zh-CN-XiaoxiaoNeural"),
    )
    assert hit is not None and hit.is_file()


def test_list_library_voices(tmp_path: Path, monkeypatch):
    from echo_eval.stimulus.tts_library import list_library_voices, library_status

    def _fake_synth(text, out_path, **_kw):
        out_path = Path(out_path)
        _write_beep_wav(out_path, duration_sec=0.08)
        return tts_mod.TtsResult(
            path=str(out_path),
            backend="edge",
            text=text,
            duration_sec=0.08,
            voice="zh-CN-XiaoxiaoNeural",
        )

    monkeypatch.setattr("echo_eval.stimulus.tts_library.synthesize", _fake_synth)
    root = tmp_path / "lib"
    add_utterance(
        root, text="一", case_id="A", step_index=1, backend="edge", voice="zh-CN-XiaoxiaoNeural"
    )
    add_utterance(
        root, text="二", case_id="A", step_index=2, backend="edge", voice="zh-CN-XiaoxiaoNeural"
    )
    add_utterance(
        root, text="三", case_id="B", step_index=1, backend="edge", voice="zh-CN-YunxiNeural"
    )
    voices = list_library_voices(root)
    assert len(voices) == 2
    by_voice = {v["voice"]: v for v in voices}
    assert by_voice["zh-CN-XiaoxiaoNeural"]["count"] == 2
    assert "晓晓" in by_voice["zh-CN-XiaoxiaoNeural"]["label"]
    status = library_status(root)
    assert len(status["voices"]) == 2


def test_synthesize_with_library_hit(tmp_path: Path, monkeypatch):
    def _fake_synth(text, out_path, **_kw):
        out_path = Path(out_path)
        _write_beep_wav(out_path, duration_sec=0.12)
        return tts_mod.TtsResult(
            path=str(out_path),
            backend="edge",
            text=text,
            duration_sec=0.12,
            voice="zh-CN-XiaoxiaoNeural",
        )

    monkeypatch.setattr("echo_eval.stimulus.tts_library.synthesize", _fake_synth)
    lib = tmp_path / "lib"
    add_utterance(
        lib,
        text="今天天气",
        case_id="W-1",
        step_index=1,
        backend="edge",
        voice="zh-CN-XiaoxiaoNeural",
    )
    out = tmp_path / "out.wav"
    result = synthesize_with_library(
        "今天天气",
        out,
        backend="edge",
        voice="zh-CN-XiaoxiaoNeural",
        case_id="W-1",
        step_index=1,
        prefer_library=True,
        library_dir=str(lib),
    )
    assert result.backend == "library"
    assert Path(result.path).is_file()


def test_synthesize_with_library_miss_sapi(tmp_path: Path, monkeypatch):
    import sys

    called = {}

    def _sapi(text, out_path, *, voice="", rate=0):
        out_path = Path(out_path)
        _write_beep_wav(out_path, duration_sec=0.1)
        called["ok"] = True
        return tts_mod.TtsResult(
            path=str(out_path),
            backend="sapi",
            text=text,
            duration_sec=0.1,
            voice="",
        )

    monkeypatch.setattr(tts_mod, "synthesize_sapi", _sapi)
    monkeypatch.setattr(sys, "platform", "win32")
    out = tmp_path / "miss.wav"
    result = synthesize_with_library(
        "不在库里的句子",
        out,
        backend="edge",
        voice="zh-CN-XiaoxiaoNeural",
        case_id="X",
        step_index=1,
        prefer_library=True,
        library_dir=str(tmp_path / "empty_lib"),
    )
    assert called.get("ok")
    assert result.backend == "library_miss_sapi"


def test_prebuild_from_excel(tmp_path: Path, monkeypatch):
    def _fake_synth(text, out_path, **_kw):
        out_path = Path(out_path)
        _write_beep_wav(out_path, duration_sec=0.08)
        return tts_mod.TtsResult(
            path=str(out_path),
            backend="edge",
            text=text,
            duration_sec=0.08,
            voice="zh-CN-XiaoxiaoNeural",
        )

    monkeypatch.setattr("echo_eval.stimulus.tts_library.synthesize", _fake_synth)
    csv_path = tmp_path / "mini.csv"
    csv_path.write_text(
        "用例编号,用例名称,测试步骤,预期结果,等待时间\n"
        'C1,demo,"1. 打开设置","设置页",3\n',
        encoding="utf-8",
    )
    lib = tmp_path / "lib"
    summary = prebuild_from_excel(
        csv_path,
        lib,
        backend="edge",
        voice="zh-CN-XiaoxiaoNeural",
        wake_text="小翼管家",
    )
    assert summary["created"] >= 2  # wake + step
    assert summary["errors"] == []
    assert summary["entry_count"] >= 2
