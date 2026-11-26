"""TTS MOS-like fluency score from device reply wav (host mic or scrcpy extract)."""
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


def _rms_and_zcr(
    samples: list[int], rate: int, frame_ms: float = 20.0
) -> tuple[list[float], list[float]]:
    frame = max(1, int(rate * frame_ms / 1000.0))
    rms_out: list[float] = []
    zcr_out: list[float] = []
    for i in range(0, len(samples), frame):
        chunk = samples[i : i + frame]
        if not chunk:
            break
        acc = 0.0
        for x in chunk:
            acc += float(x) * float(x)
        rms_out.append(math.sqrt(acc / len(chunk)))
        crosses = 0
        for j in range(1, len(chunk)):
            a = chunk[j - 1]
            b = chunk[j]
            if (a >= 0 > b) or (a < 0 <= b):
                crosses += 1
        zcr_out.append(crosses / float(len(chunk)))
    return rms_out, zcr_out


def _mod_index(rms: list[float], center: int, half: int = 5) -> float:
    """Local RMS variation (syllable-like modulation). Higher → more speech-like."""
    lo = max(0, center - half)
    hi = min(len(rms), center + half + 1)
    window = rms[lo:hi]
    if len(window) < 3:
        return 0.0
    mean = sum(window) / float(len(window))
    if mean < 1e-6:
        return 0.0
    var = sum((x - mean) ** 2 for x in window) / float(len(window))
    return math.sqrt(var) / mean


def _label_frames(
    rms: list[float],
    zcr: list[float],
    *,
    silence_db_below_peak: float = 25.0,
) -> list[str]:
    """
    Label each 20ms frame: silence | speech | media.

    media ≈ sustained energy with low modulation (songs / video beds).
    speech ≈ energy + syllable modulation and/or mid ZCR.
    """
    if not rms:
        return []
    peak = max(rms) or 1.0
    thr = peak / (10 ** (silence_db_below_peak / 20.0))
    labels: list[str] = []
    for i, (e, z) in enumerate(zip(rms, zcr)):
        if e < thr:
            labels.append("silence")
            continue
        mod = _mod_index(rms, i)
        # Voiced speech often mid ZCR; music/beds often lower modulation.
        speech_like = (mod >= 0.18 and 0.01 <= z <= 0.35) or (
            mod >= 0.28 and e >= thr
        )
        media_like = mod < 0.12 and e >= thr * 1.2
        if speech_like and not media_like:
            labels.append("speech")
        elif media_like and not speech_like:
            labels.append("media")
        elif speech_like:
            labels.append("speech")
        else:
            # Ambiguous active audio: prefer speech so short TTS is not dropped
            labels.append("speech" if mod >= 0.15 else "media")
    # Smooth short blips: isolated media inside speech → speech; isolated speech
    # inside long media → media
    smoothed = list(labels)
    for i in range(1, len(smoothed) - 1):
        if smoothed[i - 1] == smoothed[i + 1] and smoothed[i] != smoothed[i - 1]:
            if smoothed[i - 1] in ("speech", "media"):
                smoothed[i] = smoothed[i - 1]
    return smoothed


