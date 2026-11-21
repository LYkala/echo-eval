import os
import re
import json
import base64


def _get_config_candidates():
    """按优先级返回 VL 模型配置文件路径。"""
    testapp_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    webserver_root = os.path.dirname(os.path.abspath(__file__))
    candidates = []
    env_path = os.environ.get('VL_EVAL_CONFIG', '').strip()
    if env_path:
        candidates.append(env_path)
    candidates.extend([
        os.path.join(testapp_root, 'configs', 'model.yaml'),
        os.path.join(webserver_root, 'configs', 'model.yaml'),
    ])
    seen = set()
    ordered = []
    for path in candidates:
        if path and path not in seen:
            seen.add(path)
            ordered.append(path)
    return ordered


def _load_vl_eval_config():
    """从 configs/model.yaml 加载 vl_eval 模型配置"""
    try:
        import yaml
    except ImportError as e:
        raise ImportError(
            "缺少 PyYAML 依赖，请在 webserverV2 目录执行: pip install -r requirements.txt"
        ) from e

    config_path = None
    for path in _get_config_candidates():
        if os.path.isfile(path):
            config_path = path
            break
    if not config_path:
        paths = ', '.join(_get_config_candidates())
        raise FileNotFoundError(
            f'未找到 VL 模型配置文件，请创建 configs/model.yaml（可参考 configs/model.yaml.example）。'
            f' 已查找: {paths}'
        )

    with open(config_path, 'r', encoding='utf-8') as f:
        config = yaml.safe_load(f) or {}
    models = config.get('models') or {}
    entry = models.get('vl_eval')
    if not entry:
        raise ValueError(f'未在 {config_path} 中找到 models.vl_eval 配置')
    return entry


def _completion_extra_body(model_cfg):
    """OpenAI 兼容网关的 extra_body（如 DeepSeek 关闭思考模式）。"""
    if not isinstance(model_cfg, dict):
        return None
    eb = model_cfg.get('extra_body')
    if isinstance(eb, dict) and eb:
        return eb
    # deepseek-v4.1-flash 默认思考会占满小 max_tokens，导致 content 为空
    mid = str(model_cfg.get('model_id') or '').lower()
    if 'deepseek' in mid:
        return {'thinking': {'type': 'disabled'}}
    return None


def _message_text(message) -> str:
    """取出助手可见文本；content 为空时尝试 reasoning_content（兜底）。"""
    if message is None:
        return ''
    content = getattr(message, 'content', None)
    if content is None and isinstance(message, dict):
        content = message.get('content')
    text = (content or '').strip() if isinstance(content, str) else (str(content or '').strip())
    if text:
        return text
    rc = getattr(message, 'reasoning_content', None)
    if rc is None and isinstance(message, dict):
        rc = message.get('reasoning_content')
    return (rc or '').strip() if isinstance(rc, str) else ''


def _chat_completion(client, model_cfg, messages, *, temperature=0.2, max_tokens=1024):
    """统一 chat.completions 调用，注入 extra_body，返回 (response, text)。"""
    kwargs = {
        'model': model_cfg['model_id'],
        'messages': messages,
        'temperature': temperature,
        'max_tokens': max_tokens,
    }
    eb = _completion_extra_body(model_cfg)
    if eb:
        kwargs['extra_body'] = eb
    response = client.chat.completions.create(**kwargs)
    text = _message_text(response.choices[0].message)
    return response, text


def encode_image(image_path):
    """将图片转为 base64 字符串"""
    with open(image_path, "rb") as f:
        return base64.b64encode(f.read()).decode("utf-8")


