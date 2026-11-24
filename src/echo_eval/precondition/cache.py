"""On-disk precondition step cache: untested / passed / failed / stale."""
from __future__ import annotations

import json
import threading
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Literal

StepStatus = Literal["untested", "passed", "failed", "stale"]


@dataclass
class StepCacheEntry:
    key: str
    step_text: str
    prefix: list[str]
    status: StepStatus = "untested"
    adb_commands: list[str] = field(default_factory=list)
    kind: str = ""  # deterministic | vl | mixed
    package: str = ""
    judge_notes: str = ""
    screenshots: list[str] = field(default_factory=list)
    device_wm_size: str = ""
    updated_at: str = ""
    case_ids: list[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "StepCacheEntry":
        status = str(data.get("status") or "untested")
        if status not in ("untested", "passed", "failed", "stale"):
            status = "untested"
        return cls(
            key=str(data.get("key") or ""),
            step_text=str(data.get("step_text") or ""),
            prefix=[str(x) for x in (data.get("prefix") or [])],
            status=status,  # type: ignore[arg-type]
            adb_commands=[str(x) for x in (data.get("adb_commands") or [])],
            kind=str(data.get("kind") or ""),
            package=str(data.get("package") or ""),
            judge_notes=str(data.get("judge_notes") or ""),
            screenshots=[str(x) for x in (data.get("screenshots") or [])],
            device_wm_size=str(data.get("device_wm_size") or ""),
            updated_at=str(data.get("updated_at") or ""),
            case_ids=[str(x) for x in (data.get("case_ids") or [])],
        )


class PreconditionCache:
    def __init__(self, root: Path) -> None:
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)
        self.path = self.root / "steps.json"
        self._lock = threading.Lock()
        self._entries: dict[str, StepCacheEntry] = {}
        self._load()

    def _load(self) -> None:
        if not self.path.is_file():
            return
        try:
            raw = json.loads(self.path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return
        items = raw.get("steps") if isinstance(raw, dict) else None
        if not isinstance(items, list):
            return
        for item in items:
            if not isinstance(item, dict):
                continue
            entry = StepCacheEntry.from_dict(item)
            if entry.key:
                self._entries[entry.key] = entry

    def save(self) -> None:
        with self._lock:
            payload = {
                "updated_at": datetime.now(timezone.utc).isoformat(),
                "steps": [e.to_dict() for e in self._entries.values()],
            }
            self.path.write_text(
                json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
            )

    def get(self, key: str) -> StepCacheEntry | None:
        return self._entries.get(key)

    def upsert(self, entry: StepCacheEntry) -> None:
        entry.updated_at = datetime.now(timezone.utc).isoformat()
        with self._lock:
            existing = self._entries.get(entry.key)
            if existing and entry.case_ids:
                merged = list(dict.fromkeys([*existing.case_ids, *entry.case_ids]))
                entry.case_ids = merged
            self._entries[entry.key] = entry

    def status_for(self, key: str) -> StepStatus:
        e = self._entries.get(key)
        return e.status if e else "untested"
