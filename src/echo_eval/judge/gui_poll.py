"""Multi-frame GUI poll judge — E1 port of TestApp evaluate_gui_poll_images."""
from __future__ import annotations

import base64
import os
from pathlib import Path
from typing import Any, Callable

from echo_eval.judge.client import JudgeClientError, chat_vision
from echo_eval.judge.model_config import load_vl_eval_config
from echo_eval.judge.schema import (
    normalize_gui_poll_evidence_refs,
    parse_json_content,
    reject_placeholder_success,
)


ProgressCb = Callable[..., None]


def subsample_image_paths(image_paths: list[str], max_images: int = 16) -> list[str]:
    """Port of TestApp _subsample_image_paths."""
    paths = [p for p in (image_paths or []) if p]
    if len(paths) <= max_images:
        return paths
    if max_images <= 2:
        return [paths[0], paths[-1]][:max_images]
    inner_budget = max_images - 2
    inner = paths[1:-1]
    if not inner:
        return [paths[0], paths[-1]]
    if len(inner) <= inner_budget:
        return [paths[0]] + inner + [paths[-1]]
    step = (len(inner) - 1) / float(inner_budget - 1) if inner_budget > 1 else 0
    picked = []
    for i in range(inner_budget):
        idx = int(round(i * step)) if inner_budget > 1 else 0
        idx = min(max(idx, 0), len(inner) - 1)
        picked.append(inner[idx])
    out = [paths[0]]
    for p in picked:
        if p not in out:
            out.append(p)
    if paths[-1] not in out:
        out.append(paths[-1])
    return out[:max_images]


def encode_image(image_path: str) -> str:
    with open(image_path, "rb") as f:
        return base64.b64encode(f.read()).decode("utf-8")


def _emit(progress_cb: ProgressCb | None, phase: str, percent: int, message: str = "") -> None:
    if not progress_cb:
        return
    try:
        progress_cb(phase=phase, percent=percent, message=message)
    except Exception:
        pass


