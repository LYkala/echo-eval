"""Webserver-aligned Excel → JSON conversion (DeviceSDK Demo path).

Mirrors webserverV2 event-driven wait semantics:
- DeviceSDK / QA: wait duration is a safety ceiling; AISF reply ends the step early.
- Excel「单步操作等待时间」values are seconds → milliseconds (same as webserver).
- Form timeout_ms / wait_duration are the import defaults (webserver UI).
"""
from __future__ import annotations

import json
import re
from io import BytesIO
from pathlib import Path
from typing import Any, Dict, List, Optional

from openpyxl import load_workbook

EXCEL_DEFAULT_CHAT_WAIT_MS = 1500
EXCEL_DEFAULT_TIMEOUT_MS = 90000
DEVICESDK_PKG = "com.example.devicesdkdemo"
VOICE_ASSISTANT_PKG = "com.huawei.aisftvassistant"
VOICE_INJECT_ACTION = "com.huawei.aisftvassistant.action.INJECT_UTTERANCE"
VOICE_INJECT_COMPONENT = (
    "com.huawei.aisftvassistant/.inject.TestUtteranceReceiver"
)
DEVICESDKDEMO_WAIT_NEW_SESSION_MS = 2000

EXCEL_PACKAGE_PRESETS = {
    DEVICESDK_PKG: {
        "label": "Device SDK Demo",
        "input_id": f"{DEVICESDK_PKG}:id/et_input",
        "send_id": f"{DEVICESDK_PKG}:id/btn_send",
        "voice_toggle_id": "",
        "enter_test_id": f"{DEVICESDK_PKG}:id/btn_confirm",
        "login_button_id": f"{DEVICESDK_PKG}:id/btn_login",
        "qa_button_id": f"{DEVICESDK_PKG}:id/btn_qa",
        "session_manage_id": f"{DEVICESDK_PKG}:id/btn_session_manage",
    },
    VOICE_ASSISTANT_PKG: {
        "label": "AISF 语音助手",
        "input_id": "",
        "send_id": "",
        "voice_toggle_id": "",
    },
}


def _build_devicesdkdemo_pre_steps(package_name: str) -> List[Dict[str, Any]]:
    """与 webserver _build_devicesdkdemo_pre_steps 一致：聊天页新增会话。"""
    session_btn_id = f"{package_name}:id/btn_session_manage"
    new_session_btn_id = f"{package_name}:id/btn_session_new"
    return [
        {
            "stepId": "click-session-manage",
            "description": "聊天页点击「会话 ▼」打开会话管理",
            "action": "click",
            "target": {"strategy": "id", "value": session_btn_id},
        },
        {
            "stepId": "click-new-session",
            "description": "会话管理弹窗点击「新增会话」",
            "action": "click",
            "target": {"strategy": "id", "value": new_session_btn_id},
        },
        {
            "stepId": "wait-new-session",
            "description": "等待新会话就绪",
            "action": "wait",
            "params": {"duration": DEVICESDKDEMO_WAIT_NEW_SESSION_MS},
        },
    ]


def _devicesdkdemo_pre_step_extra_timeout_ms() -> int:
    pre_steps = _build_devicesdkdemo_pre_steps(DEVICESDK_PKG)
    wait_ms = sum(
        int((step.get("params") or {}).get("duration") or 0)
        for step in pre_steps
        if step.get("action") == "wait"
    )
    action_overhead_ms = 0
    for step in pre_steps:
        action = step.get("action")
        if action == "scrollUntilClick":
            params = step.get("params") or {}
            max_scrolls = int(params.get("maxScrolls") or 6)
            scroll_wait_ms = int(params.get("duration") or 600)
            action_overhead_ms += max_scrolls * (scroll_wait_ms + 700) + 2000
        elif action in ("scroll", "click"):
            action_overhead_ms += 2000
    return wait_ms + action_overhead_ms + 5000


def _cell_str(value: Any) -> str:
    if value is None:
        return ""
    text = str(value).strip()
    return "" if text.lower() == "none" else text


def _parse_numbered_lines(cell_value: Any) -> List[str]:
    text = _cell_str(cell_value)
    if not text:
        return []
    lines: List[str] = []
    for raw in re.split(r"[\r\n]+", text):
        line = raw.strip()
        if not line:
            continue
        m = re.match(r"^\d+\s*[.、):：]\s*(.*)$", line)
        lines.append((m.group(1) if m else line).strip())
    return [x for x in lines if x]


