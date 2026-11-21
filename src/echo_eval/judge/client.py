"""OpenAI-compatible vision chat client (openai SDK or stdlib urllib)."""
from __future__ import annotations

import json
import ssl
import urllib.error
import urllib.request
from typing import Any


class JudgeClientError(RuntimeError):
    pass


def _completion_extra_body(model_cfg: dict[str, Any]) -> dict[str, Any] | None:
    eb = model_cfg.get("extra_body")
    if isinstance(eb, dict) and eb:
        return eb
    mid = str(model_cfg.get("model_id") or "").lower()
    if "deepseek" in mid:
        return {"thinking": {"type": "disabled"}}
    return None


def _message_text(message: Any) -> str:
    if message is None:
        return ""
    if isinstance(message, dict):
        content = message.get("content")
        text = (content or "").strip() if isinstance(content, str) else str(content or "").strip()
        if text:
            return text
        rc = message.get("reasoning_content")
        return (rc or "").strip() if isinstance(rc, str) else ""
    content = getattr(message, "content", None)
    text = (content or "").strip() if isinstance(content, str) else str(content or "").strip()
    if text:
        return text
    rc = getattr(message, "reasoning_content", None)
    return (rc or "").strip() if isinstance(rc, str) else ""


def chat_vision(
    model_cfg: dict[str, Any],
    messages: list[dict[str, Any]],
    *,
    temperature: float = 0.0,
    max_tokens: int = 3072,
    timeout_sec: float = 120.0,
) -> str:
    """Return assistant text. Tries openai package, then urllib."""
    try:
        from openai import OpenAI  # type: ignore
    except ImportError:
        return _chat_vision_urllib(
            model_cfg,
            messages,
            temperature=temperature,
            max_tokens=max_tokens,
            timeout_sec=timeout_sec,
        )

    client = OpenAI(
        api_key=model_cfg["api_key"],
        base_url=model_cfg["base_url"],
        timeout=timeout_sec,
        max_retries=0,
    )
    kwargs: dict[str, Any] = {
        "model": model_cfg["model_id"],
        "messages": messages,
        "temperature": temperature,
        "max_tokens": max_tokens,
    }
    eb = _completion_extra_body(model_cfg)
    if eb:
        kwargs["extra_body"] = eb
    try:
        response = client.chat.completions.create(**kwargs)
    except Exception as e:
        raise JudgeClientError(f"VL 调用失败: {e}") from e
    return _message_text(response.choices[0].message)


def _chat_vision_urllib(
    model_cfg: dict[str, Any],
    messages: list[dict[str, Any]],
    *,
    temperature: float,
    max_tokens: int,
    timeout_sec: float,
) -> str:
    base = str(model_cfg["base_url"]).rstrip("/")
    url = base + "/chat/completions"
    payload: dict[str, Any] = {
        "model": model_cfg["model_id"],
        "messages": messages,
        "temperature": temperature,
        "max_tokens": max_tokens,
    }
    eb = _completion_extra_body(model_cfg)
    if eb:
        payload.update(eb)

    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        method="POST",
        headers={
            "Authorization": f"Bearer {model_cfg['api_key']}",
            "Content-Type": "application/json",
        },
    )
    ctx = ssl.create_default_context()
    try:
        with urllib.request.urlopen(req, timeout=timeout_sec, context=ctx) as resp:
            body = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", errors="replace")
        raise JudgeClientError(f"VL HTTP {e.code}: {detail}") from e
    except Exception as e:
        raise JudgeClientError(f"VL 请求失败: {e}") from e

    try:
        obj = json.loads(body)
        msg = obj["choices"][0]["message"]
    except (KeyError, IndexError, TypeError, json.JSONDecodeError) as e:
        raise JudgeClientError(f"VL 响应无法解析: {body[:500]}") from e
    return _message_text(msg)
