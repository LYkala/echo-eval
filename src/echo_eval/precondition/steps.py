"""Normalize precondition steps and build prefix+step cache keys."""
from __future__ import annotations

import hashlib
import json
import re


def normalize_step(text: str) -> str:
    s = (text or "").strip()
    s = re.sub(r"\s+", "", s)
    # unify full/half brackets
    s = s.replace("【", "[").replace("】", "]")
    s = s.replace("（", "(").replace("）", ")")
    return s


def resolve_step_settle_sec(
    step_text: str,
    *,
    default_sec: float,
    overrides: dict[str, float] | None = None,
) -> float:
    """Per-step wait after a precondition action (ads, page load, etc.).

    Matching order: exact normalized text → override key contained in step
    (or step contained in key). Falls back to default_sec.
    """
    default = max(0.0, float(default_sec))
    if not overrides:
        return default
    norm = normalize_step(step_text)
    if not norm:
        return default
    exact: dict[str, float] = {}
    for raw_key, raw_val in overrides.items():
        key = normalize_step(str(raw_key))
        if not key:
            continue
        try:
            exact[key] = max(0.0, float(raw_val))
        except (TypeError, ValueError):
            continue
    if norm in exact:
        return exact[norm]
    # Prefer longest matching key so "打开爱奇艺" beats "打开".
    best_key = ""
    best_val = default
    for key, val in exact.items():
        if key in norm or norm in key:
            if len(key) > len(best_key):
                best_key = key
                best_val = val
    return best_val if best_key else default


def step_cache_key(prefix: list[str], current: str) -> str:
    """Key = normalized prefix path + current step (same text, different prefix → different key)."""
    payload = {
        "prefix": [normalize_step(p) for p in prefix],
        "step": normalize_step(current),
    }
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    digest = hashlib.sha1(raw.encode("utf-8")).hexdigest()[:16]
    return f"{digest}:{normalize_step(current)[:40]}"
