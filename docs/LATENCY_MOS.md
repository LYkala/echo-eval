# 端到端时延（P2）与 TTS MOS（P3）

与 GUI 成功率、ASR 准确率**并行**，互不影响。

## 现场 vs pull 后

| 阶段 | 做什么 |
|------|--------|
| **现场** | 唤醒/用例播报（默认 edge-tts）→ **短 ASR 采图** → **主机麦录 MOS** |
| **pull 后后台** | GUI VL、ASR VL、**离线时延**、MOS 打分 |

现场不再为时延做高频 screencap；时延从 `capture.mp4` 按 `play_end_offset_sec` 抽帧离线算。

## P2 端到端时延（离线）

**定义**：`play_end`（主机 TTS 播完）→ 画面**首变**（忽略底部识别条文字变化）。

| 项 | 默认 |
|----|------|
| 输入 | `capture.mp4` + timeline `play_end_offset_sec` |
| 基线 | TTS 播放中段抽一帧（说完前 UI 尚未变） |
| 采帧 | 说完后约 10 fps，窗口 `latency.window_sec`（默认 8s） |
| 忽略区域 | 底部 `bottom_band_ratio`（默认 0.22） |
| 超时 | `status=no_action`（无操控） |
| 精度 | 报告到 **0.1s** |

产物：`latency_frames/`、`timeline.steps[].latency`、`latency_summary.json`、`report.md` 时延节。

## P3 TTS MOS

**背景**：系统 `screenrecord` **不录**设备外放音频，因此用**主机麦克风**在说完后录一段设备扬声器回复。录制必须在现场；打分在 pull 后。

| 项 | 默认 |
|----|------|
| 窗口 | 说完后 5s |
| 无有效语音 | **N/A**（不记失败） |
| 评分 | 启发式流畅度 **1–5**（静音比、切分、削波等；非听感大模型） |

Windows 需配置麦克风（`ffmpeg -list_devices true -f dshow -i dummy` 查看名称）：

```yaml
mos:
  input_format: dshow
  input_device: audio=你的麦克风名称
```

产物：`mos_audio/step_XX.wav`、`mos/step_XX.json`、`mos_summary.json`。

录制失败（无设备/权限）时该句 MOS 记 N/A/error，不阻断整例 GUI 评判。