def evaluate(image_path, exceptions):
    """
    使用 VL 模型评判任务结果。

    参数:
        image_path: 结果截图路径 (result_final_*.png)
        exceptions: 期望条件列表，如 ["条件1", "条件2", ...]

    返回:
        dict: {"raw": 原始响应字符串, "parsed": 解析后的评判列表}
    """
    try:
        from openai import OpenAI
    except ImportError as e:
        raise ImportError(
            "缺少 openai 依赖，请在 webserverV2 目录执行: pip install -r requirements.txt"
        ) from e

    model_cfg = _load_vl_eval_config()
    timeout_sec = float(model_cfg.get('timeout_sec') or os.environ.get('VL_EVAL_TIMEOUT_SEC') or 120)
    client = OpenAI(
        api_key=model_cfg['api_key'],
        base_url=model_cfg['base_url'],
        timeout=timeout_sec,
        max_retries=0,
    )

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

    _, raw_result = _chat_completion(
        client,
        model_cfg,
        [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": prompt},
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": f"data:image/jpeg;base64,{base64_image}"
                        }
                    }
                ]
            }
        ],
        temperature=0.2,
        max_tokens=int(model_cfg.get('max_tokens') or 1024),
    )

    parsed = None
    try:
        clean = (raw_result or '').strip()
        if clean.startswith("```json"):
            clean = clean[7:]
        elif clean.startswith("```"):
            clean = clean[3:]
        if clean.endswith("```"):
            clean = clean[:-3]
        clean = clean.strip()
        parsed = json.loads(clean)
    except (json.JSONDecodeError, Exception):
        parsed = None

    return {"raw": raw_result, "parsed": parsed}


def _subsample_image_paths(image_paths, max_images=16):
    """过多截图时均匀抽样，始终保留首尾帧。"""
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


def _emit_eval_progress(progress_cb, phase, percent, message=''):
    if not progress_cb:
        return
    try:
        progress_cb(phase=phase, percent=percent, message=message)
    except Exception:
        pass


def evaluate_gui_poll_images(image_paths, exceptions, progress_cb=None):
    """
    用全部（或抽样后的）gui_poll_* 截图做 VL 评判。

    按时间顺序关联多张过程截图，对照 exceptions；全部期望达成才 success。
    progress_cb(phase=..., percent=..., message=...) 可选，用于结果页进度条。
    """
    try:
        from openai import OpenAI
    except ImportError as e:
        raise ImportError(
            "缺少 openai 依赖，请在 webserverV2 目录执行: pip install -r requirements.txt"
        ) from e

    paths = [p for p in (image_paths or []) if p and os.path.isfile(p)]
    if not paths:
        return {'raw': '', 'parsed': None, 'images': []}

    _emit_eval_progress(progress_cb, 'vl_prepare', 35, '准备截图评判')
    selected = _subsample_image_paths(paths, max_images=16)
    model_cfg = _load_vl_eval_config()
    base_timeout = float(
        model_cfg.get('timeout_sec') or os.environ.get('VL_EVAL_TIMEOUT_SEC') or 120
    )
    timeout_sec = min(600.0, base_timeout + 20.0 * len(selected))
    client = OpenAI(
        api_key=model_cfg['api_key'],
        base_url=model_cfg['base_url'],
        timeout=timeout_sec,
        max_retries=0,
    )

    names = [os.path.basename(p) for p in selected]
    numbered = '\n'.join(f'  图{i}: {n}' for i, n in enumerate(names, 1))
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

    content = [{'type': 'text', 'text': prompt}]
    for path in selected:
        content.append({
            'type': 'image_url',
            'image_url': {
                'url': f'data:image/jpeg;base64,{encode_image(path)}',
            },
        })

    _emit_eval_progress(
        progress_cb, 'vl_main', 45,
        f'截图 VL 评判中（{len(selected)}/{len(paths)} 张）',
    )
    _, raw_result = _chat_completion(
        client,
        model_cfg,
        [{'role': 'user', 'content': content}],
        temperature=0.0,
        max_tokens=int(model_cfg.get('max_tokens') or 3072),
    )
    parsed = None
    try:
        clean = (raw_result or '').strip()
        if clean.startswith('```json'):
            clean = clean[7:]
        elif clean.startswith('```'):
            clean = clean[3:]
        if clean.endswith('```'):
            clean = clean[:-3]
        parsed = json.loads(clean.strip())
    except (json.JSONDecodeError, Exception):
        parsed = None
    if isinstance(parsed, list):
        parsed = _normalize_gui_poll_evidence_refs(parsed, names)
        parsed = _reject_placeholder_success(parsed, exceptions)
        path_by_name = {os.path.basename(p): p for p in selected}
        _emit_eval_progress(progress_cb, 'vl_ground', 60, '证据图描述校准中')
        parsed = _ground_evidence_reasons(
            client,
            model_cfg,
            parsed,
            path_by_name,
            progress_cb=progress_cb,
        )
        _emit_eval_progress(progress_cb, 'vl_repair', 88, '证据图对齐校验中')
        parsed = _repair_evidence_image_refs(
            client,
            model_cfg,
            parsed,
            selected,
        )
    _emit_eval_progress(progress_cb, 'vl_done', 95, '截图评判完成')
    return {
        'raw': raw_result,
        'parsed': parsed,
        'images': names,
        'image_count_total': len(paths),
        'image_count_used': len(selected),
    }


