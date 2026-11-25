# 模型评测方法说明

本文说明 echo-eval 中两套与**大模型 / 视觉模型（VL）**相关的评判机制：

1. **预置条件**：保证正式语音测试开始前，设备界面已到达用例要求的起点。  
2. **正式测试**：对录屏抽帧后，对照「预期结果」判定语音助手任务是否达成。

两者共用 `configs/model.yaml`（`models.vl_eval`），但**输入、输出、判定字段、调用时机**不同，不可混用。

更细的实现与 CLI 见 [JUDGE.md](./JUDGE.md)、[PHASE4_RUN.md](./PHASE4_RUN.md)、[CASE_TIMELINE.md](./CASE_TIMELINE.md)。  
ASR 准确率（与 GUI 成功率并行）见 [ASR_JUDGE.md](./ASR_JUDGE.md)。

---

## 1. 总览对比

| 维度 | 预置条件评判 | 正式测试评判 | ASR 准确率（P1） |
|------|--------------|--------------|------------------|
| 目的 | 单步 UI 是否到位（可缓存 ADB） | 整段语音任务是否满足预期 | 屏上识别文是否与播报文案一致 |
| 入口 | `precondition-calibrate` / 覆盖检查 | `run --excel`（抽帧后自动评判） | 同 `run`，句后截屏 |
| 视觉输入 | 通常 1～2 张**实时截屏**（before / after） | 录屏抽帧后的**多帧**序列（最多约 16 张） | 句后 0.5s 间隔截屏（≤约 8 张） |
| 文本依据 | 当前预置步骤文案 + 前缀路径 | 用例「预期结果」解析出的 `exceptions` | 该句播报文案；忽略固定提示 |
| 模型角色 | Act：提议点击坐标；Verify：是否达成 | GUI Poll：对照预期判 success/failed | 读底部气泡，判是否一致 |
| 成功字段 | Verify：`achieved: true/false` | `evaluation.json` → `passed` | `asr_summary.json`（不影响 GUI passed） |
| 无 VL 时 | 系统类步骤走确定性 ADB；GUI 需校准 | `--backend mock` 可联调 | `--backend mock` 可联调 |
| 产物 | `artifacts/precondition_cache/steps.json` + shots | `evaluation.json` + `report.md` | `asr_frames/` + `asr/` + `asr_summary.json` |

时间关系（单条用例）：

```text
覆盖检查（可选，多数不调 VL）
    ↓
预置回放 / 校准（GUI 步才调 VL Act+Verify）
    ↓
TTS 播报 + 句后 ASR 截屏 + 录屏 + pull
    ↓
抽帧 → GUI VL 评判 ‖ ASR VL 评判（互不影响）
```

---

## 2. 预置条件相关评判

预置条件分三层，只有 **GUI 点击类校准** 会调用 VL 做「提案 + 验收」；覆盖检查与确定性步骤一般**不调** VL。

### 2.1 覆盖检查（开跑前 / 前端「查看覆盖情况」）

**是否调用模型：否。**

依据本地缓存 `artifacts/precondition_cache/steps.json` 与确定性规则，判断每条用例预置是否 **ready**：

| 步骤状态 | 是否 ready | 说明 |
|----------|------------|------|
| `passed` 且 `adb_commands` 非空 | 是 | 正式 run 将回放这些命令 |
| `passed` 但命令为空（非确定性） | 否 | 与 run 对齐，避免「检查绿、开跑跳过」 |
| 确定性系统步（音量/亮度/静音/开应用/回桌面等） | 是 | 无缓存也可直接执行 |
| `failed` / `stale` / GUI 无缓存 | 否 | 需先 `precondition-calibrate` |

未 ready 的用例在 `run` 时跳过（日志含具体步骤与原因）。

### 2.2 确定性步骤（不调 VL）

文案可映射为系统 ADB 时，**不截屏、不 VL**，例如：

- `打开爱奇艺` / `打开设置` → `launch_package` / `am start`
- `音量调到50%` / `静音` → `cmd media_session volume …`（可有 `media …` 兜底，**任一成功即可**）
- `亮度调到50%`、`回桌面` 等

成功后写入 cache（`kind=launch|volume|mute|…`）。手改过的 `adb_commands` 在 `passed` 时优先原样回放，不再被 `launch_package` 覆盖。

### 2.3 GUI 步骤 VL 校准（Act + Verify）

针对「点击筛选」「点击显示」等无法用固定系统命令完成的步骤。

#### 流程（每步最多 `max_retries` 轮，默认 3）

```text
截屏 before
    → VL-Act：根据画面给出白名单 input 命令
    → 执行 adb（仅允许 input tap / swipe / keyevent）
    → 等待该步 settle（step_settle_sec 或 overrides）
    → 截屏 after
    → VL-Verify：对比前后画面，输出 achieved
    → 成功则写入 cache；失败则带反馈进入下一轮
```

通过后**不再**额外 sleep 一轮（避免与 Verify 前等待重复）。

#### VL-Act（提议命令）

- **角色**：Android UI 自动化助手。  
- **输入**：
  - 当前 `before` 截图（必选）
  - 屏幕尺寸、已完成前缀、当前步骤文案
  - 重试时：上一轮失败 `reason`、上一轮 `adb_commands`；以及**至多一张**上一轮操作时截图（效率优先，不堆完整历史多图）
- **输出 JSON**：

```json
{
  "adb_commands": ["input tap 100 200"],
  "reason": "简短中文说明"
}
```

- **约束**：只允许 `input tap` / `input swipe` / `input keyevent`；其它命令拒绝执行。

#### VL-Verify（验收是否达成）

- **角色**：Android UI 测试验收助手。  
- **输入**：操作后截图（必选）；可选操作前截图便于对比；步骤文案与前缀。  
- **输出 JSON**：

