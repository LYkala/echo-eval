# 语音助手无线 ADB 自动化评测 — 开发计划

> 目标：通过无线 ADB 连接语音助手设备，自动播放语音、ADB 录屏，再调用多模态大模型依据「输入指令 + 录屏证据」评判效果；后续可扩展为数据集驱动的 TTS → 播放 → 评测流水线。

> 参考源码：已导入 `vendor/testApp/`。评判对照见 [`docs/TESTAPP_JUDGE_MAPPING.md`](TESTAPP_JUDGE_MAPPING.md)。  
> 核心对齐文件：`webserverV2/llm_eval.py`（尤其 `evaluate_gui_poll_images`）、`local_adb_runner/case_eval.py`。

---

## 1. 问题与范围

### 1.1 要解决什么

| 环节 | 说明 |
|------|------|
| 连接 | 无线 ADB（`adb connect host:port`），稳定会话与断线重连 |
| 刺激 | 本机/远端播放语音，唤醒并驱动语音助手 |
| 观测 | `adb shell screenrecord`（或截帧序列）采集 UI 响应过程 |
| 评判 | 将「用例期望 + 语音文本/音频元数据 + 录屏/关键帧」交给 VLM/LLM，输出结构化 Pass/Fail |
| 扩展 | 用例从单条脚本 → 数据集（文本 → TTS → 播放 → 评测） |

### 1.2 非目标（首期不做）

- 不侵入设备 ROM / 不 Hook 助手内部 API（默认纯黑盒：音频进 + 画面出）
- 不做云真机平台、不做完整 IDE
- 不做多设备并发调度（接口预留，实现单设备优先）
- 不把「操作 Agent」（自动点 UI）作为首期范围；首期是 **Oracle（评判器）+ 录制流水线**

### 1.3 成功标准（MVP）

1. 一条命令完成：连接 → 播放一段固定 wav → 录屏 N 秒 → 抽帧 → LLM 评判 → 写出 JSON 报告  
2. 评判结果对齐 TestApp：`result_parsed`（逐条 expectation + `final_result`）、`passed`；对外可映射为 pass/fail；`evidence[]` 引用抽帧文件名  
3. 无大模型 Key 时可用 mock judge 跑通全链路  
4. 新增一条用例只需改 YAML/JSON，不改 Python 主流程  

---

## 2. 总体架构

```
┌─────────────────────────────────────────────────────────────┐
│                     Orchestrator (runner)                   │
│  load case → connect → record.start → play → wait → stop    │
│              → extract frames → judge → persist report      │
└───────────┬─────────────┬──────────────┬──────────┬─────────┘
            │             │              │          │
     ┌──────▼──────┐ ┌────▼─────┐ ┌──────▼─────┐ ┌─▼──────────┐
     │ device/adb  │ │ stimulus │ │ capture/   │ │ judge/     │
     │ wireless    │ │ audio    │ │ screenrec  │ │ llm oracle │
     │ session     │ │ player   │ │ + frames   │ │ (+ mock)   │
     └─────────────┘ └──────────┘ └────────────┘ └────────────┘
            │                                         ▲
            │              artifacts/                 │
            └──── video / frames / logs / report ─────┘
```

### 2.1 模块职责

| 模块 | 职责 | 首期交付 |
|------|------|----------|
| `device/` | ADB 无线连接、设备健康检查、shell 封装 | 连接/重连/超时 |
| `stimulus/` | 播放本地音频；预留 TTS 接口 | 本地 wav 播放 |
| `capture/` | 启停 `screenrecord`、pull 视频、抽帧 | mp4 + 均匀抽帧 |
| `judge/` | 组装 prompt、调用 VLM、解析结构化结果 | OpenAI 兼容 API + mock |
| `cases/` | 用例 schema、期望描述、超时与窗口 | YAML 样例 |
| `runner/` | 编排、产物目录、CLI | `python -m runner run --case ...` |
| `report/` | JSON/Markdown 报告 | 单用例报告 |

---

## 3. 与 TestAppGUI「评判效果」的对齐方式（已对照源码）

详细对照：[`TESTAPP_JUDGE_MAPPING.md`](TESTAPP_JUDGE_MAPPING.md)。

