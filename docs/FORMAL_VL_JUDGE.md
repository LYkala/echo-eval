# 正式评测阶段：大模型评判方法

本文**只描述正式测试**（语音播报 + 录屏完成后）如何用视觉大模型（VL）判定用例是否通过。  
不含预置条件校准中的 Act / Verify；预置与正式的对照见 [EVALUATION_METHODS.md](./EVALUATION_METHODS.md)。

实现位置：`src/echo_eval/judge/`（对齐 TestAppGUI `evaluate_gui_poll_images`，E1）。

---

## 1. 评判时机与输入

正式评判在设备侧流程结束后进行：

```text
预置回放完成 → TTS + 录屏 → pull → 抽帧到 frames/
                                    ↓
                         VL / mock 多帧评判
                                    ↓
                         evaluation.json + report.md
```

| 输入 | 说明 |
|------|------|
| 截图序列 | 优先 `frames/frame_*.jpg`（或目录下其它图片），按文件名排序 |
| 期望 `exceptions` | 来自用例表「预期结果」列（多行 → 多条字符串） |
| 模型配置 | `configs/model.yaml` → `models.vl_eval`（可用 `VL_EVAL_CONFIG` 覆盖路径） |

帧数较多时会**抽样**：保留首帧、尾帧，中间均匀取样，合计最多约 **16** 张，再送入模型。

超时：在配置 `timeout` / `timeout_sec` 基础上，按选用图片数适当加长（有上限）。

---

## 2. 评判目标

模型扮演**严格的 GUI 任务评判员**，结合**全部**送入的截图，对照每条 expectation（即 `exceptions`），判断语音助手任务是否达成。

原则（写入提示词）：

- **从严**：不确定、证据不足、只完成一半流程 → `failed`
- 每条 expectation **独立**判断，综合整段序列而非单张
- **全部** expectation 为 `success` 时，`final_result` 才可为 `success`；任一 `failed` 则整体 `failed`

本地在模型返回后再做：

1. 证据图文件名规范化（`normalize_gui_poll_evidence_refs`）
2. 占位成功拒绝等规则（`reject_placeholder_success`，减少「空输入框 / 占位文案」被当成已达成）
3. 解析 `passed`（`parse_evaluation_passed`）写入 `evaluation.json`

---

## 3. 调用方式

- **消息形态**：单条 `user`，`content` = `[文本提示词] + [多张 image_url(base64)]`
- **温度**：`0.0`
- **max_tokens**：配置项或默认 `3072`
- **CLI**：`python -m echo_eval judge --run-dir ... --expect ... --backend vl`  
  端到端：`python -m echo_eval run --excel ... --backend vl`（抽帧后自动调用）

`--backend mock` 不调大模型：有图且有期望则直接判过，仅用于联调。

---

## 4. 提示词（完整模板）

以下为代码中拼接的用户提示词主体（`gui_poll.py`）。花括号内为运行时填入：截图张数、对照表示例文件名、`exceptions` 原文等。

```text
你是一个严格的 GUI 任务评判员。下面按时间顺序给出任务执行过程中的 gui_poll 截图
（共提供 {N} 张，标记为 图1…图{N}；若有抽样会保留首尾与中间关键帧）。
请结合**全部**截图，对照 exceptions 判断任务是否达成预期。
默认从严：不确定、证据不足、只完成一半流程时，一律判 failed。

重要（证据图必须与画面一致）：
- 「图N」只表示本批列表中的第 N 张（见下方对照表），与文件名里的 gui_poll_数字 不是同一套编号，禁止混用
- evidence[].image 必须写对照表中的完整文件名（例如 {示例文件名}），不要只写「图N」或「gui_poll_N」
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
  {
    "expectation":"...",
    "reason":"综合序列判断的理由",
    "think":"...",
    "result":"success"
  },
  {
    "final_result":"整体",
    "reason":"整体结论",
    "think":"...",
    "result":"success",
    "evidence":[
      {"image":"{首帧文件名}","reason":"已打开优酷并出现搜索结果","elements":["优酷首页","搜索框"]},
      {"image":"{末帧文件名}","reason":"播放页可见暂停键与进度00:02","elements":["暂停按钮","进度条"]}
    ]
  }
]

本批送入模型的截图对照表（图序号 = 下列顺序，必须按此引用文件名）：
  图1: frame_0001.jpg
  图2: ...
  …

期望内容（exceptions）：
{用例预期结果列表}
```

提示词之后，按对照表顺序附加每张图的 `data:image/jpeg|png;base64,...`。

---

## 5. 期望的模型输出结构

JSON **数组**（不要 markdown 代码围栏），典型两项结构：

1. **逐条 expectation**（可多条）  
   - `expectation` / `reason` / `think` / `result`（`success` | `failed`）  
   - **不要**在这些条目里写 `evidence`

2. **一条 `final_result`**  
   - `final_result`、`reason`、`think`、`result`  
   - `evidence[]`：`image`（完整文件名）、`reason`、可选 `elements`

本地根据 `result_parsed` 计算 `passed`（见 `parse_evaluation_passed`）：各步 / 轮次需为 `success`，且最终结论一致时整体通过。

---

## 6. 产物字段（evaluation.json 摘要）

| 字段 | 含义 |
|------|------|
| `mode` | `gui_poll_images` |
| `backend` / `source` | `vl` / `vl_eval_e1` 或 mock |
| `exceptions` | 送入的期望列表 |
| `images` | 实际送模的文件名列表 |
| `image_count_total` / `image_count_used` | 抽帧总数 / 送模张数 |
| `result_raw` | 模型原始文本 |
| `result_parsed` | 解析后的 JSON 数组 |
| `passed` | 最终是否通过（布尔） |

同目录 `report.md` 含后端与结论摘要，便于人工浏览。

---

## 7. 相关文档与代码

| 路径 | 说明 |
|------|------|
| [JUDGE.md](./JUDGE.md) | 评判器用法与 mock 约定 |
| [EVALUATION_METHODS.md](./EVALUATION_METHODS.md) | 预置 vs 正式总览 |
| `src/echo_eval/judge/gui_poll.py` | 提示词与 VL 调用 |
| `src/echo_eval/judge/schema.py` | 解析 `passed`、占位拒绝 |
| `src/echo_eval/judge/service.py` | 收集帧、写 `evaluation.json` |