def _ground_evidence_reasons(client, model_cfg, parsed, path_by_name, progress_cb=None):
    """对每条 evidence 只看对应单张图重写入选原因，避免多图串帧。"""
    if not isinstance(parsed, list) or not path_by_name:
        return parsed
    # 统计需接地的证据条数，便于进度条细分
    work = []
    for item in parsed:
        if not isinstance(item, dict):
            continue
        evidence = item.get('evidence')
        if not isinstance(evidence, list):
            continue
        for ev in evidence:
            if not isinstance(ev, dict):
                continue
            img_ref = (
                ev.get('image') or ev.get('img') or ev.get('figure') or ev.get('name') or ''
            )
            img_name = os.path.basename(str(img_ref).strip())
            if img_name in path_by_name:
                work.append(img_name)
    total = max(len(work), 1)
    done = 0
    out = []
    for item in parsed:
        if not isinstance(item, dict):
            out.append(item)
            continue
        item = dict(item)
        evidence = item.get('evidence')
        if not isinstance(evidence, list) or not evidence:
            out.append(item)
            continue
        new_ev = []
        for ev in evidence:
            if not isinstance(ev, dict):
                new_ev.append(ev)
                continue
            ev = dict(ev)
            img_ref = (
                ev.get('image') or ev.get('img') or ev.get('figure') or ev.get('name') or ''
            )
            img_name = os.path.basename(str(img_ref).strip())
            path = path_by_name.get(img_name)
            if not path or not os.path.isfile(path):
                new_ev.append(ev)
                continue
            old_reason = str(ev.get('reason') or ev.get('why') or '').strip()
            grounded = _describe_single_evidence_image(
                client, model_cfg, path, img_name, old_reason,
            )
            if grounded:
                ev['reason'] = grounded
                ev['reason_grounded'] = True
                if old_reason and old_reason != grounded:
                    ev['reason_original'] = old_reason
            new_ev.append(ev)
            done += 1
            pct = 60 + int(25 * done / total)
            _emit_eval_progress(
                progress_cb, 'vl_ground', pct,
                f'证据校准 {done}/{total}',
            )
        item['evidence'] = new_ev
        out.append(item)
    return out