| TestAppGUI | echo-eval 落点 | 说明 |
|------------|----------------|------|
| `exceptions` | `cases/*.yaml` → `expect` 导出为字符串列表 | 兼容拼写 `expections` |
| `gui_poll_*` 多帧 | `capture/` 录屏抽帧 | **主评判路径** = `evaluate_gui_poll_images` |
| `evaluate_reply_and_image` | 可选：助手回复文本 + 末帧 | local_adb 默认模式 |
| `evaluation.json` | `artifacts/.../judgment.json` | 保留 `result_parsed` / `passed` |
| `models.vl_eval` | `configs/model.yaml` | OpenAI 兼容；密钥不进仓 |
| `parse_evaluation_passed` | `judge/schema.py` | 二态 success/failed，从严 |

**接入步骤（下一步实现）：**

1. 薄封装移植 `evaluate_gui_poll_images`（先不做 evidence ground/repair）  
2. 移植 `parse_evaluation_passed` 与 JSON fence 清洗  
3. screenrecord → 抽帧文件名对齐 evidence 引用规则  
4. MockJudge 产出同结构 `evaluation.json`，便于无 Key CI  

---

## 4. 核心数据契约

### 4.1 用例（Case）

```yaml
id: wake_and_weather
name: 唤醒后查询天气
device:
  serial: "192.168.1.20:5555"   # 无线 adb
stimulus:
  type: audio_file              # 后续扩展: tts_text
  path: assets/audio/wake_weather.wav
  play_device: host             # host | android
capture:
  duration_sec: 15
  start_offset_sec: 0           # 播放前先开录
  fps_sample: 1                 # 每秒抽 1 帧给 VLM
expect:
  goal: "助手被唤醒并展示或播报天气相关结果"
  must_see: ["天气", "℃"]       # 软约束，交给模型；可选 OCR 预检
  must_not_see: ["网络错误", "无法连接"]
  timeout_sec: 12
judge:
  model: gpt-4.1                # 或本地/兼容端点
  max_frames: 8
```

### 4.2 评判结果（Judgment）

```json
{
  "case_id": "wake_and_weather",
  "verdict": "pass",
  "score": 0.86,
  "confidence": 0.8,
  "reasons": ["第 4–6 秒画面出现天气卡片且含温度单位"],
  "evidence": [
    {"t_sec": 4.0, "frame": "frames/0004.jpg", "note": "天气 UI 出现"}
  ],
  "raw_model_output": "...",
  "meta": {"model": "...", "latency_ms": 3200}
}
```

与 TestApp 对齐为二态：`success` | `failed`（证据不足判 failed）。对外报告可映射为 `pass` / `fail`。

---

## 5. 分阶段开发计划

### Phase 0 — 环境与骨架（打底）

**产出**

- Python 包结构、`pyproject.toml`、CLI 入口  
- 配置：`ADB_HOST`、`JUDGE_API_BASE`、`JUDGE_API_KEY`（本地可 mock）  
- 目录约定：`cases/`、`assets/`、`artifacts/`、`docs/`  

**验收**

- `python -m runner doctor` 能检查 adb 是否在 PATH、配置是否齐全  

---

### Phase 1 — 无线 ADB 与录屏闭环

**任务**

1. `adb connect` / `adb devices` 封装；失败重试与会话保活  
2. `screenrecord` 启停（注意设备最长录制时长，必要时分段）  
3. `adb pull` 视频到 `artifacts/<run_id>/screen.mp4`  
4. ffmpeg 抽帧（或 `adb exec-out screencap` 定时截图作为降级方案）  

**验收**

- 无语音也能：连接 → 录 5 秒 → pull → 得到 ≥3 张帧  

**风险与对策**

| 风险 | 对策 |
|------|------|
| 无线 ADB 不稳 | 心跳 `adb shell echo`；失败自动 reconnect |
| 部分设备无 `screenrecord` | 降级为定时 `screencap` |
| 录制权限/麦克风无关 UI | 本阶段只保证画面；音频另议 |

---

### Phase 2 — 语音刺激（播放）

**任务**

1. Host 侧播放：`sounddevice` / `ffplay` / `pygame` 播放 wav 到外放，设备麦克拾音（最简黑盒）  
2. 可选：`adb push` + `am`/`media` 在设备上播放（若助手读本机音频通道）  
3. 与录屏时间轴对齐：`t0=record_start`，记录 `play_started_at`  

