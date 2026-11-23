"""Optional live progress file for the Web console (ECHO_EVAL_PROGRESS_FILE)."""
from __future__ import annotations

import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def _path() -> Path | None:
    raw = (os.environ.get("ECHO_EVAL_PROGRESS_FILE") or "").strip()
    if not raw:
        return None
    return Path(raw)


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _read(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {}


def _write(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(path)


def init_progress(cases: list[tuple[str, str]]) -> None:
    """cases: list of (case_id, name)."""
    path = _path()
    if not path:
        return
    data = {
        "total": len(cases),
        "current_index": 0,
        "current_case_id": "",
        "current_name": "",
        "phase": "queued",
        "phase_label": "排队中",
        "updated_at": _now(),
        "cases": [
            {
                "index": i + 1,
                "case_id": cid,
                "name": name,
                "status": "pending",
                "phase": "pending",
                "phase_label": "等待",
            }
            for i, (cid, name) in enumerate(cases)
        ],
    }
    _write(path, data)
    print(
        f"[echo-eval] progress file={path} total={len(cases)}",
        flush=True,
    )


def update_case(
    *,
    index: int,
    case_id: str,
    name: str,
    status: str,
    phase: str,
    phase_label: str,
) -> None:
    path = _path()
    if not path:
        return
    data = _read(path)
    cases = list(data.get("cases") or [])
    for row in cases:
        if int(row.get("index") or 0) == index or row.get("case_id") == case_id:
            row["status"] = status
            row["phase"] = phase
            row["phase_label"] = phase_label
            row["name"] = name or row.get("name") or ""
            break
    else:
        cases.append(
            {
                "index": index,
                "case_id": case_id,
                "name": name,
                "status": status,
                "phase": phase,
                "phase_label": phase_label,
            }
        )
    data["cases"] = cases
    data["total"] = int(data.get("total") or len(cases))
    data["current_index"] = index
    data["current_case_id"] = case_id
    data["current_name"] = name
    data["phase"] = phase
    data["phase_label"] = phase_label
    data["updated_at"] = _now()
    done = sum(1 for c in cases if c.get("status") in ("passed", "failed", "error"))
    data["done"] = done
    _write(path, data)
    print(
        f"[echo-eval] progress {index}/{data['total']} {case_id} {status} {phase_label}",
        flush=True,
    )


def finish_batch() -> None:
    path = _path()
    if not path:
        return
    data = _read(path)
    total = int(data.get("total") or 0)
    data["phase"] = "finished"
    data["phase_label"] = "全部完成"
    data["current_index"] = total
    data["done"] = total
    data["updated_at"] = _now()
    _write(path, data)
