"""On-disk TTS utterance library: prebuild wavs, index, runtime lookup."""
from __future__ import annotations

import hashlib
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from echo_eval.stimulus.tts import (
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
    soft_case_any: dict[str, Any] | None = None
    soft_text: dict[str, Any] | None = None
    soft_text_any: dict[str, Any] | None = None
    for e in data.get("entries") or []:
        if not isinstance(e, dict):
            continue
        rel = str(e.get("rel_path") or "")
        if not rel or not (library_root / rel).is_file():
            continue
        if e.get("key") == key:
            return e
        same_case_step = (
            str(e.get("case_id") or "") == case_id
            and int(e.get("step_index") or -1) == int(step_index)
            and str(e.get("text_hash") or "") == th
        )
        same_text = str(e.get("text_hash") or "") == th
        same_profile = str(e.get("voice_profile") or "") == profile
        # Soft match: same case/step/profile and text hash
        if soft_case is None and same_case_step and same_profile:
            soft_case = e
        # Prebuild voice may differ from run voice — still prefer same case/step/text
        if soft_case_any is None and same_case_step:
            soft_case_any = e
        # Custom / cross-case: same profile + text (e.g. UI-added utterance)
        if soft_text is None and same_text and same_profile:
            soft_text = e
        if soft_text_any is None and same_text:
            soft_text_any = e
    return soft_case or soft_case_any or soft_text or soft_text_any


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
        return {
            "status": "exists",
            "entry": existing,
            "path": str(library_root / rel),
        }

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
) -> dict[str, Any]:
    """Pre-synthesize all case steps (+ optional wake) into the library."""
    from echo_eval.cases.excel_cases import load_cases

    cases = load_cases(excel_path, default_wait_sec=float(default_wait_sec))
    created = 0
    exists = 0
    errors: list[dict[str, str]] = []
    total = 0

    wake = (wake_text or "").strip()
    if wake:
        total += 1
        try:
            r = add_utterance(
                library_root,
                text=wake,
                case_id="_wake_",
                step_index=0,
                backend=backend,
                voice=voice,
                rate=rate,
                ffmpeg_path=ffmpeg_path,
                fallback_sapi=fallback_sapi,
                source="wake",
                force=force,
            )
            if r["status"] == "created":
                created += 1
            else:
                exists += 1
        except Exception as e:
            errors.append({"case_id": "_wake_", "step": "0", "error": str(e)})

    for case in cases:
        for i, step in enumerate(case.steps, 1):
            total += 1
            try:
                r = add_utterance(
                    library_root,
                    text=step.text,
                    case_id=case.case_id,
                    step_index=i,
                    backend=backend,
                    voice=voice,
                    rate=rate,
                    ffmpeg_path=ffmpeg_path,
                    fallback_sapi=fallback_sapi,
                    source="dataset",
                    force=force,
                )
                if r["status"] == "created":
                    created += 1
                else:
                    exists += 1
            except Exception as e:
                errors.append(
                    {
                        "case_id": case.case_id,
                        "step": str(i),
                        "error": str(e),
                    }
                )

    data = load_index(library_root)
    return {
        "library_root": str(library_root),
        "total": total,
        "created": created,
        "exists": exists,
        "errors": errors,
        "entry_count": len(data.get("entries") or []),
        "updated_at": data.get("updated_at") or "",
    }


def library_status(library_root: Path) -> dict[str, Any]:
    root = Path(library_root)
    data = load_index(root)
    entries = [e for e in (data.get("entries") or []) if isinstance(e, dict)]
    missing = 0
    for e in entries:
        rel = str(e.get("rel_path") or "")
        if not rel or not (root / rel).is_file():
            missing += 1
    return {
        "library_root": str(root),
        "exists": root.is_dir(),
        "entry_count": len(entries),
        "missing_files": missing,
        "updated_at": data.get("updated_at") or "",
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
