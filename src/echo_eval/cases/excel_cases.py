"""Parse TestApp-style / v0.11 Excel/CSV cases (语料 + 预置条件 + 预期结果)."""
from __future__ import annotations

import csv
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Sequence


@dataclass
class CaseStep:
    text: str
    wait_sec: float = 3.0
    # 1-based round index within the same 用例 ID (v0.29 multi-row cases)
    round_index: int = 1


@dataclass
class CaseRound:
    """One spreadsheet row under a shared 用例 ID (方案 A: merged into one EvalCase)."""

    round_index: int
    source_row: int
    utterances: list[str] = field(default_factory=list)
    expected: str = ""
    precondition_raw: str = ""


@dataclass
class EvalCase:
    case_id: str
    name: str
    description: str = ""
    case_type: str = "GUI"
    steps: list[CaseStep] = field(default_factory=list)
    expected: str = ""
    timeout_sec: float = 120.0
    # v0.11 fields
    object_name: str = ""
    scene: str = ""
    priority: str = ""
    exec_notes: str = ""
    precondition_steps: list[str] = field(default_factory=list)
    # v0.29: ordered rounds after merging same-ID rows (top → bottom)
    rounds: list[CaseRound] = field(default_factory=list)

    def exceptions(self) -> list[str]:
        lines = _parse_numbered_lines(self.expected)
        if lines:
            return lines
        if (self.expected or "").strip():
            return [self.expected.strip()]
        return []

    @property
    def round_count(self) -> int:
        return max(1, len(self.rounds) or (1 if self.steps else 0))


