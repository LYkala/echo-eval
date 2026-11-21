"""Doubao / OpenAI-compatible VL evaluation (self-contained in this project)."""
from __future__ import annotations

import base64
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def _get_config_candidates():
    """按优先级返回 VL 模型配置文件路径（均相对本工程）。"""
    candidates = []
    env_path = os.environ.get("VL_EVAL_CONFIG", "").strip()
    if env_path:
        candidates.append(env_path)
    candidates.extend(
        [
            str(ROOT / "configs" / "model.yaml"),
            str(ROOT / "config" / "model.yaml"),
        ]
    )
    seen = set()
    ordered = []
    for path in candidates:
        if path and path not in seen:
            seen.add(path)
            ordered.append(path)
    return ordered


def _load_vl_eval_config():
    """从 configs/model.yaml 加载 vl_eval 模型配置。"""
    try:
        import yaml
    except ImportError as e:
        raise ImportError(
            "缺少 PyYAML，请在本工程执行: pip install -r requirements.txt"
        ) from e

    config_path = None
    for path in _get_config_candidates():
        if os.path.isfile(path):
            config_path = path
            break
    if not config_path:
        paths = ", ".join(_get_config_candidates())
        raise FileNotFoundError(
            "未找到 VL 模型配置。请复制 configs/model.yaml.example 为 "
            f"configs/model.yaml 并填写 api_key。已查找: {paths}"
        )

    with open(config_path, "r", encoding="utf-8") as f:
        config = yaml.safe_load(f) or {}
    models = config.get("models") or {}
    entry = models.get("vl_eval")
    if not entry:
        raise ValueError(f"未在 {config_path} 中找到 models.vl_eval 配置")
    return entry


def encode_image(image_path):
    with open(image_path, "rb") as f:
        return base64.b64encode(f.read()).decode("utf-8")


def _openai_client(model_cfg):
    try:
        from openai import OpenAI
    except ImportError as e:
        raise ImportError(
            "缺少 openai，请在本工程执行: pip install -r requirements.txt"
        ) from e
    timeout_sec = float(
        model_cfg.get("timeout_sec")
        or model_cfg.get("timeout")
        or os.environ.get("VL_EVAL_TIMEOUT_SEC")
        or 120
    )
    return OpenAI(
        api_key=model_cfg["api_key"],
        base_url=model_cfg["base_url"],
        timeout=timeout_sec,
        max_retries=0,
    )


def _parse_json_content(raw_result):
    parsed = None
    try:
        clean = (raw_result or "").strip()
        if clean.startswith("```json"):
            clean = clean[7:]
        elif clean.startswith("```"):
            clean = clean[3:]
        if clean.endswith("```"):
            clean = clean[:-3]
        parsed = json.loads(clean.strip())
    except (json.JSONDecodeError, Exception):
        parsed = None
    return parsed


def evaluate(image_path, exceptions):
    """截图 + exceptions 评判。返回 {"raw", "parsed"}。"""
    model_cfg = _load_vl_eval_config()
    client = _openai_client(model_cfg)
    base64_image = encode_image(image_path)

    prompt = f"""
你是一个严格的gui任务评判员，我需要你根据该任务评测集的exceptions，以及任务最终的截图来进行任务结果的评判。

对你的评判要求如下：
    - 对每一步的结果进行反思，判断这一步的结果是否符合需要
    - 对最终结果进行反思，只有当每一步的结果都符合需要的时候，才能认为任务整体成功

你需要严格的输出 JSON字符串，不要以```json开头，应该以{{开头
think简短一些，不要超过二十个字：
[
  {{
    "expectation":"",
    "think":"",
    "result":"success"
  }},
  {{
    "expectation":"",
    "think":"",
    "result":"failed"
  }},
  {{
    "final_result":"",
    "think":"",
    "result":"success"
  }}
]

期望内容：
{exceptions}
"""

    response = client.chat.completions.create(
        model=model_cfg["model_id"],
        messages=[
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": prompt},
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": f"data:image/jpeg;base64,{base64_image}"
                        },
                    },
                ],
            }
        ],
        temperature=0.2,
        max_tokens=1024,
    )
    raw_result = response.choices[0].message.content
    return {"raw": raw_result, "parsed": _parse_json_content(raw_result)}


