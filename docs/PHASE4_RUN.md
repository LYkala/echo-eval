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
  backend: sapi   # 或 file
  voice: ""
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

## 预置条件（v0.11）

开跑前可做覆盖检查；每条正式测前：`force-stop` 上一条包 → Home → 回放本条预置 ADB → 再播「文本语料」。

```yaml
precondition:
  enabled: true
  coverage_check: true
  reuse_passed: true          # 已通过步骤只回放，不重校准
  verify_after_replay: false  # false=已通过不再 VL 复验
  max_retries: 3
  cache_dir: artifacts/precondition_cache
  packages_path: configs/packages.yaml
```

样例数据集：`cases/examples/sample_v012_system_iqiyi_10.csv`（v0.12 精简预置；新列名）。

点击类预置需先独立校准（写入 `artifacts/precondition_cache/`，跨数据集按「前缀+当前步」复用；默认不自动清理）：

```bat
set PYTHONPATH=src
python -m echo_eval precondition-calibrate --excel cases/examples/sample_v012_system_iqiyi_10.csv --backend vl
python -m echo_eval precondition-calibrate --excel cases/examples/sample_v012_system_iqiyi_10.csv --retry-failed
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
