#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

import openpyxl

from .config import DATA, E2E_XLSX_NAME, ES_GLOB


def e2e_path() -> Path:
    p = DATA / E2E_XLSX_NAME
    if not p.exists():
        raise FileNotFoundError(p)
    return p


def es_path() -> Path:
    files = list(DATA.glob(ES_GLOB))
    if not files:
        raise FileNotFoundError(f"no {ES_GLOB} under {DATA}")
    return files[0]


def load_filename_to_fid(path: Path | None = None) -> dict[str, str]:
    path = path or es_path()
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    mapping: dict[str, str] = {}
    for sheet in wb.sheetnames:
        ws = wb[sheet]
        header = list(next(ws.iter_rows(min_row=1, max_row=1, values_only=True)))
        try:
            i_fid = header.index("fid")
            i_name = header.index("fileName")
        except ValueError:
            continue
        for row in ws.iter_rows(min_row=2, values_only=True):
            if not row:
                continue
            name, fid = row[i_name], row[i_fid]
            if not name or not fid:
                continue
            name_s, fid_s = str(name).strip(), str(fid).strip()
            if name_s not in mapping:
                mapping[name_s] = fid_s
    wb.close()
    return mapping


def count_fids(val) -> int:
    if val is None:
        return 0
    s = str(val).strip()
    if not s:
        return 0
    return sum(1 for p in s.split(",") if p.strip())


def read_cases(
    path: Path | None = None,
    offset: int = 0,
    limit: int = 50,
    q: str = "",
    sort_by: str = "",
    sort_dir: str = "asc",
    count_min: int | None = None,
    count_max: int | None = None,
    status: str = "",
    ids_only: bool = False,
) -> dict:
    path = path or e2e_path()
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    ws = wb.active
    headers = [c.value for c in next(ws.iter_rows(min_row=1, max_row=1))]
    rows_all: list[dict] = []
    for i, row in enumerate(ws.iter_rows(min_row=2, values_only=True), start=2):
        item = {"_row": i}
        for h, v in zip(headers, row):
            if h:
                item[str(h)] = v
        if q:
            blob = " ".join(str(item.get(k) or "") for k in ("用例编号", "原始输入", "UserId"))
            if q.lower() not in blob.lower():
                continue
        if count_min is not None or count_max is not None:
            raw = item.get("预期图片数量")
            try:
                n = int(float(raw)) if raw is not None and str(raw).strip() != "" else None
            except (TypeError, ValueError):
                n = None
            if n is None:
                continue
            if count_min is not None and n < count_min:
                continue
            if count_max is not None and n > count_max:
                continue
        if status:
            wanted = [s.strip() for s in str(status).split(",") if s.strip()]
            if wanted:
                cur = str(item.get("执行状态") or "").strip()
                if not any(
                    (cur in ("", "未跑")) if w == "未跑" else (cur == w) for w in wanted
                ):
                    continue
        rows_all.append(item)
    wb.close()

    sort_by = (sort_by or "").strip()
    if sort_by and sort_by in (headers or []):
        reverse = (sort_dir or "asc").lower() == "desc"

        def sort_key(item: dict):
            v = item.get(sort_by)
            if v is None or v == "":
                return (1, 0)
            try:
                return (0, float(v))
            except (TypeError, ValueError):
                return (0, str(v))

        rows_all.sort(key=sort_key, reverse=reverse)

    total = len(rows_all)
    ids = [
        str(item.get("用例编号") or "").strip()
        for item in rows_all
        if str(item.get("用例编号") or "").strip()
    ]
    page = [] if ids_only else rows_all[offset : offset + limit]
    return {
        "headers": [h for h in headers if h],
        "total": total,
        "offset": offset,
        "limit": limit,
        "rows": page,
        "ids": ids if ids_only else [],
        "path": str(path),
        "sort_by": sort_by,
        "sort_dir": sort_dir or "asc",
        "count_min": count_min,
        "count_max": count_max,
        "status": status,
    }


def names_to_actual_fid(names: list[str], mapping: dict[str, str]) -> tuple[str, list[str]]:
    fids: list[str] = []
    missing: list[str] = []
    for name in names:
        fid = mapping.get(name)
        if not fid:
            missing.append(name)
        else:
            fids.append(fid)
    return ",".join(fids), missing


def ensure_header_col(ws, title: str) -> int:
    """Return 1-based column index for header; append if missing."""
    headers = [c.value for c in ws[1]]
    if title in headers:
        return headers.index(title) + 1
    last = 0
    for i, h in enumerate(headers, 1):
        if h is not None and str(h).strip() != "":
            last = i
    col = last + 1
    ws.cell(1, col).value = title
    return col


def ensure_header_before(ws, title: str, before: str) -> int:
    """Ensure title exists immediately before `before` column; insert if needed."""
    headers = [c.value for c in ws[1]]
    if title in headers:
        return headers.index(title) + 1
    if before in headers:
        col = headers.index(before) + 1  # 1-based insert position
        ws.insert_cols(col)
        ws.cell(1, col).value = title
        return col
    return ensure_header_col(ws, title)


STATUS_DONE = "完成"
STATUS_PARTIAL = "中断"
STATUS_FAILED = "失败"
STATUS_EMPTY = "无结果"
STATUS_CHOICES = ("", STATUS_DONE, STATUS_PARTIAL, STATUS_FAILED, STATUS_EMPTY)