def _describe_single_evidence_image(client, model_cfg, image_path, image_name, old_reason):
    """仅根据单张截图写出入选原因。"""
    prompt = f"""只看这一张手机截图（文件名：{image_name}），用中文写 20～60 字，描述画面中实际可见的内容。

规则：
- 只写这张图里能直接看到的 UI/文字/弹窗/页面状态
- 不要猜测前后帧，不要写「已搜索到」「已播放」等图中未出现的结论
- 若下方原入选原因与画面不符，以画面为准重写，不要沿用错误描述
- 只输出原因正文，不要 JSON、不要引号、不要前缀

原入选原因（可能有误，仅供参考）：{old_reason or '（无）'}
"""
    try:
        _, text = _chat_completion(
            client,
            model_cfg,
            [{
                'role': 'user',
                'content': [
                    {'type': 'text', 'text': prompt},
                    {
                        'type': 'image_url',
                        'image_url': {
                            'url': f'data:image/jpeg;base64,{encode_image(image_path)}',
                        },
                    },
                ],
            }],
            temperature=0,
            max_tokens=160,
        )
        text = (text or '').strip('`"\' \n')
        if text.startswith('```'):
            text = text.strip('`')
            if text.startswith('json'):
                text = text[4:].strip()
        # 去掉偶发前缀
        for prefix in ('入选原因：', '原因：', '描述：'):
            if text.startswith(prefix):
                text = text[len(prefix):].strip()
        if 8 <= len(text) <= 120:
            return text
    except Exception:
        return None
    return None


def _image_matches_claim(client, model_cfg, image_path, claim):
    """单图判断是否符合原入选主张。"""
    claim = (claim or '').strip()
    if not claim or not image_path or not os.path.isfile(image_path):
        return False
    prompt = (
        f'这张手机截图是否主要体现以下内容？\n「{claim}」\n'
        '只回答 YES 或 NO，不要其他文字。'
    )
    try:
        _, ans = _chat_completion(
            client,
            model_cfg,
            [{
                'role': 'user',
                'content': [
                    {'type': 'text', 'text': prompt},
                    {
                        'type': 'image_url',
                        'image_url': {
                            'url': f'data:image/jpeg;base64,{encode_image(image_path)}',
                        },
                    },
                ],
            }],
            temperature=0,
            max_tokens=16,
        )
        ans = (ans or '').strip().upper()
        return ans.startswith('Y') or 'YES' in ans
    except Exception:
        return False


def _repair_evidence_image_refs(client, model_cfg, parsed, selected_paths):
    """
    若原入选原因与当前图不符，则在相邻帧中寻找符合原主张的截图并替换。
    """
    if not isinstance(parsed, list) or not selected_paths:
        return parsed
    names = [os.path.basename(p) for p in selected_paths]
    name_to_idx = {n: i for i, n in enumerate(names)}
    out = []
    for item in parsed:
        if not isinstance(item, dict):
            out.append(item)
            continue
        item = dict(item)
        evidence = item.get('evidence')
        if not isinstance(evidence, list) or not evidence:
            out.append(item)
            continue
        new_ev = []
        for ev in evidence:
            if not isinstance(ev, dict):
                new_ev.append(ev)
                continue
            ev = dict(ev)
            claim = str(ev.get('reason_original') or '').strip()
            img_name = os.path.basename(
                str(ev.get('image') or ev.get('img') or '').strip()
            )
            idx = name_to_idx.get(img_name)
            # 仅在「已改写原因」且保留了原文主张时尝试换图
            if not claim or idx is None or not ev.get('reason_grounded'):
                new_ev.append(ev)
                continue
            cur_path = selected_paths[idx]
            if _image_matches_claim(client, model_cfg, cur_path, claim):
                # 当前图其实符合原主张：恢复/保留主张，并再接地一次更贴切的描述
                grounded = _describe_single_evidence_image(
                    client, model_cfg, cur_path, img_name, claim,
                )
                if grounded:
                    ev['reason'] = grounded
                new_ev.append(ev)
                continue
            # 在邻域内找更匹配的帧
            found = None
            for delta in (1, -1, 2, -2, 3, -3):
                j = idx + delta
                if j < 0 or j >= len(selected_paths):
                    continue
                cand = selected_paths[j]
                if _image_matches_claim(client, model_cfg, cand, claim):
                    found = cand
                    break
            if not found:
                new_ev.append(ev)
                continue
            new_name = os.path.basename(found)
            grounded = _describe_single_evidence_image(
                client, model_cfg, found, new_name, claim,
            )
            ev['image'] = new_name
            ev['image_repaired'] = True
            ev['image_original'] = img_name
            if grounded:
                ev['reason'] = grounded
                ev['reason_grounded'] = True
            else:
                ev['reason'] = claim
            new_ev.append(ev)
        item['evidence'] = new_ev
        out.append(item)
    return out


