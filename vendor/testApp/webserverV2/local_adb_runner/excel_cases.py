"""Parse webserver-style Excel GUI cases (classic + WebTest headers)."""
from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Tuple

from openpyxl import load_workbook


@dataclass
class ChatTurn:
    text: str
    wait_sec: float
    clarify: str = ""


@dataclass
class GuiCase:
    case_id: str
    name: str
    description: str
    case_type: str
    package: str
    timeout_sec: float
    turns: List[ChatTurn] = field(default_factory=list)
    expected: str = ""
    json_path: str = ""


def _cell_str(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip()


def _parse_numbered_lines(cell_value: Any) -> List[str]:
    text = _cell_str(cell_value)
    if not text:
        return []
    lines: List[str] = []
    for raw in re.split(r"[\r\n]+", text):
        line = raw.strip()
        if not line:
            continue
        m = re.match(r"^\d+[\.\)、]\s*(.*)$", line)
        lines.append((m.group(1) if m else line).strip())
    return [x for x in lines if x]


def _parse_wait_seconds(cell_value: Any, default_sec: float) -> List[float]:
    waits: List[float] = []
    for line in _parse_numbered_lines(cell_value):
        try:
            waits.append(float(line))
        except ValueError:
            waits.append(default_sec)
    return waits


def _parse_optional_ms(cell_value: Any, default_ms: int) -> int:
    text = _cell_str(cell_value)
    if not text:
        return default_ms
    try:
        return int(float(text))
    except ValueError:
        return default_ms


def _header_map(headers: Sequence[Any]) -> Dict[str, int]:
    col: Dict[str, int] = {}
    for i, h in enumerate(headers):
        name = _cell_str(h)
        if not name:
            continue
        n = name.replace(" ", "")
        if "用例编号" in n or n.lower() in ("id", "caseid", "testid"):
            col["id"] = i
        elif "用例名称" in n or "用例标题" in n or "标题" == n:
            col["name"] = i
        elif "预置条件" in n:
            col["desc"] = i
        elif "描述" in n and "预期" not in n and "预置" not in n:
            col["desc"] = i
        elif "用例类型" in n or n == "类型":
            col["type"] = i
        elif re.fullmatch(r"测试步骤\d+", n):
            # 旧多列表头「测试步骤1/2…」才当语料列；新格式「S1场景」等是场景标签，不是步骤
            col.setdefault("steps_cols", [])
            col["steps_cols"].append(i)  # type: ignore[index]
        elif "测试步骤" in n:
            col["steps"] = i
        elif "语料" in n and "预期" not in n:
            col.setdefault("steps", i)
        elif "单步操作等待" in n or "等待时间" in n:
            col["wait"] = i
        elif "澄清" in n:
            col["clarify"] = i
        elif "预期结果" in n or n.lower() in ("exceptions", "expections"):
            col["expected"] = i
        elif "预期" in n and "expected" not in col:
            # 兼容旧表；勿覆盖已匹配的「预期结果」
            col["expected"] = i
        elif "timeout" in n.lower():
            col["timeout"] = i
        elif "wait_duration" in n.lower():
            col["wait_duration"] = i
        elif "包名" in n or "package" in n.lower():
            col["package"] = i
    return col


def parse_excel(
    xlsx_path: Path,
    *,
    default_package: str = "com.example.devicesdkdemo",
    default_wait_sec: float = 120.0,
    default_timeout_ms: int = 360000,
) -> List[GuiCase]:
    wb = load_workbook(str(xlsx_path), read_only=True, data_only=True)
    ws = wb.active
    rows = list(ws.iter_rows(values_only=True))
    wb.close()
    if not rows:
        return []

    col = _header_map(rows[0])
    if "id" not in col:
        raise ValueError(f"Excel 缺少用例编号列: {xlsx_path}")

    cases: List[GuiCase] = []
    for row in rows[1:]:
        if not row:
            continue
        case_id = _cell_str(row[col["id"]] if col["id"] < len(row) else None)
        if not case_id:
            continue

        name = _cell_str(row[col["name"]] if "name" in col and col["name"] < len(row) else None) or case_id
        desc = _cell_str(row[col["desc"]] if "desc" in col and col["desc"] < len(row) else None)
        case_type = _cell_str(row[col["type"]] if "type" in col and col["type"] < len(row) else None) or "GUI"
        package = (
            _cell_str(row[col["package"]] if "package" in col and col["package"] < len(row) else None)
            or default_package
        )
        expected = _cell_str(row[col["expected"]] if "expected" in col and col["expected"] < len(row) else None)

        timeout_ms = default_timeout_ms
        if "timeout" in col and col["timeout"] < len(row):
            timeout_ms = _parse_optional_ms(row[col["timeout"]], default_timeout_ms)

        wait_default = default_wait_sec
        if "wait_duration" in col and col["wait_duration"] < len(row):
            wait_default = _parse_optional_ms(row[col["wait_duration"]], int(default_wait_sec * 1000)) / 1000.0

        chat_texts: List[str] = []
        if "steps" in col and col["steps"] < len(row):
            chat_texts = _parse_numbered_lines(row[col["steps"]])
        steps_cols = col.get("steps_cols") or []
        if isinstance(steps_cols, list) and steps_cols:
            for idx in steps_cols:
                if idx < len(row):
                    t = _cell_str(row[idx])
                    if t:
                        chat_texts.append(t)

        if not chat_texts:
            continue

        waits = [wait_default] * len(chat_texts)
        if "wait" in col and col["wait"] < len(row):
            parsed = _parse_wait_seconds(row[col["wait"]], wait_default)
            for i, w in enumerate(parsed):
                if i < len(waits):
                    waits[i] = w

        clarifies = [""] * len(chat_texts)
        if "clarify" in col and col["clarify"] < len(row):
            cmap = {}
            for i, line in enumerate(_parse_numbered_lines(row[col["clarify"]]), 1):
                cmap[i] = line
            for i in range(len(chat_texts)):
                clarifies[i] = cmap.get(i + 1, "")

        turns = [
            ChatTurn(text=chat_texts[i], wait_sec=float(waits[i]), clarify=clarifies[i])
            for i in range(len(chat_texts))
        ]
        cases.append(
            GuiCase(
                case_id=case_id,
                name=name,
                description=desc,
                case_type=case_type,
                package=package,
                timeout_sec=timeout_ms / 1000.0,
                turns=turns,
                expected=expected,
            )
        )
    return cases


def resolve_dataset_xlsx(datasets_dir: Path, dataset: str) -> Path:
    """dataset 可为目录名，或直接指向 .xlsx。"""
    p = Path(dataset)
    if p.is_file() and p.suffix.lower() in (".xlsx", ".xlsm"):
        return p.resolve()
    folder = datasets_dir / dataset
    if folder.is_dir():
        preferred = folder / "dataset.xlsx"
        if preferred.is_file():
            return preferred.resolve()
        found = sorted(folder.glob("*.xlsx")) + sorted(folder.glob("*.xlsm"))
        if found:
            return found[0].resolve()
    raise FileNotFoundError(f"找不到数据集 Excel: dataset={dataset!r} under {datasets_dir}")
