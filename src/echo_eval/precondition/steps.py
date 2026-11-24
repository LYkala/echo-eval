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


def step_cache_key(prefix: list[str], current: str) -> str:
    """Key = normalized prefix path + current step (same text, different prefix → different key)."""
    payload = {
        "prefix": [normalize_step(p) for p in prefix],
        "step": normalize_step(current),
    }
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    digest = hashlib.sha1(raw.encode("utf-8")).hexdigest()[:16]
    return f"{digest}:{normalize_step(current)[:40]}"