_DEST_PLACEHOLDER_RE = re.compile(
    r'你要去哪儿|请输入目的地|搜索目的地|请输入终点|搜索地点|输入目的地|去哪儿'
)
_DEST_FILLED_RE = re.compile(
    r'已(设置|填入|选定|输入|选择).{0,12}(目的地|终点)|'
    r'(目的地|终点)(已)?(为|是|显示|变成|改成)[^，。；\s]{1,20}|'
    r'[「『"].{0,20}钟楼.{0,20}[」』"]'
)
# 早期帧里的「你要去哪儿」不能否定后面已经画出的路线/导航
_NAV_REACHED_RE = re.compile(
    r'开始导航|导航界面|导航中|路线规划|地图上.{0,8}路线|'
    r'\d+\s*(?:分钟|公里)|预计到达|全程'
)


def _reject_placeholder_success(parsed, exceptions):
    """防止把「你要去哪儿」等占位输入框当成目的地已达成。"""
    if not isinstance(parsed, list):
        return parsed
    exc_blob = ''
    if isinstance(exceptions, (list, tuple)):
        exc_blob = '\n'.join(str(x) for x in exceptions)
    else:
        exc_blob = str(exceptions or '')
    wants_destination = bool(re.search(r'目的地|终点|钟楼', exc_blob))
    out = []
    flipped = False
    for item in parsed:
        if not isinstance(item, dict):
            out.append(item)
            continue
        item = dict(item)
        if str(item.get('result') or '').lower() != 'success':
            out.append(item)
            continue
        parts = [
            str(item.get('expectation') or ''),
            str(item.get('reason') or ''),
            str(item.get('think') or ''),
        ]
        for ev in item.get('evidence') or []:
            if isinstance(ev, dict):
                parts.append(str(ev.get('reason') or ''))
                parts.append(str(ev.get('reason_original') or ''))
                els = ev.get('elements') or []
                if isinstance(els, list):
                    parts.extend(str(x) for x in els)
        blob = '\n'.join(parts)
        if not _DEST_PLACEHOLDER_RE.search(blob):
            out.append(item)
            continue
        # 序列后段已经进入路线/导航：早期占位输入框不再否决
        if _NAV_REACHED_RE.search(blob) or _DEST_FILLED_RE.search(blob):
            out.append(item)
            continue
        # 期望要求目的地，或理由自己拿占位文案当成功证据
        if wants_destination or _DEST_PLACEHOLDER_RE.search(
            str(item.get('reason') or '') + str(item.get('think') or '')
        ):
            if not _DEST_FILLED_RE.search(blob):
                item['result'] = 'failed'
                note = '【系统复核】仍为目的地占位输入（如「你要去哪儿」），未确认具体目的地已设置，改判失败。'
                prev = (item.get('reason') or '').strip()
                item['reason'] = f'{prev} {note}'.strip() if prev else note
                item['placeholder_rejected'] = True
                flipped = True
        out.append(item)
    if flipped:
        for item in out:
            if isinstance(item, dict) and item.get('final_result') is not None:
                item['result'] = 'failed'
                prev = (item.get('reason') or '').strip()
                note = '【系统复核】存在未完成的目的地设置，整体改判失败。'
                item['reason'] = f'{prev} {note}'.strip() if prev else note
    return out