def _parse_optional_ms(cell_value: Any, default_ms: int) -> int:
    text = _cell_str(cell_value)
    if not text:
        return default_ms
    try:
        value = int(float(text))
        return value if value > 0 else default_ms
    except ValueError:
        return default_ms


def _parse_wait_seconds_lines(cell_value: Any, default_wait_ms: int) -> List[int]:
    """Excel step wait values are seconds → ms (webserver _excel_parse_wait_seconds_lines)."""
    waits: List[int] = []
    for line in _parse_numbered_lines(cell_value):
        try:
            waits.append(int(float(line) * 1000))
        except ValueError:
            waits.append(int(default_wait_ms))
    return waits


def _sanitize_filename(name: str) -> str:
    return re.sub(r"[^\w\-.]+", "_", name)[:80] or "case"


def _normalize_case_type(value: Any, package_name: Optional[str] = None) -> str:
    text = _cell_str(value).upper()
    if text in ("GUI", "QA"):
        return text
    if package_name == DEVICESDK_PKG:
        return "QA"
    return "GUI"


def _ensure_timeout_covers_wait_steps(testcase: Dict[str, Any], headroom_ms: int = 120000) -> Dict[str, Any]:
    total = 0
    for step in testcase.get("steps") or []:
        if step.get("action") != "wait":
            continue
        try:
            total += int((step.get("params") or {}).get("duration") or 0)
        except (TypeError, ValueError):
            pass
    base = int(testcase.get("timeout") or EXCEL_DEFAULT_TIMEOUT_MS)
    need = total + int(headroom_ms)
    if need > base:
        testcase["timeout"] = need
    return testcase


def _create_voice_utterance_steps(
    step_index: int,
    text: str,
    wait_ms: int,
    *,
    clarify_message: Optional[str] = None,
) -> List[Dict[str, Any]]:
    """文字语料 → 导出广播，不再点 Demo 输入框。等待用超时兜底，不看 Demo 回复事件。"""

    def _inject(step_id: str, description: str, utterance: str) -> Dict[str, Any]:
        return {
            "stepId": step_id,
            "description": description,
            "action": "broadcast",
            "params": {
                "action": VOICE_INJECT_ACTION,
                "component": VOICE_INJECT_COMPONENT,
                "text": utterance,
            },
        }

    preview = f"{text[:30]}..." if len(text) > 30 else text
    steps: List[Dict[str, Any]] = [
        _inject(f"inject-utterance-{step_index}", f"注入语料 {step_index}: {preview}", text),
        {
            "stepId": f"wait-utterance-{step_index}",
            "description": f"等待语音助手执行 {step_index}",
            "action": "wait",
            "params": {"duration": int(wait_ms)},
        },
    ]
    clarify = (clarify_message or "").strip()
    if clarify:
        steps.append(
            _inject(f"inject-clarify-{step_index}", f"注入澄清 {step_index}", clarify)
        )
        steps.append(
            {
                "stepId": f"wait-clarify-{step_index}",
                "description": f"等待澄清执行 {step_index}",
                "action": "wait",
                "params": {"duration": int(wait_ms)},
            }
        )
    return steps


def _create_chat_step(
    step_index: int,
    text: str,
    input_id: str,
    send_id: str,
    wait_ms: int,
    *,
    event_driven: bool,
    clarify_message: Optional[str] = None,
) -> List[Dict[str, Any]]:
    # DeviceSDK: wait-response ends on AISF reply; duration is safety only.
    if event_driven:
        safety_ms = max(int(wait_ms or 0), 5 * 60 * 1000)
        wait_desc = f"等待AISF回复(事件驱动) {step_index}"
        wait_duration = safety_ms
    else:
        wait_desc = f"等待AI回复 {step_index}"
        wait_duration = int(wait_ms)
    wait_params: Dict[str, Any] = {"duration": wait_duration}
    clarify = (clarify_message or "").strip()
    if clarify:
        wait_params["clarifyMessage"] = clarify
    return [
        {
            "stepId": f"click-input-{step_index}",
            "description": f"点击输入框 {step_index}",
            "action": "click",
            "target": {"strategy": "id", "value": input_id},
        },
        {
            "stepId": f"input-message-{step_index}",
            "description": (
                f"输入消息: {text[:30]}..." if len(text) > 30 else f"输入消息: {text}"
            ),
            "action": "input",
            "target": {"strategy": "id", "value": input_id},
            "params": {"text": text, "clearFirst": True},
        },
        {
            "stepId": f"click-send-{step_index}",
            "description": f"点击发送按钮 {step_index}",
            "action": "click",
            "target": {"strategy": "id", "value": send_id},
        },
        {
            "stepId": f"wait-response-{step_index}",
            "description": wait_desc,
            "action": "wait",
            "params": wait_params,
        },
    ]