def evaluate_reply_and_image(image_path, exceptions, replies=None, user_messages=None):
    """回复文本 + 截图 + exceptions 综合评判。"""
    model_cfg = _load_vl_eval_config()
    client = _openai_client(model_cfg)

    reply_blocks = []
    for i, item in enumerate(replies or [], 1):
        if isinstance(item, dict):
            rnd = item.get("round") or i
            text = (
                item.get("reply_text")
                or item.get("actual")
                or item.get("text")
                or ""
            ).strip()
            um = item.get("user_message") or ""
            if user_messages and not um and i - 1 < len(user_messages):
                um = user_messages[i - 1] or ""
        else:
            rnd = i
            text = str(item or "").strip()
            um = (
                user_messages[i - 1]
                if user_messages and i - 1 < len(user_messages)
                else ""
            ) or ""
        reply_blocks.append(
            f"轮次{rnd}:\n  用户提问: {um}\n  模型回答: {text or '(空)'}"
        )
    replies_text = "\n\n".join(reply_blocks) if reply_blocks else "(无模型回答)"

    base64_image = encode_image(image_path)
    prompt = f"""
你是一个严格的 GUI 任务评判员。请同时依据：
1) 任务 exceptions（期望条件）
2) 模型在 DeviceSDK 对话中的文字回复
3) 任务最终界面截图
综合判断任务是否成功。

要求：
- 对每条 expectation 独立判断：结合「模型是否声称完成」与「截图是否佐证」
- 若模型回答声称成功但截图明显未达成，判 failed
- 若截图已达成但模型回答含糊，可结合截图判 success
- 全部 expectation 都 success 时，final_result 才为 success
- think 简短，不超过二十字
- 严格输出 JSON 数组，不要用 ``` 包裹，以 [ 开头：
[
  {{"expectation":"...","think":"...","result":"success"}},
  {{"expectation":"...","think":"...","result":"failed"}},
  {{"final_result":"整体","think":"...","result":"success"}}
]

期望内容（exceptions）：
{exceptions}

模型回复：
{replies_text}
"""

    response = client.chat.completions.create(
        model=model_cfg["model_id"],
        messages=[
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": prompt},
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": f"data:image/jpeg;base64,{base64_image}"
                        },
                    },
                ],
            }
        ],
        temperature=0.2,
        max_tokens=1024,
    )
    raw_result = response.choices[0].message.content
    return {"raw": raw_result, "parsed": _parse_json_content(raw_result)}


def evaluate_qa_rounds(rounds):
    """多轮 QA 文本评判。"""
    to_judge = [r for r in (rounds or []) if (r.get("expected") or "").strip()]
    if not to_judge:
        return {
            "raw": "[]",
            "parsed": [
                {
                    "final_result": "无待评判轮次",
                    "think": "无预期",
                    "result": "success",
                }
            ],
        }

    model_cfg = _load_vl_eval_config()
    client = _openai_client(model_cfg)

    rounds_text = []
    for r in to_judge:
        rounds_text.append(
            f"轮次{r.get('round')}:\n"
            f"  用户提问: {r.get('user_message') or ''}\n"
            f"  预期回答: {r.get('expected') or ''}\n"
            f"  模型回答: {r.get('actual') or '(空)'}"
        )
    joined = "\n\n".join(rounds_text)

    prompt = f"""
你是一个严格的多轮对话评判员。请按轮次判断「模型回答」是否满足「预期回答」。
仅评判给出了预期回答的轮次；每一轮独立判断。
全部待评判轮次都 success 时，最终 final_result 才为 success。

说明：
- 若预期写成「图片中是否… / 界面是否…」等截图表述，请根据模型回答内容推断任务是否已达成该条件。
- 模型回答为空则该轮 failed。

严格输出 JSON 数组（不要用```包裹），格式：
[
  {{"round":1,"expectation":"...","think":"简短","result":"success"}},
  {{"round":2,"expectation":"...","think":"简短","result":"failed"}},
  {{"final_result":"整体","think":"简短","result":"success"}}
]

待评判内容：
{joined}
"""

    response = client.chat.completions.create(
        model=model_cfg["model_id"],
        messages=[{"role": "user", "content": prompt}],
        temperature=0.2,
        max_tokens=1024,
    )
    raw_result = response.choices[0].message.content
    parsed = _parse_json_content(raw_result)
    if isinstance(parsed, list):
        judged_rounds = [r.get("round") for r in to_judge if r.get("round") is not None]
        step_idx = 0
        for item in parsed:
            if not isinstance(item, dict) or item.get("final_result") is not None:
                continue
            if item.get("round") is None and step_idx < len(judged_rounds):
                item["round"] = judged_rounds[step_idx]
            step_idx += 1
    return {"raw": raw_result, "parsed": parsed}