def _normalize_gui_poll_evidence_refs(parsed, names):
    """将 evidence.image 的「图N」解析为对照表文件名，便于前端展示与核对。"""
    name_by_idx = {i: n for i, n in enumerate(names or [], 1)}
    name_set = set(names or [])
    lower_map = {n.lower(): n for n in (names or [])}

    def resolve_one(ref):
        raw = str(ref or '').strip()
        if not raw:
            return raw
        if raw in name_set:
            return raw
        hit = lower_map.get(raw.lower())
        if hit:
            return hit
        m = re.match(r'^图\s*(\d+)$', raw) or re.match(r'^#?\s*(\d+)$', raw)
        if m:
            return name_by_idx.get(int(m.group(1)), raw)
        # 仅写了 basename / gui_poll_N 时尽量精确匹配
        for n in names or []:
            if n.endswith(raw) or raw in n:
                # 避免 gui_poll_1 误匹配 gui_poll_10
                m_poll = re.search(r'gui_poll_(\d+)', raw, re.I)
                m_name = re.search(r'gui_poll_(\d+)', n, re.I)
                if m_poll and m_name and m_poll.group(1) != m_name.group(1):
                    continue
                return n
        return raw

    out = []
    for item in parsed:
        if not isinstance(item, dict):
            out.append(item)
            continue
        item = dict(item)
        evidence = item.get('evidence')
        if isinstance(evidence, list) and evidence:
            new_ev = []
            for ev in evidence:
                if isinstance(ev, str):
                    new_ev.append(resolve_one(ev))
                    continue
                if isinstance(ev, dict):
                    ev = dict(ev)
                    for key in ('image', 'img', 'figure', 'name'):
                        if key in ev and ev[key]:
                            ev[key] = resolve_one(ev[key])
                            break
                    new_ev.append(ev)
                    continue
                new_ev.append(ev)
            item['evidence'] = new_ev
        out.append(item)
    return out


def evaluate_reply_and_image(image_path, exceptions, replies=None, user_messages=None):
    """
    豆包多模态：结合模型回复文本 + 最终截图 + exceptions 一起评判。

    replies: [{"round":1,"reply_text":"..."}, ...] 或纯字符串列表
    user_messages: 可选，同轮用户语料
    """
    try:
        from openai import OpenAI
    except ImportError as e:
        raise ImportError(
            "缺少 openai 依赖，请在 webserverV2 目录执行: pip install -r requirements.txt"
        ) from e

    model_cfg = _load_vl_eval_config()
    timeout_sec = float(model_cfg.get('timeout_sec') or os.environ.get('VL_EVAL_TIMEOUT_SEC') or 120)
    client = OpenAI(
        api_key=model_cfg['api_key'],
        base_url=model_cfg['base_url'],
        timeout=timeout_sec,
        max_retries=0,
    )

    reply_blocks = []
    for i, item in enumerate(replies or [], 1):
        if isinstance(item, dict):
            rnd = item.get('round') or i
            text = (item.get('reply_text') or item.get('actual') or item.get('text') or '').strip()
            um = (item.get('user_message') or '')
            if user_messages and not um and i - 1 < len(user_messages):
                um = user_messages[i - 1] or ''
        else:
            rnd = i
            text = str(item or '').strip()
            um = (user_messages[i - 1] if user_messages and i - 1 < len(user_messages) else '') or ''
        reply_blocks.append(
            f"轮次{rnd}:\n  用户提问: {um}\n  模型回答: {text or '(空)'}"
        )
    replies_text = '\n\n'.join(reply_blocks) if reply_blocks else '(无模型回答)'

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

    _, raw_result = _chat_completion(
        client,
        model_cfg,
        [
            {
                'role': 'user',
                'content': [
                    {'type': 'text', 'text': prompt},
                    {
                        'type': 'image_url',
                        'image_url': {'url': f'data:image/jpeg;base64,{base64_image}'},
                    },
                ],
            }
        ],
        temperature=0.2,
        max_tokens=int(model_cfg.get('max_tokens') or 1024),
    )
    parsed = None
    try:
        clean = (raw_result or '').strip()
        if clean.startswith('```json'):
            clean = clean[7:]
        elif clean.startswith('```'):
            clean = clean[3:]
        if clean.endswith('```'):
            clean = clean[:-3]
        parsed = json.loads(clean.strip())
    except (json.JSONDecodeError, Exception):
        parsed = None
    return {'raw': raw_result, 'parsed': parsed}


