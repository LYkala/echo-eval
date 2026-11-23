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
export VL_EVAL_CONFIG="D:/code/voiceass/TestAppGUI-master/testApp/configs/model.yaml"

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
```

多用例时：停录并 pull 完成后，默认按一次 `KEYCODE_HOME` 回到系统桌面；Home 失败只打警告，不中断下一条。

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