def _create_test_case(
    *,
    test_id: str,
    test_name: str,
    description: str,
    package_name: str,
    input_id: str,
    send_id: str,
    wait_ms: int,
    timeout_ms: int,
    chat_steps: List[Dict[str, Any]],
    case_type: Optional[str] = None,
    exceptions: Optional[List[str]] = None,
) -> Dict[str, Any]:
    case_type = _normalize_case_type(case_type, package_name)
    test_case: Dict[str, Any] = {
        "version": "1.0.0",
        "testId": test_id,
        "testName": test_name,
        "description": description,
        "caseType": case_type,
        "targetApp": {"packageName": package_name},
        "timeout": int(timeout_ms),
        "steps": [
            {
                "stepId": "wait-home",
                "description": "用例切换缓冲",
                "action": "wait",
                "params": {"duration": 500},
            },
            {"stepId": "launch", "description": "启动目标应用", "action": "launchApp"},
            {
                "stepId": "wait-home",
                "description": "等待首页加载",
                "action": "wait",
                "params": {"duration": 1000},
            },
        ],
    }
    if exceptions:
        test_case["exceptions"] = [str(x).strip() for x in exceptions if str(x).strip()]
    if package_name == DEVICESDK_PKG:
        test_case["steps"].extend(_build_devicesdkdemo_pre_steps(package_name))
        test_case["timeout"] = int(timeout_ms) + _devicesdkdemo_pre_step_extra_timeout_ms()

    event_driven = case_type == "QA" or package_name == DEVICESDK_PKG
    for i, step in enumerate(chat_steps, 1):
        step_wait = int(step.get("wait_ms") or wait_ms)
        if package_name == VOICE_ASSISTANT_PKG:
            test_case["steps"].extend(
                _create_voice_utterance_steps(
                    i,
                    step.get("text", ""),
                    max(step_wait, int(timeout_ms)),
                    clarify_message=step.get("clarify"),
                )
            )
            continue
        test_case["steps"].extend(
            _create_chat_step(
                i,
                step.get("text", ""),
                input_id,
                send_id,
                step_wait,
                event_driven=event_driven,
                clarify_message=step.get("clarify"),
            )
        )
    # QA / DeviceSDK：不插长轮询截图垫步；GUI 非 DeviceSDK 才加结果截图
    if case_type != "QA":
        test_case["steps"].append(
            {
                "stepId": "screenshot-result",
                "description": "截图保存结果",
                "action": "screenshot",
                "params": {"saveName": "final_result"},
            }
        )
    return _ensure_timeout_covers_wait_steps(test_case)


