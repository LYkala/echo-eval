"""ASR accuracy judge: multi-frame VL compare on-screen bubble text vs spoken text."""
from __future__ import annotations

import json
import os
import re
import time
from pathlib import Path
from typing import Any, Literal

from echo_eval.judge.client import JudgeClientError, chat_vision
from echo_eval.judge.gui_poll import encode_image, subsample_image_paths
from echo_eval.judge.model_config import ModelConfigError, find_model_config_path, load_vl_eval_config
from echo_eval.judge.schema import parse_json_content

Backend = Literal["auto", "vl", "mock"]

_PUNCT_RE = re.compile(r"[\s\W_]+", re.UNICODE)


def normalize_asr_text(text: str) -> str:
    """Strip whitespace/punctuation for loose equality (点击电影。 ≈ 点击电影)."""
    s = (text or "").strip().lower()
    s = _PUNCT_RE.sub("", s)
    return s


def is_ignore_asr_text(text: str, ignore_texts: list[str] | None = None) -> bool:
    norms = {normalize_asr_text(x) for x in (ignore_texts or []) if str(x).strip()}
    if not norms:
        norms = {normalize_asr_text("我还在听，说再见退出")}
    return normalize_asr_text(text) in norms


def texts_match(spoken: str, recognized: str, *, ignore_texts: list[str] | None = None) -> bool:
    if is_ignore_asr_text(recognized, ignore_texts):
        return False
    a = normalize_asr_text(spoken)
    b = normalize_asr_text(recognized)
    return bool(a) and bool(b) and a == b


def resolve_asr_backend(backend: Backend) -> str:
    if backend == "vl":
        return "vl"
    if backend == "mock":
        return "mock"
    return "vl" if find_model_config_path() else "mock"


def evaluate_asr_images_mock(
    image_paths: list[str],
    spoken_text: str,
    *,
    ignore_texts: list[str] | None = None,
) -> dict[str, Any]:
    """Mock: pass if any image filename/path hint or non-empty images + spoken non-empty.

    For unit tests without VL: if spoken is non-empty and at least one image exists → pass,
    unless spoken normalizes to an ignore phrase.
    """
    paths = [p for p in image_paths if p and os.path.isfile(p)]
    if not paths or not normalize_asr_text(spoken_text):
        return {
            "raw": "",
            "parsed": {
                "match": False,
                "recognized_text": "",
                "spoken_text": spoken_text,
                "reason": "无截图或无播报文案",
                "evidence_images": [],
            },
            "images": paths,
            "passed": False,
        }
    if is_ignore_asr_text(spoken_text, ignore_texts):
        return {
            "raw": "",
            "parsed": {
                "match": False,
                "recognized_text": "",
                "spoken_text": spoken_text,
                "reason": "播报文案为忽略提示，无法评判",
                "evidence_images": [os.path.basename(paths[-1])],
            },
            "images": paths,
            "passed": False,
        }
    # Local filename convention for tests: *asr_ok* / *asr_fail*
    names = " ".join(os.path.basename(p) for p in paths)
    if "asr_fail" in names:
        passed = False
        recognized = "错误识别"
        reason = "mock: 文件名含 asr_fail"
    else:
        passed = True
        recognized = spoken_text
        reason = "mock: 有截图且假定识别一致"
    return {
        "raw": "",
        "parsed": {
            "match": passed,
            "recognized_text": recognized,
            "spoken_text": spoken_text,
            "reason": reason,
            "evidence_images": [os.path.basename(p) for p in paths[:3]],
        },
        "images": paths,
        "passed": passed,
    }


