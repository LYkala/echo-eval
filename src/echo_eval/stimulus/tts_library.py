"""On-disk TTS utterance library: prebuild wavs, index, runtime lookup."""
from __future__ import annotations

import hashlib
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from echo_eval.stimulus.tts import (
    CURATED_EDGE_VOICES,
    DEFAULT_EDGE_VOICE,
    TtsError,
    TtsResult,
    synthesize,
    wav_duration_sec,
)

INDEX_NAME = "index.json"
DEFAULT_LIBRARY_DIR = "assets/tts_library"


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def text_hash(text: str) -> str:
    return hashlib.sha1((text or "").strip().encode("utf-8")).hexdigest()[:12]


def voice_profile(backend: str, voice: str = "") -> str:
    b = (backend or "edge").strip().lower()
    v = (voice or "").strip()
    if b == "edge":
        return f"edge:{(v or DEFAULT_EDGE_VOICE)}"
    if b == "sapi":
        return f"sapi:{(v or 'default')}"
    return f"{b}:{v or 'default'}"


def parse_voice_profile(profile: str) -> tuple[str, str]:
    """Split 'edge:zh-CN-XiaoxiaoNeural' → ('edge', 'zh-CN-XiaoxiaoNeural')."""
    raw = (profile or "").strip()
    if ":" not in raw:
        return "edge", raw or DEFAULT_EDGE_VOICE
    backend, voice = raw.split(":", 1)
    return (backend or "edge").strip().lower(), (voice or "").strip()


def voice_label(profile: str) -> str:
    backend, voice = parse_voice_profile(profile)
    if backend == "edge":
        for v in CURATED_EDGE_VOICES:
            if v.get("id") == voice:
                return str(v.get("label") or voice)
        return voice or profile
    if backend == "sapi":
        return f"本机 SAPI（{voice or 'default'}）"
    return profile


def _safe_token(s: str) -> str:
    out = []
    for ch in (s or ""):
        if ch.isalnum() or ch in ("-", "_", "."):
            out.append(ch)
        else:
            out.append("_")
    return "".join(out)[:80] or "x"


def resolve_library_root(library_dir: str | Path, *, repo_root: Path | None = None) -> Path:
    p = Path(library_dir or DEFAULT_LIBRARY_DIR)
    if not p.is_absolute():
        root = repo_root
        if root is None:
            from echo_eval.config import REPO_ROOT

            root = REPO_ROOT
        p = root / p
    return p.resolve()


def load_index(library_root: Path) -> dict[str, Any]:
    path = library_root / INDEX_NAME
    if not path.is_file():
        return {"version": 1, "entries": [], "updated_at": ""}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {"version": 1, "entries": [], "updated_at": ""}
    if not isinstance(data, dict):
        return {"version": 1, "entries": [], "updated_at": ""}
    data.setdefault("version", 1)
    data.setdefault("entries", [])
    return data


def save_index(library_root: Path, data: dict[str, Any]) -> None:
    library_root.mkdir(parents=True, exist_ok=True)
    data["updated_at"] = _now()
    path = library_root / INDEX_NAME
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(path)


def entry_key(
    *,
    case_id: str,
    step_index: int,
    text: str,
    profile: str,
) -> str:
    return f"{profile}|{case_id}|{int(step_index)}|{text_hash(text)}"


def find_entry(
    library_root: Path,
    *,
    case_id: str,
    step_index: int,
    text: str,
    profile: str,
) -> dict[str, Any] | None:
    data = load_index(library_root)
    key = entry_key(case_id=case_id, step_index=step_index, text=text, profile=profile)
    th = text_hash(text)
    soft_case: dict[str, Any] | None = None
    soft_text: dict[str, Any] | None = None
    for e in data.get("entries") or []:
        if not isinstance(e, dict):
            continue
        rel = str(e.get("rel_path") or "")
        if not rel or not (library_root / rel).is_file():
            continue
        if e.get("key") == key:
            return e
        same_profile = str(e.get("voice_profile") or "") == profile
        if not same_profile:
            continue
        # Soft match: same case/step/profile and text hash
        if (
            soft_case is None
            and str(e.get("case_id") or "") == case_id
            and int(e.get("step_index") or -1) == int(step_index)
            and str(e.get("text_hash") or "") == th
        ):
            soft_case = e
        # Custom / cross-case: same profile + text (e.g. UI-added utterance)
        if soft_text is None and str(e.get("text_hash") or "") == th:
            soft_text = e
    return soft_case or soft_text