**验收**

- 人工听感：设备能被唤醒；产物目录有 `timeline.json`  

**后续扩展点（先定义接口，后实现）**

```python
class Stimulus(Protocol):
    def prepare(self, case: Case) -> PreparedAudio: ...
    def play(self, prepared: PreparedAudio) -> PlayEvent: ...
```

- `AudioFileStimulus`（本期）  
- `TtsStimulus(text, voice)`（下期：数据集文本 → TTS → wav → 同上）  

---

### Phase 3 — LLM 评判器（对齐 TestApp `llm_eval`）

**任务**

1. 从 `vendor/testApp/webserverV2/llm_eval.py` 移植：  
   - `_subsample_image_paths`（最多 16，保首尾）  
   - `evaluate_gui_poll_images` 的 prompt 与 JSON 解析  
   - （可选二期）`_ground_evidence_reasons` / `_repair_evidence_image_refs`  
2. 移植 `case_eval.parse_evaluation_passed`  
3. 配置加载对齐 `models.vl_eval`（`VL_EVAL_CONFIG`）  
4. 实现 `MockJudge` 产出同结构 `result_parsed`  
5. 适配器：YAML `expect` → `exceptions: string[]`  

**Prompt 原则（与 TestApp 一致）**

- 严格 GUI 评判员；证据不足 → failed（无 uncertain）  
- 逐条 expectation；全部 success 才 `final_result=success`  
- evidence 必须引用真实帧文件名，禁止串帧  

**验收**

- 用固定帧夹具对比：本项目输出与直接调用 vendor `llm_eval` 的 `passed` 一致  
- 金标成功/失败样例人工一致  

---

### Phase 4 — 编排与报告

**任务**

1. `runner run --case cases/xxx.yaml` 端到端  
2. 产物布局：

```
artifacts/20261121_141200_wake_and_weather/
  case.yaml
  timeline.json
  screen.mp4
  frames/
  judgment.json
  report.md
```

3. 退出码：pass=0，fail=1，uncertain/error=2（便于 CI）  

**验收**

- 单条用例可重复跑；报告可人工抽查  

---

### Phase 5 — 数据集与 TTS 扩展

**任务**

1. `dataset/*.jsonl`：`id, text, expect, tags`  
2. TTS 适配器（Azure / 本地 CosyVoice / edge-tts 等）生成 wav  
3. 批量 runner：`runner batch --dataset ... --limit 50`  
4. 汇总：通过率、失败聚类（按 reasons 关键词）、成本统计  

**验收**

- 10 条文本用例无人值守跑完并生成 summary.csv  

---

## 6. 技术选型建议

| 层级 | 建议 | 理由 |
|------|------|------|
| 语言 | Python 3.11+ | ADB/音视频/LLM SDK 生态成熟 |
| CLI | Typer 或 argparse | 轻量 |
| ADB | 官方 `adb` CLI 封装（subprocess） | 无线调试无需重依赖；必要时再上 pure-python-adb |
| 视频 | ffmpeg | 抽帧稳定 |
| 配置 | pydantic + YAML | Case/Judgment 强类型 |
| LLM | OpenAI 兼容 HTTP | 便于切换供应商；视觉模型必选 |
| 测试 | pytest + mock judge + 固定帧夹具 | 无真机也能测 judge |

首期 **不做** Web UI；需要可视化时再加简单本地报告页。

---

## 7. 目录结构（建议）

```
.
├── README.md
├── docs/
│   └── DEVELOPMENT_PLAN.md          # 本文档
├── pyproject.toml
├── src/voice_eval/
│   ├── __init__.py
│   ├── device/adb.py
│   ├── stimulus/audio.py
│   ├── stimulus/tts.py              # Phase 5
│   ├── capture/screenrecord.py
│   ├── capture/frames.py
│   ├── judge/protocol.py
│   ├── judge/schema.py
│   ├── judge/prompts/oracle_v1.md
│   ├── judge/openai_compat.py
│   ├── judge/mock.py
│   ├── cases/loader.py
│   ├── report/writer.py
│   └── runner/cli.py
├── cases/
│   └── examples/wake_and_weather.yaml
├── assets/audio/
├── tests/
└── artifacts/                       # gitignore
```

