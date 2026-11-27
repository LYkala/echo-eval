"""Optional live progress file for the Web console (ECHO_EVAL_PROGRESS_FILE)."""
from __future__ import annotations

import json
import os
import threading
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

_lock = threading.Lock()


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


def _recompute(data: dict[str, Any]) -> None:
    cases = list(data.get("cases") or [])
    data["total"] = int(data.get("total") or len(cases))
    data["device_done"] = sum(
        1 for c in cases if c.get("device_status") in ("done", "error")
    )
    data["judge_done"] = sum(
        1
        for c in cases
        if c.get("judge_status") in ("passed", "failed", "error", "skipped")
    )
    # Overall "done" = fully finished (device + judge terminal).
    data["done"] = sum(
        1
        for c in cases
        if c.get("status") in ("passed", "failed", "error")
        and c.get("device_status") in ("done", "error")
        and c.get("judge_status") in ("passed", "failed", "error", "skipped")
    )


def _pending_case(index: int, case_id: str, name: str) -> dict[str, Any]:
    return {
        "index": index,
        "case_id": case_id,
        "name": name,
        "status": "pending",
        "phase": "pending",
        "phase_label": "等待设备执行",
        "device_status": "pending",
        "device_label": "等待",
        "judge_status": "pending",
        "judge_label": "等待",
    }


def init_progress(cases: list[tuple[str, str]]) -> None:
    """cases: list of (case_id, name).

    If progress.json already lists sibling cases not in this batch (single-case /
    failed-only rerun), reset only the batch rows and keep the rest so the Web
    history is not wiped.
    """
    path = _path()
    if not path:
        return
    batch = [(str(cid), str(name or cid)) for cid, name in cases]
    batch_ids = {cid for cid, _ in batch}

    with _lock:
        existing = _read(path)
        existing_cases = list(existing.get("cases") or [])
        has_siblings = any(
            str(c.get("case_id") or "") not in batch_ids for c in existing_cases
        )

        if existing_cases and has_siblings:
            by_id: dict[str, dict[str, Any]] = {
                str(c.get("case_id")): dict(c) for c in existing_cases if c.get("case_id")
            }
            for cid, name in batch:
                prev = by_id.get(cid) or {}
                row = _pending_case(int(prev.get("index") or 0) or 0, cid, name)
                # Drop stale run_dir so harvest picks the new capture after rerun.
                by_id[cid] = row

            ordered: list[dict[str, Any]] = []
            seen: set[str] = set()
            for c in existing_cases:
                cid = str(c.get("case_id") or "")
                if not cid or cid in seen:
                    continue
                ordered.append(by_id.get(cid) or dict(c))
                seen.add(cid)
            for cid, name in batch:
                if cid in seen:
                    continue
                ordered.append(by_id[cid])
                seen.add(cid)
            for i, row in enumerate(ordered):
                row["index"] = i + 1

            data: dict[str, Any] = {
                "total": len(ordered),
                "device_done": 0,
                "judge_done": 0,
                "done": 0,
                "current_index": 0,
                "current_case_id": "",
                "current_name": "",
                "phase": "queued",
                "phase_label": "排队中（覆盖重跑）",
                "device_phase_label": "设备：等待开始",
                "judge_phase_label": "评判：等待",
                "updated_at": _now(),
                "cases": ordered,
            }
            _recompute(data)
            _write(path, data)
            print(
                f"[echo-eval] progress merge file={path} total={len(ordered)} "
                f"rerun={len(batch)}",
                flush=True,
            )
            return

        data = {
            "total": len(batch),
            "device_done": 0,
            "judge_done": 0,
            "done": 0,
            "current_index": 0,
            "current_case_id": "",
            "current_name": "",
            "phase": "queued",
            "phase_label": "排队中",
            "device_phase_label": "设备：等待开始",
            "judge_phase_label": "评判：等待",
            "updated_at": _now(),
            "cases": [
                _pending_case(i + 1, cid, name) for i, (cid, name) in enumerate(batch)
            ],
        }
        _write(path, data)
    print(
        f"[echo-eval] progress file={path} total={len(batch)}",
        flush=True,
    )


