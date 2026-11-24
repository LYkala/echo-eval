"""VL act / verify for precondition GUI steps."""
from __future__ import annotations

import base64
import json
import mimetypes
import re
from pathlib import Path
from typing import Any, Callable

from echo_eval.judge.client import chat_vision
from echo_eval.judge.model_config import load_vl_eval_config
from echo_eval.precondition.commands import filter_allowed_commands

ActFn = Callable[..., dict[str, Any]]
VerifyFn = Callable[..., dict[str, Any]]


def _encode_image(path: Path) -> tuple[str, str]:
    mime = mimetypes.guess_type(str(path))[0] or "image/png"
    data = base64.b64encode(path.read_bytes()).decode("utf-8")
    return mime, data


def extract_json_object(text: str) -> dict[str, Any]:
    raw = (text or "").strip()
    if not raw:
        raise ValueError("VL 返回空文本")
    fence = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", raw, re.S | re.I)
    if fence:
        raw = fence.group(1)
    else:
        start = raw.find("{")
        end = raw.rfind("}")
        if start < 0 or end <= start:
            raise ValueError(f"VL 返回中无 JSON 对象: {raw[:200]}")
        raw = raw[start : end + 1]
    obj = json.loads(raw)
    if not isinstance(obj, dict):
        raise ValueError("VL JSON 根节点必须是 object")
    return obj


def vl_propose_commands(
    *,
    screenshot: Path,
    step_text: str,
    prefix: list[str],
    wm_size: str,
    feedback: str = "",
    model_cfg: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """Ask VL for whitelisted input commands to achieve step_text."""
    cfg = model_cfg or load_vl_eval_config()
    mime, b64 = _encode_image(Path(screenshot))
    prefix_txt = " → ".join(prefix) if prefix else "（无，当前为第一步）"
    feedback_txt = feedback.strip() or "（无）"
    system = (
        "你是 Android UI 自动化助手。根据截图给出完成「当前预置步骤」所需的 "
        "adb shell 命令。只允许使用：\n"
        "- input tap X Y\n"
        "- input swipe X1 Y1 X2 Y2 [duration_ms]\n"
        "- input keyevent CODE\n"
        "禁止 am/pm/settings/rm 等其它命令。坐标必须落在屏幕范围内。"
        "只输出一个 JSON 对象，不要其它说明。"
    )
    user_text = (
        f"屏幕信息: {wm_size or 'unknown'}\n"
        f"已完成前缀: {prefix_txt}\n"
        f"当前步骤: {step_text}\n"
        f"上一轮失败反馈: {feedback_txt}\n"
        "返回 JSON 字段:\n"
        '  "adb_commands": ["input tap 100 200", ...],\n'
        '  "reason": "简短中文说明"\n'
    )
    messages = [
        {"role": "system", "content": system},
        {
            "role": "user",
            "content": [
                {"type": "text", "text": user_text},
                {
                    "type": "image_url",
                    "image_url": {"url": f"data:{mime};base64,{b64}"},
                },
            ],
        },
    ]
    timeout = float(cfg.get("timeout_sec") or cfg.get("timeout") or 120)
    text = chat_vision(cfg, messages, temperature=0.0, max_tokens=1024, timeout_sec=timeout)
    parsed = extract_json_object(text)
    cmds = parsed.get("adb_commands") or parsed.get("commands") or []
    if not isinstance(cmds, list):
        raise ValueError("adb_commands 必须是数组")
    cmds_s = [str(c).strip() for c in cmds if str(c).strip()]
    allowed, rejected = filter_allowed_commands(cmds_s)
    return {
        "raw": text,
        "parsed": parsed,
        "adb_commands": allowed,
        "rejected": rejected,
        "reason": str(parsed.get("reason") or ""),
    }


def vl_verify_step(
    *,
    screenshot_after: Path,
    step_text: str,
    prefix: list[str],
    screenshot_before: Path | None = None,
    model_cfg: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """Ask VL whether the precondition step looks achieved."""
    cfg = model_cfg or load_vl_eval_config()
    mime_a, b64_a = _encode_image(Path(screenshot_after))
    prefix_txt = " → ".join(prefix) if prefix else "（无）"
    system = (
        "你是 Android UI 测试验收助手。根据截图判断「当前预置步骤」是否已经达成。"
        "只输出一个 JSON 对象。"
    )
    user_text = (
        f"已完成前缀: {prefix_txt}\n"
        f"当前步骤: {step_text}\n"
        "请判断当前画面是否表明该步骤已成功完成。\n"
        "返回 JSON 字段:\n"
        '  "achieved": true/false,\n'
        '  "reason": "简短中文说明"\n'
    )
    content: list[dict[str, Any]] = [{"type": "text", "text": user_text}]
    if screenshot_before and Path(screenshot_before).is_file():
        mime_b, b64_b = _encode_image(Path(screenshot_before))
        content.append({"type": "text", "text": "操作前截图："})
        content.append(
            {
                "type": "image_url",
                "image_url": {"url": f"data:{mime_b};base64,{b64_b}"},
            }
        )
        content.append({"type": "text", "text": "操作后截图："})
    content.append(
        {
            "type": "image_url",
            "image_url": {"url": f"data:{mime_a};base64,{b64_a}"},
        }
    )
    messages = [
        {"role": "system", "content": system},
        {"role": "user", "content": content},
    ]
    timeout = float(cfg.get("timeout_sec") or cfg.get("timeout") or 120)
    text = chat_vision(cfg, messages, temperature=0.0, max_tokens=512, timeout_sec=timeout)
    parsed = extract_json_object(text)
    achieved = parsed.get("achieved")
    if isinstance(achieved, str):
        achieved = achieved.strip().lower() in ("1", "true", "yes", "y", "是")
    else:
        achieved = bool(achieved)
    return {
        "raw": text,
        "parsed": parsed,
        "achieved": achieved,
        "reason": str(parsed.get("reason") or ""),
    }


def mock_propose_commands(
    *,
    screenshot: Path,
    step_text: str,
    prefix: list[str],
    wm_size: str,
    feedback: str = "",
    model_cfg: dict[str, Any] | None = None,
) -> dict[str, Any]:
    # Center-ish tap; tests / offline dry-run only.
    return {
        "raw": "",
        "parsed": {},
        "adb_commands": ["input tap 540 1200"],
        "rejected": [],
        "reason": f"mock act for {step_text}",
    }


def mock_verify_step(
    *,
    screenshot_after: Path,
    step_text: str,
    prefix: list[str],
    screenshot_before: Path | None = None,
    model_cfg: dict[str, Any] | None = None,
) -> dict[str, Any]:
    return {
        "raw": "",
        "parsed": {"achieved": True},
        "achieved": True,
        "reason": f"mock verify for {step_text}",
    }