def resolve_wav(
    library_root: Path,
    *,
    case_id: str,
    step_index: int,
    text: str,
    profile: str,
) -> Path | None:
    e = find_entry(
        library_root,
        case_id=case_id,
        step_index=step_index,
        text=text,
        profile=profile,
    )
    if not e:
        return None
    p = library_root / str(e.get("rel_path") or "")
    return p if p.is_file() else None


def _upsert_entry(data: dict[str, Any], entry: dict[str, Any]) -> None:
    entries = list(data.get("entries") or [])
    key = entry.get("key")
    out: list[dict[str, Any]] = []
    replaced = False
    for e in entries:
        if isinstance(e, dict) and e.get("key") == key:
            out.append(entry)
            replaced = True
        elif isinstance(e, dict):
            out.append(e)
    if not replaced:
        out.append(entry)
    data["entries"] = out


def add_utterance(
    library_root: Path,
    *,
    text: str,
    case_id: str = "",
    step_index: int = 0,
    backend: str = "edge",
    voice: str = "",
    rate: int = 0,
    ffmpeg_path: str = "",
    fallback_sapi: bool = True,
    source: str = "custom",
    force: bool = False,
) -> dict[str, Any]:
    """Synthesize one utterance into the library (skip if index hit unless force)."""
    text = (text or "").strip()
    if not text:
        raise TtsError("语音库文本为空")
    library_root = Path(library_root)
    library_root.mkdir(parents=True, exist_ok=True)
    profile = voice_profile(backend, voice)
    cid = (case_id or "").strip() or f"custom_{text_hash(text)}"
    step = int(step_index)

    existing = find_entry(
        library_root, case_id=cid, step_index=step, text=text, profile=profile
    )
    if existing and not force:
        rel = str(existing.get("rel_path") or "")
        existing_path = library_root / rel if rel else None
        if existing_path is not None and existing_path.is_file():
            return {
                "status": "exists",
                "entry": existing,
                "path": str(existing_path),
            }
        # Index hit but wav missing → fall through and re-synthesize (补充合成)

    profile_dir = _safe_token(profile.replace(":", "_"))
    fname = f"{_safe_token(cid)}_{step:02d}_{text_hash(text)}.wav"
    rel_path = f"{profile_dir}/{fname}"
    out_wav = library_root / rel_path
    out_wav.parent.mkdir(parents=True, exist_ok=True)

    result = synthesize(
        text,
        out_wav,
        backend=backend,
        voice=voice,
        rate=rate,
        ffmpeg_path=ffmpeg_path,
        fallback_sapi=fallback_sapi,
        case_id=cid,
        step_index=max(1, step) if step else 1,
    )

    entry = {
        "key": entry_key(case_id=cid, step_index=step, text=text, profile=profile),
        "case_id": cid,
        "step_index": step,
        "text": text,
        "text_hash": text_hash(text),
        "voice_profile": profile,
        "rel_path": rel_path.replace("\\", "/"),
        "source": source,
        "synth_backend": result.backend,
        "duration_sec": result.duration_sec,
        "updated_at": _now(),
    }
    data = load_index(library_root)
    _upsert_entry(data, entry)
    save_index(library_root, data)
    return {"status": "created", "entry": entry, "path": str(out_wav.resolve())}


def _emit_prebuild_progress(payload: dict[str, Any]) -> None:
    """One NDJSON progress line for streaming console UI."""
    print(json.dumps(payload, ensure_ascii=False), flush=True)