def case_status_of(item: dict) -> str:
    return str(item.get("执行状态") or "").strip()


def is_case_done(item: dict) -> bool:
    """Only explicit 执行状态=完成 counts; 中断/失败 will be re-run."""
    return case_status_of(item) == STATUS_DONE


def set_case_status(path: Path, case_id: str, status: str) -> dict:
    """
    Manually set 执行状态 for a case id.
    status="" clears to 未跑. Returns {ok, row, status, path}.
    """
    status = (status or "").strip()
    if status not in STATUS_CHOICES:
        return {"ok": False, "error": f"invalid status: {status!r}"}
    wb = openpyxl.load_workbook(path)
    ws = wb.active
    ensure_header_before(ws, "执行状态", "搜索时延(ms)")
    headers = [c.value for c in ws[1]]
    if "执行状态" not in headers:
        ensure_header_col(ws, "执行状态")
        headers = [c.value for c in ws[1]]
    if "用例编号" not in headers:
        wb.close()
        return {"ok": False, "error": "missing 用例编号 column"}
    i_id = headers.index("用例编号")
    i_st = headers.index("执行状态")
    excel_row = None
    for r in range(2, ws.max_row + 1):
        cid = str(ws.cell(r, i_id + 1).value or "").strip()
        if cid == case_id:
            excel_row = r
            break
    if excel_row is None:
        wb.close()
        return {"ok": False, "error": f"case not found: {case_id}"}
    ws.cell(excel_row, i_st + 1).value = status if status else None
    wb.save(path)
    wb.close()
    return {"ok": True, "row": excel_row, "status": status, "path": str(path)}


def write_actual_results(
    path: Path,
    excel_row: int,
    actual_fid: str,
    actual_names: str,
    actual_count: int | None = None,
    search_latency_ms: int | None = None,
    missing_count: int | None = None,
    status: str | None = None,
    overwrite_done: bool = False,
) -> None:
    """Write 执行状态 / 搜索时延(ms) / 实际搜索数量 / 实际fid / 实际图片名称 / 缺映射."""
    wb = openpyxl.load_workbook(path)
    ws = wb.active
    # order: … | 执行状态 | 搜索时延(ms) | 实际搜索数量 | 实际fid | 实际图片名称
    ensure_header_before(ws, "执行状态", "搜索时延(ms)")
    headers = [c.value for c in ws[1]]
    if "搜索时延(ms)" not in headers:
        ensure_header_before(ws, "执行状态", "实际搜索数量")
    ensure_header_before(ws, "搜索时延(ms)", "实际搜索数量")
    headers = [c.value for c in ws[1]]
    if "实际搜索数量" not in headers:
        ensure_header_before(ws, "搜索时延(ms)", "实际fid")
    ensure_header_before(ws, "实际搜索数量", "实际fid")
    ensure_header_col(ws, "实际fid")
    ensure_header_col(ws, "实际图片名称")
    ensure_header_col(ws, "缺映射")
    headers = [c.value for c in ws[1]]

    def col(title: str) -> int | None:
        return headers.index(title) + 1 if title in headers else None

    col_status = col("执行状态")
    col_lat = col("搜索时延(ms)")
    col_count = col("实际搜索数量")
    col_fid = col("实际fid")
    col_names = col("实际图片名称")
    col_missing = col("缺映射")

    # Protect finished cases from being clobbered by 中断/失败 unless forced
    if (
        not overwrite_done
        and col_status
        and status is not None
        and status != STATUS_DONE
    ):
        prev = str(ws.cell(excel_row, col_status).value or "").strip()
        if prev == STATUS_DONE:
            wb.close()
            return

    if actual_count is None:
        actual_count = count_fids(actual_names) if actual_names else count_fids(actual_fid)
    if status is not None and col_status:
        ws.cell(excel_row, col_status).value = status or None
    if search_latency_ms is not None and col_lat:
        ws.cell(excel_row, col_lat).value = int(search_latency_ms)
    if col_count:
        ws.cell(excel_row, col_count).value = int(actual_count)
    if col_fid:
        ws.cell(excel_row, col_fid).value = actual_fid
    if col_names:
        ws.cell(excel_row, col_names).value = actual_names
    if missing_count is not None and col_missing:
        ws.cell(excel_row, col_missing).value = int(missing_count)
    if "预期图片数量" in headers and "预期fid" in headers:
        c_cnt = headers.index("预期图片数量") + 1
        c_fid = headers.index("预期fid") + 1
        ws.cell(excel_row, c_cnt).value = count_fids(ws.cell(excel_row, c_fid).value)
    wb.save(path)
    wb.close()


def iter_case_rows(
    path: Path,
    case_ids: list[str] | None = None,
    limit: int | None = None,
    skip_done: bool = False,
):
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    ws = wb.active
    headers = [c.value for c in next(ws.iter_rows(min_row=1, max_row=1))]
    n = 0
    for i, row in enumerate(ws.iter_rows(min_row=2, values_only=True), start=2):
        item = {"_row": i}
        for h, v in zip(headers, row):
            if h:
                item[str(h)] = v
        cid = str(item.get("用例编号") or "")
        if case_ids and cid not in case_ids:
            continue
        # skip_done applies even when case_ids are given (unless caller sets False)
        if skip_done and is_case_done(item):
            continue
        yield item
        n += 1
        if limit is not None and n >= limit:
            break
    wb.close()