def evaluate_qa_rounds(rounds):
    """
    对多轮 QA 文本按步骤评判（每轮有预期才评判）。

    rounds: [
      {"round": 1, "user_message": "...", "expected": "...", "actual": "..."},
      ...
    ]
    返回: {"raw": str, "parsed": list}，parsed 含每轮 result + final_result
    """
    try:
        from openai import OpenAI
    except ImportError as e:
        raise ImportError(
            "缺少 openai 依赖，请在 webserverV2 目录执行: pip install -r requirements.txt"
        ) from e

    to_judge = [
        r for r in (rounds or [])
        if (r.get('expected') or '').strip()
    ]
    if not to_judge:
        return {
            'raw': '[]',
            'parsed': [{
                'final_result': '无待评判轮次',
                'think': '无预期',
                'result': 'success',
            }],
        }

    model_cfg = _load_vl_eval_config()
    timeout_sec = float(model_cfg.get('timeout_sec') or os.environ.get('VL_EVAL_TIMEOUT_SEC') or 120)
    client = OpenAI(
        api_key=model_cfg['api_key'],
        base_url=model_cfg['base_url'],
        timeout=timeout_sec,
        max_retries=0,
    )

    rounds_text = []
    for r in to_judge:
        rounds_text.append(
            f"轮次{r.get('round')}:\n"
            f"  用户提问: {r.get('user_message') or ''}\n"
            f"  预期回答: {r.get('expected') or ''}\n"
            f"  模型回答: {r.get('actual') or '(空)'}"
        )
    joined = '\n\n'.join(rounds_text)

    prompt = f"""
你是一个严格的多轮对话评判员。请按轮次判断「模型回答」是否满足「预期回答」。
仅评判给出了预期回答的轮次；每一轮独立判断。
全部待评判轮次都 success 时，最终 final_result 才为 success。

说明：
- 若预期写成「图片中是否… / 界面是否…」等截图表述，请根据模型回答内容推断任务是否已达成该条件（例如模型明确说已打开打车/导航、已确认行程等可判 success；仅道歉失败/未完成则判 failed）。
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

    _, raw_result = _chat_completion(
        client,
        model_cfg,
        [{'role': 'user', 'content': prompt}],
        temperature=0.2,
        max_tokens=int(model_cfg.get('max_tokens') or 1024),
    )
    parsed = None
    try:
        clean = (raw_result or '').strip()
        if clean.startswith('```json'):
            clean = clean[7:]
        elif clean.startswith('```'):
            clean = clean[3:]
        if clean.endswith('```'):
            clean = clean[:-3]
        parsed = json.loads(clean.strip())
    except (json.JSONDecodeError, Exception):
        parsed = None
    # 补全缺失的 round，保证结果页按轮展示「通过/未通过」
    if isinstance(parsed, list):
        judged_rounds = [r.get('round') for r in to_judge if r.get('round') is not None]
        step_idx = 0
        for item in parsed:
            if not isinstance(item, dict) or item.get('final_result') is not None:
                continue
            if item.get('round') is None and step_idx < len(judged_rounds):
                item['round'] = judged_rounds[step_idx]
            step_idx += 1
    return {'raw': raw_result, 'parsed': parsed}


if __name__ == "__main__":
    result = evaluate(
        image_path="img.png",
        exceptions=[
            "图中包含一个布娃娃",
            "布娃娃穿着红色帽子",
            "带着蝴蝶结",
            "有着蓝色大眼睛"
        ]
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))