def prebuild_from_excel(
    excel_path: str | Path,
    library_root: Path,
    *,
    backend: str = "edge",
    voice: str = "",
    rate: int = 0,
    ffmpeg_path: str = "",
    fallback_sapi: bool = True,
    wake_text: str = "",
    force: bool = False,
    default_wait_sec: float = 3.0,
    progress: bool = False,
) -> dict[str, Any]:
    """Pre-synthesize all case steps (+ optional wake) into the library.

    force=False skips intact library hits (补充缺失/失败)；force=True 全量重做。
    When progress=True, prints NDJSON lines: progress / done.
    """
    from echo_eval.cases.excel_cases import load_cases

    cases = load_cases(excel_path, default_wait_sec=float(default_wait_sec))
    created = 0
    exists = 0
    errors: list[dict[str, str]] = []

    jobs: list[dict[str, Any]] = []
    wake = (wake_text or "").strip()
    if wake:
        jobs.append(
            {
                "text": wake,
                "case_id": "_wake_",
                "step_index": 0,
                "source": "wake",
                "label": f"唤醒「{wake}」",
            }
        )
    for case in cases:
        for i, step in enumerate(case.steps, 1):
            jobs.append(
                {
                    "text": step.text,
                    "case_id": case.case_id,
                    "step_index": i,
                    "source": "dataset",
                    "label": f"{case.case_id}#{i}",
                }
            )

    total = len(jobs)
    if progress:
        _emit_prebuild_progress(
            {
                "type": "start",
                "total": total,
                "backend": backend,
                "voice": voice,
                "force": bool(force),
                "wake_text": wake,
                "wake_included": bool(wake),
            }
        )

    for done_i, job in enumerate(jobs, 1):
        try:
            r = add_utterance(
                library_root,
                text=str(job["text"]),
                case_id=str(job["case_id"]),
                step_index=int(job["step_index"]),
                backend=backend,
                voice=voice,
                rate=rate,
                ffmpeg_path=ffmpeg_path,
                fallback_sapi=fallback_sapi,
                source=str(job["source"]),
                force=force,
            )
            status = str(r.get("status") or "")
            if status == "created":
                created += 1
            else:
                exists += 1
            if progress:
                _emit_prebuild_progress(
                    {
                        "type": "progress",
                        "done": done_i,
                        "total": total,
                        "percent": round(100.0 * done_i / float(total), 1) if total else 100.0,
                        "status": status or "ok",
                        "case_id": job["case_id"],
                        "step": str(job["step_index"]),
                        "label": job["label"],
                        "created": created,
                        "exists": exists,
                        "errors": len(errors),
                    }
                )
        except Exception as e:
            errors.append(
                {
                    "case_id": str(job["case_id"]),
                    "step": str(job["step_index"]),
                    "error": str(e),
                }
            )
            if progress:
                _emit_prebuild_progress(
                    {
                        "type": "progress",
                        "done": done_i,
                        "total": total,
                        "percent": round(100.0 * done_i / float(total), 1) if total else 100.0,
                        "status": "error",
                        "case_id": job["case_id"],
                        "step": str(job["step_index"]),
                        "label": job["label"],
                        "error": str(e),
                        "created": created,
                        "exists": exists,
                        "errors": len(errors),
                    }
                )

    data = load_index(library_root)
    result = {
        "type": "done",
        "library_root": str(library_root),
        "total": total,
        "created": created,
        "exists": exists,
        "errors": errors,
        "entry_count": len(data.get("entries") or []),
        "updated_at": data.get("updated_at") or "",
        "wake_included": bool(wake),
        "wake_text": wake,
        "backend": backend,
        "voice": voice,
        "force": bool(force),
    }
    if progress:
        _emit_prebuild_progress(result)
    return result


def list_library_voices(library_root: Path) -> list[dict[str, Any]]:
    """Aggregate usable voices already present in the library (by voice_profile)."""
    root = Path(library_root)
    data = load_index(root)
    counts: dict[str, int] = {}
    samples: dict[str, str] = {}
    for e in data.get("entries") or []:
        if not isinstance(e, dict):
            continue
        profile = str(e.get("voice_profile") or "").strip()
        if not profile:
            continue
        rel = str(e.get("rel_path") or "")
        if not rel or not (root / rel).is_file():
            continue
        counts[profile] = counts.get(profile, 0) + 1
        if profile not in samples:
            samples[profile] = rel.replace("\\", "/")
    out: list[dict[str, Any]] = []
    for profile, count in sorted(counts.items(), key=lambda kv: (-kv[1], kv[0])):
        backend, voice = parse_voice_profile(profile)
        out.append(
            {
                "profile": profile,
                "backend": backend,
                "voice": voice,
                "label": voice_label(profile),
                "count": count,
                "sample_rel_path": samples.get(profile) or "",
            }
        )
    return out


def library_status(library_root: Path) -> dict[str, Any]:
    root = Path(library_root)
    data = load_index(root)
    entries = [e for e in (data.get("entries") or []) if isinstance(e, dict)]
    missing = 0
    for e in entries:
        rel = str(e.get("rel_path") or "")
        if not rel or not (root / rel).is_file():
            missing += 1
    voices = list_library_voices(root)
    return {
        "library_root": str(root),
        "exists": root.is_dir(),
        "entry_count": len(entries),
        "missing_files": missing,
        "updated_at": data.get("updated_at") or "",
        "voices": voices,
        "entries": entries[-50:],  # tail for UI
    }


def copy_library_wav_to(out_path: Path, src: Path) -> TtsResult:
    out_path = Path(out_path)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, out_path)
    return TtsResult(
        path=str(out_path.resolve()),
        backend="library",
        text="",
        duration_sec=round(wav_duration_sec(out_path), 3),
        voice="library",
    )