def update_case(
    *,
    index: int,
    case_id: str,
    name: str,
    status: str | None = None,
    phase: str | None = None,
    phase_label: str | None = None,
    device_status: str | None = None,
    device_label: str | None = None,
    judge_status: str | None = None,
    judge_label: str | None = None,
    run_dir: str | None = None,
    touch_device_current: bool = False,
    touch_judge_current: bool = False,
) -> None:
    path = _path()
    if not path:
        return
    with _lock:
        data = _read(path)
        cases = list(data.get("cases") or [])
        row: dict[str, Any] | None = None
        # Prefer case_id: partial rerun uses batch-local index (often 1) which
        # must not overwrite an earlier sibling that still owns that index.
        want_id = str(case_id or "").strip()
        if want_id:
            for c in cases:
                if str(c.get("case_id") or "") == want_id:
                    row = c
                    break
        if row is None:
            for c in cases:
                if int(c.get("index") or 0) == index:
                    row = c
                    break
        if row is None:
            row = {
                "index": index,
                "case_id": case_id,
                "name": name,
                "status": "pending",
                "phase": "pending",
                "phase_label": "",
                "device_status": "pending",
                "device_label": "等待",
                "judge_status": "pending",
                "judge_label": "等待",
            }
            cases.append(row)

        row["name"] = name or row.get("name") or ""
        if device_status is not None:
            row["device_status"] = device_status
        if device_label is not None:
            row["device_label"] = device_label
        if judge_status is not None:
            row["judge_status"] = judge_status
        if judge_label is not None:
            row["judge_label"] = judge_label
        if run_dir is not None and str(run_dir).strip():
            row["run_dir"] = str(run_dir).strip()
        if status is not None:
            row["status"] = status
        if phase is not None:
            row["phase"] = phase
        if phase_label is not None:
            row["phase_label"] = phase_label
        else:
            # Build a combined label when not explicitly provided.
            dlab = row.get("device_label") or row.get("device_status") or ""
            jlab = row.get("judge_label") or row.get("judge_status") or ""
            row["phase_label"] = f"设备:{dlab} · 评判:{jlab}"

        data["cases"] = cases
        _recompute(data)
        if touch_device_current:
            data["current_index"] = index
            data["current_case_id"] = case_id
            data["current_name"] = name
            data["device_phase_label"] = (
                f"设备：{row.get('device_label') or ''}（{index}/{data['total']}）"
            )
            data["phase"] = "device_running"
            data["phase_label"] = data["device_phase_label"]
        if touch_judge_current:
            data["judge_phase_label"] = (
                f"评判：{row.get('judge_label') or ''}（{case_id}）"
            )
            # Prefer showing judge activity when device batch may already be done.
            if data.get("device_done", 0) >= data.get("total", 0):
                data["phase"] = "judging"
                data["phase_label"] = data["judge_phase_label"]
        data["updated_at"] = _now()
        _write(path, data)
        print(
            f"[echo-eval] progress {index}/{data['total']} {case_id} "
            f"device={row.get('device_status')} judge={row.get('judge_status')} "
            f"{row.get('phase_label')}",
            flush=True,
        )


def set_batch_phase(*, phase: str, phase_label: str) -> None:
    path = _path()
    if not path:
        return
    with _lock:
        data = _read(path)
        data["phase"] = phase
        data["phase_label"] = phase_label
        if phase == "judging":
            data["judge_phase_label"] = phase_label
        if phase == "device_running":
            data["device_phase_label"] = phase_label
        data["updated_at"] = _now()
        _write(path, data)


def finish_batch() -> None:
    path = _path()
    if not path:
        return
    with _lock:
        data = _read(path)
        total = int(data.get("total") or 0)
        data["phase"] = "finished"
        data["phase_label"] = "全部完成"
        data["device_phase_label"] = f"设备：已完成 {total}/{total}"
        data["judge_phase_label"] = f"评判：已完成 {total}/{total}"
        data["current_index"] = total
        _recompute(data)
        data["done"] = total
        data["device_done"] = total
        data["judge_done"] = total
        data["updated_at"] = _now()
        _write(path, data)
