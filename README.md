# echo-eval — 语音助手无线 ADB 自动化评测

通过无线 ADB 连接语音助手设备，自动录屏抽帧，并在录制过程中本机播放刺激音频；后续接入 LLM 评判，形成完整自动化测评流水线。

## CLI 和自动化测评的关系

| 阶段 | 能力 | 命令 |
|------|------|------|
| Phase 0–1 | 环境检查、无线连接、录屏、pull、抽帧 | `doctor` / `connect` / `record` |
| Phase 2 | 录屏同时本机播放 wav（设备麦克拾音） | `spike` / `record --audio ...` |
| Phase 3 | 多帧截图 + 预期结果 → VL/Mock 评判 | `judge` |
| Phase 4 | Excel/CSV → TTS → 录屏 → 评判 → 报告 | `run --excel` |
| Phase 5 | PC1 Web 控制台（上传/检查/开测/回看） | `cd web && npm run dev` |

这些命令是测评的**采集层**；完整 `run` 会复用同一套 `device/`、`capture/`、`stimulus/` 代码。

## 环境准备（Windows 端到端）

安装 Python / Node、ADB、FFmpeg、Python 包、配置与控制台启动，见 **[`docs/SETUP_WINDOWS.md`](docs/SETUP_WINDOWS.md)**。

## 配置

编辑 [`configs/device.yaml`](configs/device.yaml)：

```yaml
adb:
  host: "192.168.43.244"
  port: 5555
  adb_path: "D:/software/platform-tools/adb.exe"  # 按本机修改
capture:
  duration_sec: 15
  fps: 1
  ffmpeg_path: ""  # doctor 找不到时填写 where ffmpeg 的路径
stimulus:
  enabled: false
  path: assets/audio/beep_1s.wav
  play_device: host
  start_delay_sec: 1.0
```

## 使用

```bash
git checkout main
git pull origin main

# 免安装、零第三方依赖（pip 坏掉也能跑）
export PYTHONPATH=src
python -m echo_eval doctor
python -m echo_eval connect
python -m echo_eval record
python -m echo_eval spike --audio assets/audio/beep_1s.wav --seconds 15

# Phase 3：多帧评判（默认 configs/model.yaml，结构同 TestApp）
# 先把 TestApp 里的 api_key/base_url/model_id 填进 configs/model.yaml
python -m echo_eval judge --run-dir artifacts/capture_xxx \
  --expect "界面出现天气相关结果" --backend vl

# 无网关联调
python -m echo_eval judge --run-dir artifacts/capture_xxx \
  --expect "界面出现天气相关结果" --backend mock

# Phase 4：Excel/CSV 端到端（步骤文本 → SAPI TTS → 录屏 → 评判）
python -m echo_eval run --excel cases/examples/sample_weather.csv \
  --case-id demo_weather_001 --backend mock
```

产物：`artifacts/capture_<时间戳>/`

- `capture.mp4`
- `frames/frame_XXX.jpg`
- `timeline.json`（含 `play_offset_sec`）
- `capture_meta.json`
- `evaluation.json`（`judge` 生成）

## 文档

- [`docs/SETUP_WINDOWS.md`](docs/SETUP_WINDOWS.md) — Windows 端到端环境安装与使用  
- [`docs/JUDGE.md`](docs/JUDGE.md) — Phase 3 评判  
- [`docs/PHASE4_RUN.md`](docs/PHASE4_RUN.md) — Phase 4 Excel 编排  
- [`docs/WEB_CONSOLE.md`](docs/WEB_CONSOLE.md) — PC1 Web 控制台  
- [`docs/DEVELOPMENT_PLAN.md`](docs/DEVELOPMENT_PLAN.md) — 开发计划  
- [`docs/TESTAPP_JUDGE_MAPPING.md`](docs/TESTAPP_JUDGE_MAPPING.md) — TestAppGUI 评判对照  