---

## 8. 关键时序（单次用例）

```
t0  connect + healthcheck
t1  screenrecord start
t2  play audio (record play_started_at)
t3  wait expect.timeout_sec（或智能：UI 稳定后提前停）
t4  screenrecord stop + pull
t5  sample frames (均匀 / 场景变化检测)
t6  judge.evaluate(case, frames, timeline)
t7  write judgment.json + report.md
```

可选优化：`t3` 用截图 OCR/模板匹配提前结束，缩短录制与评测成本。

---

## 9. 评判器详细设计（本期核心）

### 9.1 接口

```python
class JudgeProtocol(Protocol):
    def evaluate(
        self,
        case: Case,
        frames: list[FrameRef],   # path + t_sec
        timeline: Timeline,
    ) -> Judgment: ...
```

### 9.2 输入打包

1. 系统提示：QA Oracle 规则  
2. 用户内容：  
   - 用例 `goal` / `must_see` / `must_not_see`  
   - 刺激摘要（播放文本或音频路径哈希）  
   - 按时间排序的图像（vision parts）  
3. 要求模型只输出 JSON（匹配 schema）  

### 9.3 后处理

1. JSON 解析 → Schema 校验  
2. 失败则 `repair` 一次（附带校验错误）  
3. 仍失败 → `verdict=uncertain`，保留 raw 输出  
4. 可选：与 OCR 预检冲突时降置信并标记  

### 9.4 与参考仓对接 checklist

- [ ] 复制/移植 prompt 模板与评分维度  
- [ ] 对齐输出字段名（或写 `adapters/testapp_gui.py` 做字段映射）  
- [ ] 用参考仓样例输入跑一轮，对比人工标注  
- [ ] 将差异记入 `docs/oracle_diff.md`  

---

## 10. 风险清单

| 风险 | 影响 | 缓解 |
|------|------|------|
| 私有参考仓不可读 | 评判细节可能偏差 | 先定稳定 Protocol；拿到代码后只换 judge 实现 |
| 外放拾音环境噪声 | 唤醒失败 | 固定音量/近场；或改设备侧播放路径 |
| VLM 幻觉 | 假 pass | uncertain 态 + evidence 强制引用；抽检集 |
| 录屏无声 | 无法听助手播报 | 首期以 UI 文字/卡片为准；后期补内部音频录制或 logcat |
| 无线 ADB 掉线 | 流水线中断 | 重连、用例级重试、产物不覆盖 |

---

## 11. 里程碑与交付物

| 里程碑 | 交付物 |
|--------|--------|
| M0 | 仓库骨架、本计划、doctor 命令 |
| M1 | 无线 ADB + 录屏抽帧可跑 |
| M2 | 音频播放 + timeline 对齐 |
| M3 | LLM/Mock Judge + schema 报告（**对齐参考仓评判**） |
| M4 | 端到端单用例 CLI |
| M5 | 数据集 + TTS 批量评测 |

---

## 12. 立即下一步（建议执行顺序）

1. ~~导入 testApp 并完成对照~~ → 见 `docs/TESTAPP_JUDGE_MAPPING.md`  
2. 落地 Phase 0–1：adb + screenrecord + 抽帧  
3. 薄封装移植 `evaluate_gui_poll_images` + `parse_evaluation_passed`；MockJudge + pytest  
4. 接通 `vl_eval`，用固定帧对比 vendor 直调与本项目 `passed`  
5. 再接 Phase 2 语音与 Phase 5 数据集  
6. **安全**：轮换曾写入 git 历史的 VL API Key（已脱敏工作区文件）  

---

## 13. 附录：无线 ADB 最小操作备忘

```bash
# 设备曾用 USB 开过 tcpip
adb tcpip 5555
adb connect <DEVICE_IP>:5555
adb devices
adb -s <DEVICE_IP>:5555 shell screenrecord --time-limit 15 /sdcard/screen.mp4
adb -s <DEVICE_IP>:5555 pull /sdcard/screen.mp4 ./screen.mp4
```

播放侧（host）示例：

```bash
ffplay -autoexit -nodisp assets/audio/wake_weather.wav
# 或
python -c "import sounddevice as sd, soundfile as sf; d,r=sf.read('a.wav'); sd.play(d,r); sd.wait()"
```