def parse_workbook(
    workbook,
    *,
    default_wait_ms: int = EXCEL_DEFAULT_CHAT_WAIT_MS,
    default_timeout_ms: int = EXCEL_DEFAULT_TIMEOUT_MS,
    default_package: str = DEVICESDK_PKG,
) -> List[Dict[str, Any]]:
    ws = workbook.active
    headers = [_cell_str(c.value) for c in ws[1]]
    col: Dict[str, Any] = {}
    step_columns: List[int] = []
    for i, header in enumerate(headers):
        n = header.replace(" ", "")
        low = header.lower()
        if "用例编号" in header:
            col["id"] = i
        elif "用例名称" in header or "用例标题" in header:
            col.setdefault("name", i)
        elif "预置条件" in header:
            col["desc"] = i
        elif header == "描述" or ("描述" in header and "测试" not in header and "预置" not in header):
            col["desc"] = i
        elif "用例类型" in header:
            col["type"] = i
        elif "包名" in header or "package" in low:
            col["package"] = i
        elif "单步操作等待时间" in header or header == "等待时间":
            col["wait"] = i
        elif "澄清" in header:
            col["clarify"] = i
        elif "预期结果" in header or "exception" in low:
            col["exceptions"] = i
        elif ("预期" in header) and ("exceptions" not in col):
            col["exceptions"] = i
        elif low.startswith("timeout"):
            col["timeout"] = i
        elif "wait_duration" in low:
            col["wait_duration"] = i
        elif re.fullmatch(r"测试步骤\d+", header.replace(" ", "")):
            # 仅旧「测试步骤1/2…」多列；新格式 S1场景 等为标签，不当语料
            step_columns.append(i)
        elif "测试步骤" in header:
            col["steps"] = i
        elif "语料" in header and "预期" not in header:
            # 避免误匹配其它含「语料」的列；优先已由「测试步骤」占用
            col.setdefault("steps", i)

    rows: List[Dict[str, Any]] = []
    for row in ws.iter_rows(min_row=2, values_only=True):
        if not row or "id" not in col:
            continue
        case_id = _cell_str(row[col["id"]] if col["id"] < len(row) else None)
        if not case_id:
            continue
        package = (
            _cell_str(row[col["package"]] if "package" in col and col["package"] < len(row) else None)
            or default_package
        )
        timeout_ms = default_timeout_ms
        if "timeout" in col and col["timeout"] < len(row):
            timeout_ms = _parse_optional_ms(row[col["timeout"]], default_timeout_ms)
        wait_ms = default_wait_ms
        if "wait_duration" in col and col["wait_duration"] < len(row):
            wait_ms = _parse_optional_ms(row[col["wait_duration"]], default_wait_ms)

        chat_texts: List[str] = []
        if "steps" in col and col["steps"] < len(row):
            chat_texts = _parse_numbered_lines(row[col["steps"]])
        for idx in step_columns:
            if idx < len(row):
                t = _cell_str(row[idx])
                if t:
                    chat_texts.append(t)
        if not chat_texts:
            continue

        wait_list = (
            _parse_wait_seconds_lines(row[col["wait"]], wait_ms) if "wait" in col and col["wait"] < len(row) else []
        )
        clarify_map: Dict[int, str] = {}
        if "clarify" in col and col["clarify"] < len(row):
            for i, line in enumerate(_parse_numbered_lines(row[col["clarify"]]), 1):
                clarify_map[i] = line
        chat_steps = []
        for i, text in enumerate(chat_texts):
            item: Dict[str, Any] = {
                "text": text,
                "wait_ms": wait_list[i] if i < len(wait_list) else wait_ms,
            }
            cm = (clarify_map.get(i + 1) or "").strip()
            if cm:
                item["clarify"] = cm
            chat_steps.append(item)

        case_type = None
        if "type" in col and col["type"] < len(row):
            case_type = _cell_str(row[col["type"]])
        exceptions: List[str] = []
        if "exceptions" in col and col["exceptions"] < len(row):
            exceptions = _parse_numbered_lines(row[col["exceptions"]])
        rows.append(
            {
                "test_id": case_id,
                "test_name": _cell_str(row[col["name"]] if "name" in col and col["name"] < len(row) else None)
                or case_id,
                "description": _cell_str(row[col["desc"]] if "desc" in col and col["desc"] < len(row) else None),
                "package": package,
                "timeout_ms": timeout_ms,
                "wait_ms": wait_ms,
                "case_type": case_type,
                "chat_steps": chat_steps,
                "exceptions": exceptions,
            }
        )
    return rows


