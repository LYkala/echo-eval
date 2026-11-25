"""TTS MOS-like fluency score from host-mic wav of device speaker reply."""
from __future__ import annotations

import json
import math
import struct
import time
import wave
from pathlib import Path
from typing import Any, Literal

Backend = Literal["auto", "heuristic", "mock"]


def _read_pcm16_mono(path: Path) -> tuple[list[int], int]:
    with wave.open(str(path), "rb") as w:
        ch = w.getnchannels()
        sw = w.getsampwidth()
        rate = w.getframerate() or 16000
        n = w.getnframes()
        raw = w.readframes(n)
    if sw != 2:
        raise ValueError(f"仅支持 16-bit PCM wav，got samplewidth={sw}")
    samples = list(struct.unpack("<" + "h" * (len(raw) // 2), raw))
    if ch > 1:
        samples = samples[0::ch]
    return samples, int(rate)


def _rms_frames(samples: list[int], rate: int, frame_ms: float = 20.0) -> list[float]:
    frame = max(1, int(rate * frame_ms / 1000.0))
    out: list[float] = []
    for i in range(0, len(samples), frame):
        chunk = samples[i : i + frame]
        if not chunk:
            break
        acc = 0.0
        for x in chunk:
            acc += float(x) * float(x)
        out.append(math.sqrt(acc / len(chunk)))
    return out


def analyze_reply_wav(
    path: Path | str,
    *,
    min_speech_sec: float = 0.3,
    silence_db_below_peak: float = 25.0,
) -> dict[str, Any]:
    """
    Objective fluency-oriented features → MOS-like score in [1, 5].

    Returns status: scored | no_speech | error
    """
    p = Path(path)
    if not p.is_file():
        return {
            "status": "error",
            "mos": None,
            "reason": f"音频不存在: {p}",
            "features": {},
        }
    try:
        samples, rate = _read_pcm16_mono(p)
    except Exception as e:
        return {"status": "error", "mos": None, "reason": str(e), "features": {}}

    if len(samples) < rate * 0.05:
        return {
            "status": "no_speech",
            "mos": None,
            "reason": "录音过短",
            "features": {"duration_sec": len(samples) / float(rate)},
        }

    frames = _rms_frames(samples, rate)
    if not frames:
        return {"status": "no_speech", "mos": None, "reason": "无有效帧", "features": {}}

    peak = max(frames) or 1.0
    # silence if rms below peak / 10^(db/20)
    thr = peak / (10 ** (silence_db_below_peak / 20.0))
    speech_flags = [x >= thr for x in frames]
    speech_frames = sum(1 for x in speech_flags if x)
    frame_sec = 0.02
    speech_sec = speech_frames * frame_sec
    duration_sec = len(frames) * frame_sec
    speech_ratio = speech_frames / float(len(frames))

    # pause fragmentation: count speech→silence transitions
    transitions = 0
    for i in range(1, len(speech_flags)):
        if speech_flags[i] != speech_flags[i - 1]:
            transitions += 1
    # clipping proxy
    clip_n = sum(1 for s in samples if s >= 32760 or s <= -32760)
    clip_ratio = clip_n / float(len(samples))

    features = {
        "duration_sec": round(duration_sec, 3),
        "speech_sec": round(speech_sec, 3),
        "speech_ratio": round(speech_ratio, 4),
        "transitions": transitions,
        "clip_ratio": round(clip_ratio, 6),
        "peak_rms": round(peak, 2),
        "sample_rate": rate,
    }

    if speech_sec < float(min_speech_sec):
        return {
            "status": "no_speech",
            "mos": None,
            "reason": f"有效语音约 {speech_sec:.2f}s < {min_speech_sec}s，记为 N/A",
            "features": features,
        }

    # Heuristic MOS: start 5, penalize long silence holes, many cuts, clipping
    mos = 5.0
    if speech_ratio < 0.25:
        mos -= 1.5
    elif speech_ratio < 0.4:
        mos -= 0.7
    # excessive chopping
    cuts = max(0, transitions // 2)
    if cuts >= 8:
        mos -= 1.5
    elif cuts >= 5:
        mos -= 1.0
    elif cuts >= 3:
        mos -= 0.5
    if clip_ratio > 0.01:
        mos -= 1.0
    elif clip_ratio > 0.002:
        mos -= 0.5
    # very short reply
    if speech_sec < 0.6:
        mos -= 0.3
    mos = max(1.0, min(5.0, round(mos * 2) / 2.0))  # 0.5 steps

    return {
        "status": "scored",
        "mos": mos,
        "reason": "主机麦启发式流畅度评分（非听感大模型）",
        "features": features,
    }


def evaluate_mos_mock(path: Path | str | None, *, force_status: str | None = None) -> dict[str, Any]:
    if force_status == "no_speech":
        return {
            "status": "no_speech",
            "mos": None,
            "reason": "mock: no_speech",
            "features": {},
        }
    p = Path(path) if path else None
    if p and "mos_fail" in p.name:
        return {
            "status": "scored",
            "mos": 2.0,
            "reason": "mock: low score",
            "features": {},
        }
    if p and p.is_file():
        return {
            "status": "scored",
            "mos": 4.5,
            "reason": "mock: assumed fluent",
            "features": {},
        }
    return {
        "status": "no_speech",
        "mos": None,
        "reason": "mock: missing wav",
        "features": {},
    }


def judge_mos_utterance(
    *,
    wav_path: Path | str | None,
    backend: Backend = "auto",
    min_speech_sec: float = 0.3,
    write_path: Path | str | None = None,
) -> dict[str, Any]:
    chosen = "mock" if backend == "mock" else "heuristic"
    if chosen == "mock":
        result = evaluate_mos_mock(wav_path)
    else:
        if not wav_path:
            result = {
                "status": "no_speech",
                "mos": None,
                "reason": "无麦克风录音",
                "features": {},
            }
        else:
            result = analyze_reply_wav(wav_path, min_speech_sec=min_speech_sec)

    data: dict[str, Any] = {
        "mode": "tts_mos",
        "source": chosen,
        "backend": chosen,
        "wav_path": str(wav_path) if wav_path else "",
        "status": result.get("status"),
        "mos": result.get("mos"),
        "reason": result.get("reason"),
        "features": result.get("features") or {},
        "timestamp": time.time(),
    }
    # N/A does not count as fail for parallel metrics; scored always "ok" presence-wise
    data["passed"] = result.get("status") == "scored"
    data["na"] = result.get("status") == "no_speech"
    if write_path:
        out = Path(write_path)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        data["evaluation_path"] = str(out.resolve())
    return data
