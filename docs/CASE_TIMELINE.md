# 多用例测试时间线

本文说明当前 echo-eval（`judge_async: true`）批量跑 Excel/CSV 时，**每条用例**与**整批**在时间上如何衔接：录屏、语音、回桌面、异步评判。

相关实现：`src/echo_eval/runner/excel_runner.py`、配置 `configs/device.yaml` 的 `run:` 段。

---

## 1. 总览：设备串行，评判并行

```
用例1 设备 ████████████████░░░░░░░░░░░░░░
用例1 评判         ░░████████████
用例2 设备               ████████████████
用例2 评判                     ░░████████████
用例3 设备                           ████████████
…
整批结束：等所有后台评判完成 → 写 summary
```

| 轴 | 行为 |
|----|------|
| **设备** | 独占时间轴：TTS → 录屏（含播报）→ pull → Home → 立刻下一条 |
| **评判** | 后台队列：抽帧 + VL/mock；默认最多 3 路并行，**不挡**下一条开测 |

评判失败只记本条 error，不中断其它设备用例和其它评判。

---

## 2. 单条用例：设备侧时间线

| 阶段 | 做什么 | 时长（量级） | 是否录屏 |
|------|--------|--------------|----------|
| 1. 准备 | 建目录、连 ADB | 通常不足 1s | 否 |
| 2. TTS 合成 | 各步先合成 `.wav`（**尚未播放**） | 视句数 / SAPI，约数秒 | 否 |
| 3. 开录 | 启动 `screenrecord`，计划时长见 §3 | — | **开始** |
| 4. head | 开录后空等 `head_delay_sec`（默认 **1s**） | 1s | 是 |
| 5. 播报循环 | 每步：播 wav → 再等「等待时间」 | Σ(语音时长 + wait) | 是 |
| 6. tail | 说完后再录 `tail_sec`（默认 **5s**） | ≤5s | 是 |
| 7. 等录屏结束 | `screenrecord_wait` | 到计划结束为止 | 结束 |
| 8. pull | 拉 `capture.mp4`，删设备端文件 | 视文件大小 | 否 |
| 9. Home | 按 Home + `return_home_wait_sec`（默认 **2s**） | ≈2s | 否 |
| 10. 交接 | 本条入评判队列 → **立刻开下一条设备** | ≈0 | 否 |

### 样例：`demo_weather_001`（`cases/examples/sample_weather.csv`）

表内两步等待分别为 2s、3s：

```
t=0       开录
t=0 ~ 1   head（静默，默认 1s）
t≈1       播「小艺小艺」 → 语音时长 d1
          再等 2s（表「等待时间」）
          播「今天西安天气怎么样」 → d2
          再等 3s
          再录约 5s（tail）
          …录屏进程结束
随后       pull → Home（再等约 2s）→ 入评判队列 / 开下一条
```

---

## 3. 录屏计划时长

开录前按已合成的 wav 计算：

```text
duration_sec = max(5, ceil( head + Σ(d_i + wait_i) + tail + 2 ))
```

| 符号 | 来源 | 默认 |
|------|------|------|
| `head` | `run.head_delay_sec` | 1 |
| `d_i` | 第 i 句 wav 时长 | — |
| `wait_i` | 表「等待时间」列；缺省用 `run.step_wait_sec` | 3 |
| `tail` | `run.tail_sec` | 5 |
| `+2` | 固定缓冲 | 2 |

播完后若仍早于计划结束时间，会再 sleep，然后等待 `screenrecord` 退出。

**录屏覆盖范围** = head + 全部播报与步间等待 + tail。  
**不含**：TTS 合成、pull、Home、抽帧、评判。

每步相对开录的偏移写入产物 `timeline.json` 的 `play_offset_sec`。

---

## 4. 单条用例：评判侧（后台）

设备第 9 步完成后入队：

| 阶段 | 做什么 | 是否挡设备下一条 |
|------|--------|------------------|
| 排队 | 最多 `judge_parallel` 路同时跑 | 否 |
| 抽帧 | 从本条 `capture.mp4` 按 `capture.fps` 抽帧 | 否 |
| VL / mock | 写 `evaluation.json`、`report.md` 等 | 否 |

`judge_async: false` 时恢复同步：本条评判结束才开下一条（旧行为）。

---

## 5. 多用例串接（设备轴）

```
[用例1: TTS → 录屏(含播报) → pull → Home]
        └─ 评判1 后台 ───────────┐
[用例2: TTS → 录屏 → pull → Home] │
        └─ 评判2 后台 ─────┐    │
[用例3: …]                  │    │
…                          ▼    ▼
全部设备跑完后，等评判队列清空 → 写 batch summary → 任务结束
```

**下一条开测时刻 ≈ 上一条 Home 完成**，不必等上一条 VL 结束。

Web 控制台「当前任务」用两条进度条区分：**设备** / **评判**。

---

## 6. 相关配置（`configs/device.yaml` → `run:`）

| 项 | 默认 | 作用 |
|----|------|------|
| `head_delay_sec` | 1 | 开录后、第一句前的静默 |
| `tail_sec` | 5 | 最后一句后多录多久，便于界面出结果 |
| `step_wait_sec` | 3 | 表无「等待时间」时的默认步间等待 |
| `return_home_after_case` | true | 每条结束后是否按系统 Home |
| `return_home_wait_sec` | 2 | Home 后等多久再开下一条 |
| `judge_async` | true | 抽帧+评判是否后台并行 |
| `judge_parallel` | 3 | 同时评判条数 |

表内「等待时间」按步覆盖 `step_wait_sec`。

---

## 7. 产物中的时间线索

每条用例目录（`artifacts/<时间>_<case_id>/`）大致包含：

| 文件 | 时间相关内容 |
|------|----------------|
| `steps/step_XX.wav` | 合成语音（开录前生成） |
| `capture.mp4` | 设备录屏（head～tail） |
| `timeline.json` | `record_started_at`、`planned_duration_sec`、每步 `play_offset_sec` / `duration_sec` / `wait_sec` |
| `frames/` | 评判用抽帧（异步，在 pull 之后后台生成） |
| `evaluation.json` / `report.md` | 评判结果（异步完成时间晚于设备结束） |

批量：`artifacts/batch_<时间>/summary.json`（整批含全部评判结束后写出）。
