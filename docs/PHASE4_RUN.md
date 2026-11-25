# Phase 4：Excel/CSV 端到端编排

## 决策摘要

| 项 | 选择 |
|----|------|
| 入口 | `run --excel`（TestApp 风格表） |
| 步骤 | 文本 → Windows SAPI TTS → 本机播放 |
| TTS | 默认 `sapi`，可切换 `file`（`edge`/`http` 预留） |
| 批量 | 支持全部或 `--case-id`；可选 `--fail-fast` |
| 时长 | 开录 → 播各步+等待 → tail → 停录 |
| 报告 | 每条 `report.md` + 批量 `summary.json` |
| 表格 | `.xlsx`（需 openpyxl）或 UTF-8 `.csv` |

## 表头（兼容 TestApp）

至少需要：

- 用例编号  
- 测试步骤（多行编号文本，将逐句 TTS）  
- 预期结果（多行 → exceptions）  

可选：用例名称、等待时间、预置条件、用例类型。

样例：`cases/examples/sample_weather.csv`

## 用法

```bash
git checkout cursor/phase4-excel-runner-4bfa   # 或 main（合入后）
export PYTHONPATH=src
# 默认读 configs/model.yaml；需要时再覆盖：
# export VL_EVAL_CONFIG="D:/path/to/TestApp/configs/model.yaml"

# 单条 + mock 评判（不调 VL）
python -m echo_eval run --excel cases/examples/sample_weather.csv \
  --case-id demo_weather_001 --backend mock

# 真实 VL
python -m echo_eval run --excel cases/examples/sample_weather.csv \
  --case-id demo_weather_001 --backend vl

# 整表批量
python -m echo_eval run --excel your.xlsx --backend vl
```

## 配置（configs/device.yaml）

```yaml
tts:
  backend: edge   # edge | sapi | file（edge 需出网）
  voice: zh-CN-XiaoxiaoNeural
  rate: 0
run:
  step_wait_sec: 3
  head_delay_sec: 1
  tail_sec: 5
  return_home_after_case: true   # 每条结束后按系统 Home
  return_home_wait_sec: 2        # Home 后再等几秒开下一条
  judge_async: true              # 抽帧+VL 后台并行，不挡下一条
  judge_parallel: 3              # 同时评判条数
```

多用例时：停录并 pull 完成后，默认按一次 `KEYCODE_HOME` 回到系统桌面；Home 失败只打警告，不中断下一条。  
评判默认异步（对齐 TestApp Web）：设备串行开下一条，抽帧+VL 进后台队列；`judge_async: false` 可恢复同步。

更细的录屏 / 播报 / Home / 异步评判时间线见 [CASE_TIMELINE.md](./CASE_TIMELINE.md)。  
预置 VL 验收与正式多帧评判的方法对比见 [EVALUATION_METHODS.md](./EVALUATION_METHODS.md)。

### TTS（edge-tts）

```bash
pip install "edge-tts>=7.0.0"
# 或：pip install -e .
```

默认 `backend: edge`，音色如 `zh-CN-XiaoxiaoNeural`。控制台可切换后端/音色并试听。edge 需访问 `speech.platform.bing.com`；连不上时默认 **回退本机 SAPI**（`tts.fallback_sapi: true`，仅 Windows），日志会打 `[warn] edge-tts 失败，回退本机 SAPI`。

### 语音库（预合成）

开测前可在控制台「预合成当前数据集」把步骤文本写成 `assets/tts_library/`（也支持自定义文本）。运行时 `prefer_library: true` 时优先命中库；未命中则用本机系统语音（Windows SAPI），不现场调 edge。

```yaml
tts:
  library_dir: assets/tts_library
  prefer_library: true
```

## 预置条件（v0.11）

开跑前可做覆盖检查；每条正式测前：`force-stop` 上一条包 → Home → 回放本条预置 ADB → 再播「文本语料」。

```yaml
precondition:
  enabled: true
  coverage_check: true
  verify_after_replay: false  # false=已通过不再 VL 复验
  max_retries: 3
  step_settle_sec: 1.0        # 每步默认等待（秒）
  step_settle_overrides:      # 按预置步骤文案差异化等待（开 App 等广告）
    打开爱奇艺: 8
    打开设置: 2
  cache_dir: artifacts/precondition_cache
  packages_path: configs/packages.yaml
```

`step_settle_overrides` 在校准与正式回放中，于**该步执行完之后**生效；匹配顺序为规范化后精确匹配 → 最长子串包含。未命中则用 `step_settle_sec`。

**预置校准**（`precondition-calibrate`）：
- 默认只处理「预置未全部 pass」的用例；整条已 ready 的跳过。
- 用例内已 pass 的前缀步：**回放 ADB**（铺界面），不重 VL。
- 未 pass 的步：确定性执行或 VL 校准。
- `--retry-failed`：重试 cache 为 failed 的步。
- `--force`：连已 pass 也重新校准（并处理全部用例）。

正式 run / 校准回放时，已 pass 的 `adb_commands` 原样执行（含手改），不调用 `launch_package` 覆盖。

样例数据集：`cases/examples/sample_v012_system_iqiyi_10.csv`（v0.12 精简预置；新列名）。

```bat
set PYTHONPATH=src
python -m echo_eval precondition-calibrate --excel cases/examples/sample_v012_system_iqiyi_10.csv --backend vl
python -m echo_eval precondition-calibrate --excel cases/examples/sample_v012_system_iqiyi_10.csv --retry-failed
python -m echo_eval precondition-calibrate --excel cases/examples/sample_v012_system_iqiyi_10.csv --force
```

未就绪或 `failed`（未加 `--retry-failed`）的用例在 `run` 时会跳过并提示。

点击类 VL 校准时，控制台/任务日志会打印 `VL-Act raw` / `VL-Verify raw`；同时落盘到  
`artifacts/precondition_cache/shots/<key>/tryN_act.json`、`tryN_verify.json`（及前后截图），便于排查点偏或超时。

## 产物

```
artifacts/<时间>_<case_id>/
  case.json
  steps/step_01.wav
  capture.mp4
  frames/
  timeline.json
  evaluation.json
  report.md
  run_meta.json

artifacts/batch_<时间>/summary.json
```