def evaluate_gui_poll_images(
    image_paths: list[str],
    exceptions: Any,
    *,
    progress_cb: ProgressCb | None = None,
    model_cfg: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """
    E1: main VL call + JSON parse + local normalize/placeholder rules.
    Skips TestApp evidence grounding / repair (E2/E3).
    """
    paths = [p for p in (image_paths or []) if p and os.path.isfile(p)]
    if not paths:
        return {"raw": "", "parsed": None, "images": []}

    _emit(progress_cb, "vl_prepare", 35, "准备截图评判")
    selected = subsample_image_paths(paths, max_images=16)
    cfg = model_cfg or load_vl_eval_config()
    base_timeout = float(
        cfg.get("timeout_sec")
        or cfg.get("timeout")
        or os.environ.get("VL_EVAL_TIMEOUT_SEC")
        or 120
    )
    timeout_sec = min(600.0, base_timeout + 20.0 * len(selected))

    names = [os.path.basename(p) for p in selected]
    numbered = "\n".join(f"  图{i}: {n}" for i, n in enumerate(names, 1))
    prompt = f"""
你是一个严格的 GUI 任务评判员。下面按时间顺序给出任务执行过程中的 gui_poll 截图
（共提供 {len(selected)} 张，标记为 图1…图{len(selected)}；若有抽样会保留首尾与中间关键帧）。
请结合**全部**截图，对照 exceptions 判断任务是否达成预期。
默认从严：不确定、证据不足、只完成一半流程时，一律判 failed。

重要（证据图必须与画面一致）：
- 「图N」只表示本批列表中的第 N 张（见下方对照表），与文件名里的 gui_poll_数字 不是同一套编号，禁止混用
- evidence[].image 必须写对照表中的完整文件名（例如 {names[min(7, len(names)-1)] if names else 'xxx_gui_poll_6.jpg'}），不要只写「图N」或「gui_poll_N」
- evidence[].reason 只能描述**该文件名对应那一张图里实际可见**的内容；禁止把后一帧的弹窗/页面写到前一帧上
- 若某现象只在后一帧出现，证据必须引用出现该现象的那一帧文件名

判定规则（必须遵守，否则判 failed）：
- 仅打开 App / 仅进入某功能首页 / 仅出现输入框或占位文案，不等于任务完成
- expectation 中出现的具体目标名（如地名「钟楼」「西安钟楼」、影片名、商家名等）必须在**至少一张**截图中作为已选中/已填入/已搜索到的结果可见；禁止把空输入框、占位提示当成已达成
- 下列占位/引导文案单独出现时，不得当作目的地或目标已设置成功：「你要去哪儿」「请输入目的地」「搜索目的地」「去哪儿」「请输入」「搜索影片/地点」等
- expectation 里的「或」只表示“可用不同控件证明同一完成态”（例如已设好目的地后的确认键，或已进入呼叫确认页）；不是“做到任意一半就算过”
- 若最终停留在：未填目的地的打车首页、整页都是会员开通（没有该影片播放器在出画）、搜索页未点进目标、详情页播放器未出画等中间态，判 failed
- 播放类期望：已进入目标影片的播放器且画面正在出画（正片、预告、贴片广告都算），并且同一页能确认片名（标题/选集/简介），判 success
- 播放器上的贴片广告倒计时（如「开通黄金VIP关闭此广告」「剩余xx秒」）表示已经开始播放该片，不要因此判 failed
- 对话里出现「GUI任务执行失败」不能推翻画面上该影片播放器已在出画的证据
- 只有静止海报、播放器黑屏、仅有「立即播放/去观看」且没有画面在动，仍判 failed
- 对每条 expectation 独立判断：综合整段截图序列，而非只看单张
- 全部 expectation 都 success 时，final_result 才为 success；任一 failed 则 final_result 必须 failed
- expectation 条目只写 reason/result，不要写 evidence
- 仅在 final_result 条目中给出 evidence：入选依据截图列表（去重），每张写清入选原因
- evidence[].reason 为该图入选原因（20～60字）；可选 elements 为该画面关键元素短词
- 严格输出 JSON 数组，不要用 ``` 包裹，以 [ 开头：
[
  {{
    "expectation":"...",
    "reason":"综合序列判断的理由",
    "think":"...",
    "result":"success"
  }},
  {{
    "final_result":"整体",
    "reason":"整体结论",
    "think":"...",
    "result":"success",
    "evidence":[
      {{"image":"{names[0] if names else 'xxx_gui_poll_1.jpg'}","reason":"已打开优酷并出现搜索结果","elements":["优酷首页","搜索框"]}},
      {{"image":"{names[-1] if names else 'xxx_gui_poll_15.jpg'}","reason":"播放页可见暂停键与进度00:02","elements":["暂停按钮","进度条"]}}
    ]
  }}
]

本批送入模型的截图对照表（图序号 = 下列顺序，必须按此引用文件名）：
{numbered}

期望内容（exceptions）：
{exceptions}
"""

    content: list[dict[str, Any]] = [{"type": "text", "text": prompt}]
    for path in selected:
        # mime hint; jpeg data url works for jpg/png for most gateways
        suffix = Path(path).suffix.lower()
        mime = "image/png" if suffix == ".png" else "image/jpeg"
        content.append(
            {
                "type": "image_url",
                "image_url": {"url": f"data:{mime};base64,{encode_image(path)}"},
            }
        )

    _emit(
        progress_cb,
        "vl_main",
        45,
        f"截图 VL 评判中（{len(selected)}/{len(paths)} 张）",
    )
    try:
        raw_result = chat_vision(
            cfg,
            [{"role": "user", "content": content}],
            temperature=0.0,
            max_tokens=int(cfg.get("max_tokens") or 3072),
            timeout_sec=timeout_sec,
        )
    except JudgeClientError:
        raise

    parsed = parse_json_content(raw_result)
    if isinstance(parsed, list):
        parsed = normalize_gui_poll_evidence_refs(parsed, names)
        parsed = reject_placeholder_success(parsed, exceptions)

    _emit(progress_cb, "vl_done", 95, "截图评判完成")
    return {
        "raw": raw_result,
        "parsed": parsed,
        "images": names,
        "image_count_total": len(paths),
        "image_count_used": len(selected),
    }
