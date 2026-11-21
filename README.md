# echo-eval — 语音助手无线 ADB 自动化评测

通过无线 ADB 连接语音助手设备，自动录屏抽帧，并在录制过程中本机播放刺激音频；后续接入 LLM 评判，形成完整自动化测评流水线。

## CLI 和自动化测评的关系

| 阶段 | 能力 | 命令 |
|------|------|------|
| Phase 0–1 | 环境检查、无线连接、录屏、pull、抽帧 | `doctor` / `connect` / `record` |
| Phase 2（当前） | 录屏同时本机播放 wav（设备麦克拾音） | `spike` / `record --audio ...` |
| 后续 | 用例 → 播放 → 录屏 → LLM 评判 → 报告 | 计划中的 `run --case` |

这些命令是测评的**采集层**；完整 `run` 会复用同一套 `device/`、`capture/`、`stimulus/` 代码。

## 配置

编辑 [`configs/device.yaml`](configs/device.yaml)：

```yaml
adb:
  host: "192.168.43.244"
  port: 5555
capture:
  duration_sec: 15
  fps: 1
stimulus:
  enabled: false
  path: assets/audio/beep_1s.wav
  play_device: host
  start_delay_sec: 1.0
```

把 `path` 换成你的唤醒词/问句 wav 即可。

## 安装与使用

```bash
git fetch origin
git checkout cursor/phase2-audio-stimulus-4bfa
git pull origin cursor/phase2-audio-stimulus-4bfa

python -m pip install -e ".[dev]"

python -m echo_eval doctor
python -m echo_eval connect

# 只录屏抽帧
python -m echo_eval record

# 穿刺：强制边录边播（电脑外放，设备拾音）
python -m echo_eval spike
# 或指定音频
python -m echo_eval spike --audio assets/audio/beep_1s.wav --seconds 15
```

产物：`artifacts/capture_<时间戳>/`

- `capture.mp4`
- `frames/frame_XXX.jpg`
- `timeline.json`（含 `play_offset_sec`）
- `capture_meta.json`

## 文档

- [`docs/DEVELOPMENT_PLAN.md`](docs/DEVELOPMENT_PLAN.md)
- [`docs/TESTAPP_JUDGE_MAPPING.md`](docs/TESTAPP_JUDGE_MAPPING.md)