def _cell_str(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip()


def _parse_numbered_lines(cell_value: Any) -> list[str]:
    text = _cell_str(cell_value)
    if not text:
        return []
    lines: list[str] = []
    for raw in re.split(r"[\r\n]+", text):
        line = raw.strip()
        if not line:
            continue
        m = re.match(r"^\d+[\.\)、]\s*(.*)$", line)
        lines.append((m.group(1) if m else line).strip())
    return [x for x in lines if x]


def _parse_utterance_lines(cell_value: Any) -> list[str]:
    """Parse 文本语料 / 测试步骤; split numbered lines and '→' multi-utterances."""
    base = _parse_numbered_lines(cell_value)
    if not base:
        return []
    out: list[str] = []
    for line in base:
        parts = [p.strip() for p in re.split(r"\s*→\s*", line) if p.strip()]
        if parts:
            out.extend(parts)
        else:
            out.append(line)
    return out


def _parse_precondition_steps(cell_value: Any) -> list[str]:
    """预置条件：按换行拆分；也容忍 ' | ' / '→' 分隔。"""
    text = _cell_str(cell_value)
    if not text:
        return []
    if "\n" in text or "\r" in text:
        return _parse_numbered_lines(text)
    # single-line separators used in some exports
    if "|" in text:
        return [p.strip() for p in text.split("|") if p.strip()]
    if "→" in text:
        return [p.strip() for p in re.split(r"\s*→\s*", text) if p.strip()]
    return [text]


def _parse_wait_seconds(cell_value: Any, default_sec: float) -> list[float]:
    waits: list[float] = []
    for line in _parse_numbered_lines(cell_value):
        try:
            waits.append(float(line))
        except ValueError:
            waits.append(default_sec)
    return waits


def _header_map(headers: Sequence[Any]) -> dict[str, Any]:
    col: dict[str, Any] = {}
    for i, h in enumerate(headers):
        name = _cell_str(h)
        if not name:
            continue
        n = name.replace(" ", "")
        n_lower = n.lower()
        if (
            "用例编号" in n
            or n in ("用例ID", "用例Id")
            or n_lower in ("id", "caseid", "testid", "用例id")
        ):
            col["id"] = i
        elif "用例名称" in n or "用例标题" in n or n == "标题":
            col["name"] = i
        elif n == "对象":
            col["object"] = i
        elif n == "场景":
            col["scene"] = i
        elif "优先级" in n:
            col["priority"] = i
        elif "预置条件" in n:
            col["precondition"] = i
        elif "执行说明" in n:
            col["exec_notes"] = i
        elif "描述" in n and "预期" not in n and "预置" not in n:
            col["desc"] = i
        elif "用例类型" in n or n == "类型":
            col["type"] = i
        elif re.fullmatch(r"测试步骤\d+", n):
            col.setdefault("steps_cols", [])
            col["steps_cols"].append(i)
        elif "测试步骤" in n:
            col["steps"] = i
        elif "文本语料" in n or ("语料" in n and "预期" not in n):
            # prefer 文本语料 as utterance source
            if "steps" not in col or "文本语料" in n:
                col["steps"] = i
        elif "单步操作等待" in n or "等待时间" in n:
            col["wait"] = i
        elif "预期结果" in n or n_lower in ("exceptions", "expections"):
            col["expected"] = i
        elif "预期" in n and "expected" not in col:
            col["expected"] = i
        elif "timeout" in n_lower:
            col["timeout"] = i
        elif n in ("轮次", "轮次号", "round") or n_lower == "round":
            col["round"] = i
        elif "用例分类" in n or n == "分类":
            col.setdefault("category", i)
    return col


def _format_expected_chunks(chunks: list[str]) -> str:
    """Number flattened expectation lines for VL judge."""
    out: list[str] = []
    for i, text in enumerate(chunks, 1):
        t = (text or "").strip()
        if t:
            out.append(f"{i}. {t}")
    return "\n".join(out)


def _expected_chunks_from_cell(expected: str) -> list[str]:
    text = (expected or "").strip()
    if not text:
        return []
    numbered = _parse_numbered_lines(text)
    return numbered if numbered else [text]


def _rows_from_csv(path: Path) -> list[list[Any]]:
    for enc in ("utf-8-sig", "utf-8", "gbk"):
        try:
            with path.open("r", encoding=enc, newline="") as f:
                reader = csv.reader(f)
                return [list(row) for row in reader]
        except UnicodeDecodeError:
            continue
    raise ValueError(f"无法解码 CSV: {path}")


def _rows_from_xlsx(path: Path) -> list[list[Any]]:
    try:
        from openpyxl import load_workbook  # type: ignore
    except ImportError as e:
        raise ImportError(
            "读取 .xlsx 需要 openpyxl。可执行:\n"
            "  python -m pip install openpyxl -i https://pypi.tuna.tsinghua.edu.cn/simple\n"
            "或将表格另存为 UTF-8 CSV 后用 --excel/--csv 指向 .csv"
        ) from e
    wb = load_workbook(str(path), read_only=True, data_only=True)
    ws = wb.active
    rows = [list(r) for r in ws.iter_rows(values_only=True)]
    wb.close()
    return rows


def _col_str(row: list[Any], col: dict[str, Any], key: str) -> str:
    if key not in col:
        return ""
    i = col[key]
    if i >= len(row):
        return ""
    return _cell_str(row[i])


def _parse_rows(
    rows: list[list[Any]],
    *,
    source: str,
    default_wait_sec: float = 3.0,
    default_timeout_sec: float = 120.0,
) -> list[EvalCase]:
    """
    Parse spreadsheet rows into EvalCase list.

    v0.29: multiple rows may share the same 用例 ID. They are merged in file
    order into one EvalCase (方案 A): one precondition, ordered utterances,
    combined expectations; runner Homes only after the whole case.
    """
    if not rows:
        return []
    col = _header_map(rows[0])
    if "id" not in col:
        raise ValueError(f"缺少「用例编号」或「用例 ID」列: {source}")

    # case_id -> draft accumulator (first-seen order)
    order: list[str] = []
    drafts: dict[str, dict[str, Any]] = {}
    # Detect non-contiguous reappearance of an ID (still merge in file order).
    last_id = ""
    seen_closed: set[str] = set()

    for row_idx, row in enumerate(rows[1:], start=2):
        if not row:
            continue
        # pad short rows
        row = list(row) + [None] * max(0, (max(col.get("id", 0), 0) + 1) - len(row))
        case_id = _cell_str(row[col["id"]] if col["id"] < len(row) else None)
        if not case_id:
            continue

        chat_texts: list[str] = []
        if "steps" in col and col["steps"] < len(row):
            chat_texts = _parse_utterance_lines(row[col["steps"]])
        steps_cols = col.get("steps_cols") or []
        if isinstance(steps_cols, list):
            for idx in steps_cols:
                if idx < len(row):
                    t = _cell_str(row[idx])
                    if t:
                        chat_texts.extend(_parse_utterance_lines(t))
        if not chat_texts:
            # allow empty steps? skip row without utterances
            continue

        name = _col_str(row, col, "name") or case_id
        desc = _col_str(row, col, "desc")
        exec_notes = _col_str(row, col, "exec_notes")
        if not desc and exec_notes:
            desc = exec_notes
        case_type = _col_str(row, col, "type") or _col_str(row, col, "category") or "GUI"
        expected = _col_str(row, col, "expected")
        object_name = _col_str(row, col, "object")
        scene = _col_str(row, col, "scene")
        priority = _col_str(row, col, "priority")
        pre_raw = (
            _col_str(row, col, "precondition")
            if "precondition" in col
            else ""
        )
        precondition_steps = (
            _parse_precondition_steps(row[col["precondition"]])
            if "precondition" in col and col["precondition"] < len(row)
            else []
        )

        timeout_sec = default_timeout_sec
        if "timeout" in col and col["timeout"] < len(row):
            raw = _cell_str(row[col["timeout"]])
            if raw:
                try:
                    val = float(raw)
                    timeout_sec = val / 1000.0 if val > 1000 else val
                except ValueError:
                    pass

        waits = [default_wait_sec] * len(chat_texts)
        if "wait" in col and col["wait"] < len(row):
            parsed = _parse_wait_seconds(row[col["wait"]], default_wait_sec)
            for i, w in enumerate(parsed):
                if i < len(waits):
                    waits[i] = w

        sheet_round = 0
        if "round" in col and col["round"] < len(row):
            raw_r = _cell_str(row[col["round"]])
            if raw_r:
                try:
                    sheet_round = int(float(raw_r))
                except ValueError:
                    sheet_round = 0

        if case_id in seen_closed and case_id != last_id:
            print(
                f"[echo-eval][warn] 用例 ID {case_id} 在表中非连续出现，仍按文件顺序合并为一组 "
                f"(source={source})",
                flush=True,
            )
        if last_id and last_id != case_id and last_id in drafts:
            seen_closed.add(last_id)
        last_id = case_id

        if case_id not in drafts:
            order.append(case_id)
            drafts[case_id] = {
                "case_id": case_id,
                "name": name,
                "description": desc,
                "case_type": case_type,
                "timeout_sec": timeout_sec,
                "object_name": object_name,
                "scene": scene,
                "priority": priority,
                "exec_notes": exec_notes,
                "precondition_steps": list(precondition_steps),
                "rounds": [],
                "steps": [],
                "expected_chunks": [],
            }
        draft = drafts[case_id]
        # Prefer first non-empty metadata / precondition
        if not draft["precondition_steps"] and precondition_steps:
            draft["precondition_steps"] = list(precondition_steps)
        if name and draft["name"] == case_id:
            draft["name"] = name
        if object_name and not draft["object_name"]:
            draft["object_name"] = object_name
        if scene and not draft["scene"]:
            draft["scene"] = scene
        if priority and not draft["priority"]:
            draft["priority"] = priority
        if exec_notes and not draft["exec_notes"]:
            draft["exec_notes"] = exec_notes
        if desc and not draft["description"]:
            draft["description"] = desc

        round_index = sheet_round if sheet_round > 0 else (len(draft["rounds"]) + 1)
        rnd = CaseRound(
            round_index=round_index,
            source_row=row_idx,
            utterances=list(chat_texts),
            expected=expected,
            precondition_raw=pre_raw,
        )
        draft["rounds"].append(rnd)
        for i, text in enumerate(chat_texts):
            draft["steps"].append(
                CaseStep(
                    text=text,
                    wait_sec=float(waits[i] if i < len(waits) else default_wait_sec),
                    round_index=round_index,
                )
            )
        draft["expected_chunks"].extend(_expected_chunks_from_cell(expected))

    cases: list[EvalCase] = []
    for case_id in order:
        d = drafts[case_id]
        cases.append(
            EvalCase(
                case_id=d["case_id"],
                name=d["name"],
                description=d["description"],
                case_type=d["case_type"],
                steps=list(d["steps"]),
                expected=_format_expected_chunks(d["expected_chunks"]),
                timeout_sec=float(d["timeout_sec"]),
                object_name=d["object_name"],
                scene=d["scene"],
                priority=d["priority"],
                exec_notes=d["exec_notes"],
                precondition_steps=list(d["precondition_steps"]),
                rounds=list(d["rounds"]),
            )
        )
    return cases


def parse_cases_file(
    path: Path | str,
    *,
    default_wait_sec: float = 3.0,
    default_timeout_sec: float = 120.0,
) -> list[EvalCase]:
    path = Path(path)
    if not path.is_file():
        raise FileNotFoundError(f"用例文件不存在: {path}")
    suffix = path.suffix.lower()
    if suffix in (".csv", ".txt"):
        rows = _rows_from_csv(path)
    elif suffix in (".xlsx", ".xlsm"):
        rows = _rows_from_xlsx(path)
    else:
        raise ValueError(f"不支持的用例文件类型: {suffix}（请用 .xlsx 或 .csv）")
    return _parse_rows(
        rows,
        source=str(path),
        default_wait_sec=default_wait_sec,
        default_timeout_sec=default_timeout_sec,
    )


def load_cases(
    path: Path | str,
    *,
    case_id: str | None = None,
    default_wait_sec: float = 3.0,
) -> list[EvalCase]:
    cases = parse_cases_file(path, default_wait_sec=default_wait_sec)
    if case_id:
        wanted = case_id.strip()
        cases = [c for c in cases if c.case_id == wanted]
        if not cases:
            raise ValueError(f"未找到用例编号: {wanted}")
    return cases
