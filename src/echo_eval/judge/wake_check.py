"""VL wake-success check after wake-word playback."""
from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

from echo_eval.judge.client import JudgeClientError, chat_vision
from echo_eval.judge.gui_poll import encode_image
from echo_eval.judge.model_config import load_vl_eval_config


def evaluate_wake_images(
    before_path: Path | str,
    after_paths: list[Path | str],
    *,
    wake_text: str = "小翼管家",
    model_cfg: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """
    Decide whether wake succeeded.

    Wake **failed** when the model finds: no meaningful UI change vs before
    AND no dialog / ASR bubble (top or bottom).
    """
    before = Path(before_path)
    afters = [Path(p) for p in after_paths if p and Path(p).is_file()]
    if not before.is_file():
        return {
            "ok": False,
            "status": "error",
            "reason": f"唤醒前截图缺失: {before}",
            "raw": "",
        }
    if not afters:
        return {
            "ok": False,
            "status": "error",
            "reason": "唤醒后无截图",
            "raw": "",
        }

    cfg = model_cfg or load_vl_eval_config()
    names = ["before.png"] + [f"after_{i}.png" for i in range(1, len(afters) + 1)]
    numbered = "\n".join(f"  {n}" for n in names)
    prompt = f"""
你是语音助手「唤醒」判定员。第一张是播唤醒词「{wake_text}」**之前**的屏幕；
后面按时间顺序是播完后的截图。

判定唤醒**成功**的任一信号即可：
- 出现助手对话气泡、聆听态、识别条，或底部/上方语音相关 UI；
- 或相对 before 有与助手唤醒相关的明显界面变化（面板弹出、波形、提示「我在听」等）。

判定唤醒**失败**（须同时近似满足）：
- 相对 before **无明显界面变化**；
- **且** 底部与上方都**没有**对话气泡 / 语音识别条 / 助手聆听态。

请只输出一个 JSON（不要 Markdown 围栏）：
{{
  "wake_ok": true/false,
  "ui_changed": true/false,
  "has_bubble": true/false,
  "reason": "简短中文理由"
}}

截图顺序：
{numbered}
""".strip()

    content: list[dict[str, Any]] = [{"type": "text", "text": prompt}]
    for p in [before, *afters[:4]]:
        mime = "image/png" if str(p).lower().endswith(".png") else "image/jpeg"
        content.append(
            {
                "type": "image_url",
                "image_url": {"url": f"data:{mime};base64,{encode_image(p)}"},
            }
        )
    messages = [{"role": "user", "content": content}]
    timeout = float(cfg.get("timeout_sec") or cfg.get("timeout") or 90)
    try:
        raw = chat_vision(
            cfg,
            messages,
            temperature=0.0,
            max_tokens=512,
            timeout_sec=min(180.0, timeout),
        )
    except JudgeClientError as e:
        return {
            "ok": False,
            "status": "error",
            "reason": str(e),
            "raw": "",
        }

    parsed = _parse_json(raw)
    wake_ok = bool(parsed.get("wake_ok")) if isinstance(parsed.get("wake_ok"), bool) else False
    if not isinstance(parsed.get("wake_ok"), bool):
        # Heuristic fallback from text
        low = (raw or "").lower()
        wake_ok = "wake_ok\": true" in low.replace(" ", "") or "\"wake_ok\":true" in low.replace(
            " ", ""
        )
    return {
        "ok": wake_ok,
        "status": "ok" if wake_ok else "failed",
        "ui_changed": parsed.get("ui_changed"),
        "has_bubble": parsed.get("has_bubble"),
        "reason": str(parsed.get("reason") or "").strip() or (raw or "")[:200],
        "raw": (raw or "")[:2000],
    }


def evaluate_wake_images_mock(
    before_path: Path | str,
    after_paths: list[Path | str],
    *,
    wake_text: str = "小翼管家",
) -> dict[str, Any]:
    _ = (before_path, after_paths, wake_text)
    return {
        "ok": True,
        "status": "ok",
        "ui_changed": True,
        "has_bubble": True,
        "reason": "mock wake ok",
        "raw": "",
    }


def _parse_json(raw: str) -> dict[str, Any]:
    text = (raw or "").strip()
    if not text:
        return {}
    try:
        obj = json.loads(text)
        return obj if isinstance(obj, dict) else {}
    except json.JSONDecodeError:
        m = re.search(r"\{[\s\S]*\}", text)
        if not m:
            return {}
        try:
            obj = json.loads(m.group(0))
            return obj if isinstance(obj, dict) else {}
        except json.JSONDecodeError:
            return {}
