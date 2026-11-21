"""Judgment parse helpers (from TestApp case_eval / llm_eval)."""
from __future__ import annotations

import json
import re
from typing import Any


def parse_json_content(raw_result: str | None) -> Any | None:
    try:
        clean = (raw_result or "").strip()
        if clean.startswith("```json"):
            clean = clean[7:]
        elif clean.startswith("```"):
            clean = clean[3:]
        if clean.endswith("```"):
            clean = clean[:-3]
        return json.loads(clean.strip())
    except (json.JSONDecodeError, TypeError, ValueError):
        return None


def parse_evaluation_passed(eval_data: dict[str, Any] | None) -> bool | None:
    """Port of TestApp local_adb_runner.case_eval.parse_evaluation_passed."""
    if not eval_data:
        return None
    if eval_data.get("passed") is not None:
        return bool(eval_data.get("passed"))
    parsed = eval_data.get("result_parsed")
    if not isinstance(parsed, list):
        return None
    round_items = [
        x for x in parsed if isinstance(x, dict) and x.get("round") is not None and x.get("result")
    ]
    if round_items and any(x.get("result") != "success" for x in round_items):
        return False
    for item in parsed:
        if isinstance(item, dict) and item.get("final_result") is not None:
            return item.get("result") == "success"
    step_items = [
        x
        for x in parsed
        if isinstance(x, dict) and x.get("result") and x.get("final_result") is None
    ]
    if step_items:
        return all(x.get("result") == "success" for x in step_items)
    return None


def normalize_exceptions(raw: Any) -> list[str]:
    if raw is None:
        return []
    if isinstance(raw, str):
        lines = [ln.strip() for ln in raw.replace("\r", "").split("\n") if ln.strip()]
        return lines
    if isinstance(raw, list):
        return [str(x).strip() for x in raw if str(x).strip()]
    return [str(raw).strip()]


_DEST_PLACEHOLDER_RE = re.compile(
    r"你要去哪儿|请输入目的地|搜索目的地|请输入终点|搜索地点|输入目的地|去哪儿"
)
_DEST_FILLED_RE = re.compile(
    r"已(设置|填入|选定|输入|选择).{0,12}(目的地|终点)|"
    r"(目的地|终点)(已)?(为|是|显示|变成|改成)[^，。；\s]{1,20}|"
    r'[「『"].{0,20}钟楼.{0,20}[」』"]'
)
_NAV_REACHED_RE = re.compile(
    r"开始导航|导航界面|导航中|路线规划|地图上.{0,8}路线|"
    r"\d+\s*(?:分钟|公里)|预计到达|全程"
)


def reject_placeholder_success(parsed: Any, exceptions: Any) -> Any:
    """Port of TestApp _reject_placeholder_success (local rule, no model call)."""
    if not isinstance(parsed, list):
        return parsed
    if isinstance(exceptions, (list, tuple)):
        exc_blob = "\n".join(str(x) for x in exceptions)
    else:
        exc_blob = str(exceptions or "")
    wants_destination = bool(re.search(r"目的地|终点|钟楼", exc_blob))
    out = []
    flipped = False
    for item in parsed:
        if not isinstance(item, dict):
            out.append(item)
            continue
        item = dict(item)
        if str(item.get("result") or "").lower() != "success":
            out.append(item)
            continue
        parts = [
            str(item.get("expectation") or ""),
            str(item.get("reason") or ""),
            str(item.get("think") or ""),
        ]
        for ev in item.get("evidence") or []:
            if isinstance(ev, dict):
                parts.append(str(ev.get("reason") or ""))
                els = ev.get("elements") or []
                if isinstance(els, list):
                    parts.extend(str(x) for x in els)
        blob = "\n".join(parts)
        if not _DEST_PLACEHOLDER_RE.search(blob):
            out.append(item)
            continue
        if _NAV_REACHED_RE.search(blob) or _DEST_FILLED_RE.search(blob):
            out.append(item)
            continue
        if wants_destination or _DEST_PLACEHOLDER_RE.search(
            str(item.get("reason") or "") + str(item.get("think") or "")
        ):
            if not _DEST_FILLED_RE.search(blob):
                item["result"] = "failed"
                note = "【系统复核】仍为目的地占位输入（如「你要去哪儿」），未确认具体目的地已设置，改判失败。"
                prev = (item.get("reason") or "").strip()
                item["reason"] = f"{prev} {note}".strip() if prev else note
                item["placeholder_rejected"] = True
                flipped = True
        out.append(item)
    if flipped:
        for item in out:
            if isinstance(item, dict) and item.get("final_result") is not None:
                item["result"] = "failed"
                prev = (item.get("reason") or "").strip()
                note = "【系统复核】存在未完成的目的地设置，整体改判失败。"
                item["reason"] = f"{prev} {note}".strip() if prev else note
    return out


def normalize_gui_poll_evidence_refs(parsed: Any, names: list[str]) -> Any:
    """Port of TestApp _normalize_gui_poll_evidence_refs."""
    name_by_idx = {i: n for i, n in enumerate(names or [], 1)}
    name_set = set(names or [])
    lower_map = {n.lower(): n for n in (names or [])}

    def resolve_one(ref: Any) -> str:
        raw = str(ref or "").strip()
        if not raw:
            return raw
        if raw in name_set:
            return raw
        hit = lower_map.get(raw.lower())
        if hit:
            return hit
        m = re.match(r"^图\s*(\d+)$", raw) or re.match(r"^#?\s*(\d+)$", raw)
        if m:
            return name_by_idx.get(int(m.group(1)), raw)
        for n in names or []:
            if n.endswith(raw) or raw in n:
                m_poll = re.search(r"gui_poll_(\d+)", raw, re.I)
                m_name = re.search(r"gui_poll_(\d+)", n, re.I)
                if m_poll and m_name and m_poll.group(1) != m_name.group(1):
                    continue
                return n
        return raw

    out = []
    for item in parsed if isinstance(parsed, list) else []:
        if not isinstance(item, dict):
            out.append(item)
            continue
        item = dict(item)
        evidence = item.get("evidence")
        if isinstance(evidence, list) and evidence:
            new_ev = []
            for ev in evidence:
                if isinstance(ev, str):
                    new_ev.append(resolve_one(ev))
                    continue
                if isinstance(ev, dict):
                    ev = dict(ev)
                    for key in ("image", "img", "figure", "name"):
                        if key in ev and ev[key]:
                            ev[key] = resolve_one(ev[key])
                            break
                    new_ev.append(ev)
                    continue
                new_ev.append(ev)
            item["evidence"] = new_ev
        out.append(item)
    return out