def convert_xlsx_to_testset(
    xlsx_path: Path,
    out_dir: Path,
    *,
    package_name: str = DEVICESDK_PKG,
    timeout_ms: int = EXCEL_DEFAULT_TIMEOUT_MS,
    wait_ms: int = EXCEL_DEFAULT_CHAT_WAIT_MS,
) -> Dict[str, Any]:
    """Same role as webserver import_excel_bytes_to_testset."""
    xlsx_path = Path(xlsx_path)
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    wb = load_workbook(str(xlsx_path), read_only=True, data_only=True)
    try:
        rows = parse_workbook(
            wb,
            default_wait_ms=int(wait_ms),
            default_timeout_ms=int(timeout_ms),
            default_package=package_name,
        )
    finally:
        wb.close()

    if not rows:
        return {"success": False, "message": "Excel 中未找到有效用例", "created": []}

    preset = EXCEL_PACKAGE_PRESETS.get(package_name) or EXCEL_PACKAGE_PRESETS[DEVICESDK_PKG]
    created: List[str] = []
    for row in rows:
        pkg = row.get("package") or package_name
        pkg_ui = EXCEL_PACKAGE_PRESETS.get(pkg) or preset
        tc = _create_test_case(
            test_id=row["test_id"],
            test_name=row["test_name"],
            description=row.get("description") or row["test_name"],
            package_name=pkg,
            input_id=pkg_ui["input_id"],
            send_id=pkg_ui["send_id"],
            wait_ms=int(row.get("wait_ms") or wait_ms),
            timeout_ms=int(row.get("timeout_ms") or timeout_ms),
            chat_steps=row["chat_steps"],
            case_type=row.get("case_type"),
            exceptions=row.get("exceptions") or None,
        )
        filename = f"{_sanitize_filename(row['test_id'])}.json"
        (out_dir / filename).write_text(
            json.dumps(tc, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        created.append(filename)
    return {
        "success": True,
        "message": f"已从 Excel 生成 {len(created)} 个用例",
        "created": created,
    }


def load_cases_from_runtime_json(
    runtime_dir: Path,
    order: Optional[List[str]] = None,
) -> List[Dict[str, Any]]:
    """Turn converted JSON into runner turn list (event-driven wait_sec).

    order: 文件名列表（Excel 行序）。缺省则读 _order.json；再没有才按文件名排序。
    """
    root = Path(runtime_dir)
    paths: List[Path] = []
    ordered_names: List[str] = list(order or [])
    if not ordered_names:
        order_file = root / "_order.json"
        if order_file.is_file():
            try:
                ordered_names = json.loads(order_file.read_text(encoding="utf-8")) or []
            except Exception:
                ordered_names = []
    if ordered_names:
        for name in ordered_names:
            if not name or str(name).startswith("_"):
                continue
            p = root / str(name)
            if p.is_file():
                paths.append(p)
        # 补上 order 里没有、但目录里存在的用例
        known = {p.name for p in paths}
        for p in sorted(root.glob("*.json")):
            if p.name.startswith("_"):
                continue
            if p.name not in known:
                paths.append(p)
    else:
        paths = [p for p in sorted(root.glob("*.json")) if not p.name.startswith("_")]

    items: List[Dict[str, Any]] = []
    for path in paths:
        data = json.loads(path.read_text(encoding="utf-8"))
        pkg = (data.get("targetApp") or {}).get("packageName") or DEVICESDK_PKG
        case_type = (data.get("caseType") or "GUI").upper()
        event_driven = case_type == "QA" or pkg == DEVICESDK_PKG
        timeout_ms = int(data.get("timeout") or EXCEL_DEFAULT_TIMEOUT_MS)
        turns: List[Dict[str, Any]] = []
        steps = data.get("steps") or []
        for i, step in enumerate(steps):
            if step.get("action") != "input":
                continue
            text = ((step.get("params") or {}).get("text") or "").strip()
            if not text:
                continue
            wait_ms = EXCEL_DEFAULT_CHAT_WAIT_MS
            for j in range(i + 1, min(i + 5, len(steps))):
                sid = str(steps[j].get("stepId") or "")
                if steps[j].get("action") == "wait" and sid.startswith("wait-response"):
                    wait_ms = int((steps[j].get("params") or {}).get("duration") or wait_ms)
                    break
            if event_driven:
                # duration already includes webserver safety floor; still cap by case timeout
                wait_sec = min(wait_ms, timeout_ms) / 1000.0
            else:
                wait_sec = wait_ms / 1000.0
            turns.append({"text": text, "wait_sec": wait_sec})
        if not turns:
            continue
        items.append(
            {
                "case_id": data.get("testId") or path.stem,
                "name": data.get("testName") or path.stem,
                "description": data.get("description") or "",
                "case_type": case_type,
                "package": pkg,
                "timeout_sec": timeout_ms / 1000.0,
                "event_driven": event_driven,
                "turns": turns,
                "json_path": str(path),
            }
        )
    return items
