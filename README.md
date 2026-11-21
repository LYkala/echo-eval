# echo-eval — 语音助手无线 ADB 自动化评测

通过无线 ADB 连接语音助手设备，自动录屏抽帧，后续接入语音播放与多模态评判，形成完整自动化测评流水线。

## CLI 和自动化测评的关系

| 阶段 | 能力 | 命令 |
|------|------|------|
| Phase 0–1（当前） | 环境检查、无线连接、录屏、pull、抽帧 | `doctor` / `connect` / `record` |
| 后续 | 播放语音 → 录屏 → LLM 评判 → 报告 | 计划中的 `run --case` |

当前 CLI **不是**最终打分产品，而是测评的**设备采集层**。完整 `run` 会直接调用同一套 `device/`、`capture/` 代码，先穿刺录屏再叠刺激与评判。

## 文档

| 文档 | 说明 |
|------|------|
| [`docs/DEVELOPMENT_PLAN.md`](docs/DEVELOPMENT_PLAN.md) | 分阶段开发计划 |
| [`docs/TESTAPP_JUDGE_MAPPING.md`](docs/TESTAPP_JUDGE_MAPPING.md) | TestAppGUI 评判对照 |

## 配置

编辑 [`configs/device.yaml`](configs/device.yaml)（可改 IP、端口、录制时长、抽帧 fps 等）：

```yaml
adb:
  host: "192.168.43.244"
  port: 5555
capture:
  duration_sec: 15
  fps: 1
```

## 安装与使用

```bash
# 在仓库根目录
python -m pip install -e ".[dev]"

python -m echo_eval doctor
python -m echo_eval connect
python -m echo_eval record
python -m echo_eval record --seconds 15 --no-frames   # 无 ffmpeg 时
```

产物目录：`artifacts/capture_<时间戳>/`

- `capture.mp4` — 设备录屏  
- `frames/frame_XXX.jpg` — 抽帧  
- `capture_meta.json` — 元数据  

Git Bash / UCRT64 下请用本 CLI 录屏（已处理 `/sdcard` 路径误转换）；手工 `adb shell` 时仍建议加 `MSYS_NO_PATHCONV=1`。

## 参考源码

`vendor/testApp/` 仅作评判对照；Phase 0–1 不依赖其运行。