def evaluate_asr_images(
    image_paths: list[str],
    spoken_text: str,
    *,
    ignore_texts: list[str] | None = None,
    model_cfg: dict[str, Any] | None = None,
) -> dict[str, Any]:
    paths = [p for p in image_paths if p and os.path.isfile(p)]
    ignore = ignore_texts or ["我还在听，说再见退出"]
    if not paths:
        return {
            "raw": "",
            "parsed": {
                "match": False,
                "recognized_text": "",
                "spoken_text": spoken_text,
                "reason": "无 ASR 截图",
                "evidence_images": [],
            },
            "images": [],
            "passed": False,
        }

    selected = subsample_image_paths(paths, max_images=12)
    cfg = model_cfg or load_vl_eval_config()
    base_timeout = float(
        cfg.get("timeout_sec") or cfg.get("timeout") or os.environ.get("VL_EVAL_TIMEOUT_SEC") or 120
    )
    timeout_sec = min(480.0, base_timeout + 12.0 * len(selected))
    names = [os.path.basename(p) for p in selected]
    numbered = "\n".join(f"  图{i}: {n}" for i, n in enumerate(names, 1))
    ignore_list = "、".join([f"「{t}」" for t in ignore])
    prompt = f"""
你是语音助手 ASR 准确率评判员。下面按时间顺序给出「用户说完一句后」的屏幕截图
（共 {len(selected)} 张，图1…图{len(selected)}）。屏幕下方通常有语音识别气泡。

任务：从这些截图中读出气泡里的**用户语音识别原文**，与播报文案比对是否一致。

规则（必须遵守）：
1. 只看屏幕**下方**语音条/气泡中的文字，不要用页面标题、榜单、按钮等其它 UI 文案冒充识别结果。
2. 若气泡内容是固定提示 {ignore_list}，**忽略**，不算识别结果；继续看其它帧。
3. 长句可能跨多帧逐步显示：请综合全部截图，拼出最终完整识别文（以出现过的最完整一版为准）。
4. 比对时忽略标点与首尾空白（例如「点击电影。」与「点击电影」视为一致）。
5. 多帧中始终只有固定提示、空白、或识别文与播报文案不一致 → match=false。
6. evidence_images 必须填写下方对照表中的**完整文件名**。

播报文案（期望识别内容）：
「{spoken_text}」

截图对照表：
{numbered}

请只输出一个 JSON 对象（不要 Markdown 围栏），字段：
{{
  "match": true/false,
  "recognized_text": "从气泡读到的识别原文（无则空字符串）",
  "spoken_text": "{spoken_text}",
  "reason": "简短理由",
  "evidence_images": ["文件名1", "..."]
}}
""".strip()

    content: list[dict[str, Any]] = [{"type": "text", "text": prompt}]
    for p in selected:
        content.append(
            {
                "type": "image_url",
                "image_url": {"url": f"data:image/png;base64,{encode_image(p)}"},
            }
        )
    messages = [{"role": "user", "content": content}]
    try:
        raw = chat_vision(
            cfg,
            messages,
            temperature=0.0,
            max_tokens=int(cfg.get("max_tokens") or 1024),
            timeout_sec=timeout_sec,
        )
    except JudgeClientError:
        raise
    parsed = parse_json_content(raw)
    if not isinstance(parsed, dict):
        parsed = {
            "match": False,
            "recognized_text": "",
            "spoken_text": spoken_text,
            "reason": f"模型返回无法解析: {(raw or '')[:200]}",
            "evidence_images": [],
        }
    recognized = str(parsed.get("recognized_text") or "").strip()
    match = parsed.get("match")
    if not isinstance(match, bool):
        match = texts_match(spoken_text, recognized, ignore_texts=ignore)
    # Local harden: ignore-only text cannot pass
    if match and is_ignore_asr_text(recognized, ignore):
        match = False
        parsed["reason"] = (str(parsed.get("reason") or "") + "；本地规则：识别文为固定提示").strip("；")
    if match and not texts_match(spoken_text, recognized, ignore_texts=ignore):
        # model said match but normalized texts differ — trust normalized compare
        match = False
        parsed["reason"] = (
            str(parsed.get("reason") or "") + "；本地规则：去标点后与播报文案不一致"
        ).strip("；")
    parsed["match"] = match
    parsed["spoken_text"] = spoken_text
    parsed["recognized_text"] = recognized
    return {
        "raw": raw,
        "parsed": parsed,
        "images": selected,
        "image_count_total": len(paths),
        "image_count_used": len(selected),
        "passed": bool(match),
    }


def judge_asr_utterance(
    *,
    spoken_text: str,
    image_paths: list[str],
    backend: Backend = "auto",
    ignore_texts: list[str] | None = None,
    write_path: Path | str | None = None,
) -> dict[str, Any]:
    chosen = resolve_asr_backend(backend)
    ignore = ignore_texts or ["我还在听，说再见退出"]
    if chosen == "mock":
        result = evaluate_asr_images_mock(image_paths, spoken_text, ignore_texts=ignore)
        source = "mock_asr"
    else:
        try:
            cfg = load_vl_eval_config()
        except ModelConfigError as e:
            raise JudgeClientError(str(e)) from e
        result = evaluate_asr_images(
            image_paths, spoken_text, ignore_texts=ignore, model_cfg=cfg
        )
        source = "vl_asr"

    data: dict[str, Any] = {
        "mode": "asr_accuracy",
        "source": source,
        "backend": chosen,
        "spoken_text": spoken_text,
        "ignore_texts": ignore,
        "images": result.get("images") or image_paths,
        "image_count_total": result.get("image_count_total", len(image_paths)),
        "image_count_used": result.get("image_count_used", len(result.get("images") or [])),
        "result_raw": result.get("raw", ""),
        "result_parsed": result.get("parsed"),
        "passed": bool(result.get("passed")),
        "timestamp": time.time(),
    }
    if write_path:
        out = Path(write_path)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        data["evaluation_path"] = str(out.resolve())
    return data