def analyze_reply_wav(
    path: Path | str,
    *,
    min_speech_sec: float = 0.3,
    silence_db_below_peak: float = 25.0,
    ignore_non_speech: bool = True,
) -> dict[str, Any]:
    """
    Objective fluency-oriented features → MOS-like score in [1, 5].

    When ignore_non_speech=True (default), sustained song/video-bed-like frames
    are excluded from fluency metrics so internal-capture noise does not dominate.

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

    frames, zcr = _rms_and_zcr(samples, rate)
    if not frames:
        return {"status": "no_speech", "mos": None, "reason": "无有效帧", "features": {}}

    labels = _label_frames(
        frames, zcr, silence_db_below_peak=silence_db_below_peak
    )
    frame_sec = 0.02
    duration_sec = len(frames) * frame_sec
    n_speech = sum(1 for x in labels if x == "speech")
    n_media = sum(1 for x in labels if x == "media")
    n_sil = sum(1 for x in labels if x == "silence")
    speech_sec = n_speech * frame_sec
    media_sec = n_media * frame_sec

    # Frames used for fluency: speech only when filtering; else any non-silence
    if ignore_non_speech:
        active_flags = [lab == "speech" for lab in labels]
        score_rms = [frames[i] for i, lab in enumerate(labels) if lab == "speech"]
    else:
        peak0 = max(frames) or 1.0
        thr0 = peak0 / (10 ** (silence_db_below_peak / 20.0))
        active_flags = [x >= thr0 for x in frames]
        score_rms = [frames[i] for i, f in enumerate(active_flags) if f]

    speech_frames = sum(1 for x in active_flags if x)
    speech_ratio = speech_frames / float(len(frames)) if frames else 0.0

    transitions = 0
    for i in range(1, len(active_flags)):
        if active_flags[i] != active_flags[i - 1]:
            transitions += 1

    # Clipping only on speech-focused samples when filtering
    if ignore_non_speech and any(active_flags):
        frame_n = max(1, int(rate * frame_sec))
        clip_n = 0
        clip_den = 0
        for i, flag in enumerate(active_flags):
            if not flag:
                continue
            chunk = samples[i * frame_n : (i + 1) * frame_n]
            clip_den += len(chunk)
            clip_n += sum(1 for s in chunk if s >= 32760 or s <= -32760)
        clip_ratio = (clip_n / float(clip_den)) if clip_den else 0.0
    else:
        clip_n = sum(1 for s in samples if s >= 32760 or s <= -32760)
        clip_ratio = clip_n / float(len(samples))

    peak = max(score_rms) if score_rms else (max(frames) or 1.0)
    features = {
        "duration_sec": round(duration_sec, 3),
        "speech_sec": round(speech_sec if ignore_non_speech else speech_frames * frame_sec, 3),
        "speech_ratio": round(speech_ratio, 4),
        "media_sec": round(media_sec, 3),
        "silence_sec": round(n_sil * frame_sec, 3),
        "ignore_non_speech": bool(ignore_non_speech),
        "transitions": transitions,
        "clip_ratio": round(clip_ratio, 6),
        "peak_rms": round(peak, 2),
        "sample_rate": rate,
    }

    effective_speech = float(features["speech_sec"])
    if effective_speech < float(min_speech_sec):
        reason = f"有效语音约 {effective_speech:.2f}s < {min_speech_sec}s，记为 N/A"
        if ignore_non_speech and media_sec >= min_speech_sec:
            reason += f"（已忽略约 {media_sec:.2f}s 疑似歌曲/视频床底）"
        return {
            "status": "no_speech",
            "mos": None,
            "reason": reason,
            "features": features,
        }

    mos = 5.0
    if speech_ratio < 0.25:
        mos -= 1.5
    elif speech_ratio < 0.4:
        mos -= 0.7
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
    if effective_speech < 0.6:
        mos -= 0.3
    # Heavy media under mix still hurts a bit even after filtering
    if ignore_non_speech and media_sec > 1.0 and media_sec > effective_speech:
        mos -= 0.5
    mos = max(1.0, min(5.0, round(mos * 2) / 2.0))

    reason = "启发式流畅度评分（非听感大模型）"
    if ignore_non_speech:
        reason += "；已忽略疑似歌曲/视频等非语音段"
    return {
        "status": "scored",
        "mos": mos,
        "reason": reason,
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
    ignore_non_speech: bool = True,
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
            result = analyze_reply_wav(
                wav_path,
                min_speech_sec=min_speech_sec,
                ignore_non_speech=ignore_non_speech,
            )

    data: dict[str, Any] = {
        "mode": "tts_mos",
        "source": chosen,
        "backend": chosen,
        "wav_path": str(wav_path) if wav_path else "",
        "status": result.get("status"),
        "mos": result.get("mos"),
        "reason": result.get("reason"),
        "features": result.get("features") or {},
        "ignore_non_speech": bool(ignore_non_speech),
        "timestamp": time.time(),
    }
    data["passed"] = result.get("status") == "scored"
    data["na"] = result.get("status") == "no_speech"
    if write_path:
        out = Path(write_path)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        data["evaluation_path"] = str(out.resolve())
    return data