```json
{
  "achieved": true,
  "reason": "简短中文说明"
}
```

- **判定**：`achieved == true` → 该预置步 `passed` 并缓存命令；否则本轮失败，把 `reason` 作为下一轮 Act 的反馈。

#### 校准范围（CLI）

| 模式 | 行为 |
|------|------|
| 默认 | 只处理「预置未全部 ready」的用例；已 pass 前缀**回放 ADB** 铺界面，再校准未通过步 |
| `--retry-failed` | 重试 cache 为 `failed` 的步 |
| `--force` | 已 pass 也重训（并处理全部相关用例） |

落盘：`shots/<key>/tryN_before.png`、`tryN_after.png`、`tryN_act.json`、`tryN_verify.json`。

### 2.4 正式 run 中的预置

默认 `verify_after_replay: false`：**不再**对已 pass 步做 VL 复验，只回放 ADB（+ settle）。  
若回放失败（如命令在设备上不存在且无成功兜底），用例跳过，原因写在日志 / 进度文案中。

---

## 3. 正式测试评判

正式评判发生在**预置完成且录屏拉取之后**，评价的是「语音语料 + 等待」整段过程中的界面表现，而不是单次点击。

### 3.1 流水线位置

```text
预置回放成功
  → 开录 → TTS 各步 + wait → tail → 停录 → pull
  → （可选 Home）
  → 抽帧到 frames/
  → judge_images（VL 或 mock）
  → evaluation.json + report.md
```

设备侧可串行下一条；抽帧+评判默认后台并行（`run.judge_async` / `judge_parallel`）。

### 3.2 评判依据

| 来源 | 用途 |
|------|------|
| `frames/` 中的截图序列 | 按时间顺序的 GUI 证据（抽样保留首尾与中间帧，上限约 16 张） |
| 用例「预期结果」列 | 规范为 `exceptions` 字符串列表（多行预期 → 多条） |

模型需结合**全部提供的截图**，对照 exceptions 判断任务是否达成；提示词要求**从严**：证据不足、只完成一半流程 → `failed`。

### 3.3 VL 输出与通过规则

对齐 TestApp GUI Poll（E1）：模型返回结构化结果，经本地解析后得到 `passed`：

- 关注 `result_parsed` 中各步 / 轮次的 `result`（如 `success`）以及 `final_result` 等字段（详见 `echo_eval.judge.schema.parse_evaluation_passed`）。
- 另有本地占位成功拒绝等规则（如导航场景下的占位文案），减少「空壳成功」。

产物：`{run_dir}/evaluation.json`（含 `passed`、`exceptions`、`images`、`result_parsed` 等），以及 `report.md` 摘要。

独立调试可用：

```bash
python -m echo_eval judge --run-dir artifacts/<某次采集> \
  --expect "界面出现天气相关结果" --backend vl
```

### 3.4 Mock 评判

`--backend mock`：不调网关。规则概要为「有图且有期望 → pass，否则 fail」，用于无 VL 或流水线联调。正式验收应使用 `--backend vl`。

### 3.5 与预置评判的差异（易混点）

| | 预置 Verify | 正式 Judge |
|--|-------------|------------|
| 问什么 | 「**这一步点击**是否完成？」 | 「**整段语音任务**是否达到预期？」 |
| 证据 | 单次操作前后各一张（或仅 after） | 整段录屏抽多帧 |
| 期望从哪来 | 预置步骤原文 | 表格「预期结果」 |
| 失败影响 | 该步 cache failed / 校准重试；run 可能跳过整条 | 本条用例 `passed=false`，一般不跳过设备流程（已录完） |

---

## 4. 配置与共用网关

两套 VL 均读 `configs/model.yaml` 中的视觉评测配置（可用环境变量 `VL_EVAL_CONFIG` 覆盖路径）。

与等待相关的预置参数（不影响正式多帧评判本身）：

```yaml
precondition:
  max_retries: 3
  step_settle_sec: 1.0
  step_settle_overrides:
    打开爱奇艺: 8
```

正式侧与录屏/并行相关：

```yaml
run:
  step_wait_sec: 3
  tail_sec: 5
  judge_async: true
  judge_parallel: 3
```

超时：预置 Act/Verify 使用配置中的 `timeout` / `timeout_sec`；正式多帧评判会在此基础上按图片数量适当加长，并设上限。

---

## 5. 推荐使用方式

1. **先覆盖检查**（前端或校准开头的报告），确认无 blocked 用例。  
2. **校准**未 ready 的 GUI 预置；看 `VL-Verify` 的 `achieved` 与 shots，而不是只看 `status=passed` 的旧 notes。  
3. **再 run**；以 `evaluation.json` 的 `passed` 与 `report.md` 作为正式对错依据。  
4. 排查时区分日志：`VL-Act` / `VL-Verify` 属于预置；`评判中` / `evaluation.json` 属于正式测试。

---

## 6. 相关文档

| 文档 | 内容 |
|------|------|
| [PHASE4_RUN.md](./PHASE4_RUN.md) | Excel/CSV 端到端、预置开关与 CLI |
| [JUDGE.md](./JUDGE.md) | 正式多帧评判器（E1）与 mock |
| [FORMAL_VL_JUDGE.md](./FORMAL_VL_JUDGE.md) | **仅正式评测**：方法说明 + 完整 VL 提示词 |
| [CASE_TIMELINE.md](./CASE_TIMELINE.md) | 录屏 / TTS / Home / 异步评判时间线 |
| [TESTAPP_JUDGE_MAPPING.md](./TESTAPP_JUDGE_MAPPING.md) | 与 TestApp 评判字段映射 |
| [WEB_CONSOLE.md](./WEB_CONSOLE.md) | 前端覆盖检查与校准入口 |
