# Android Emulator Lab（语音/虚拟麦克风试点）

本目录用于在 Android 模拟器上测试：**语音文件 → App 识别 → Kimi 大模型 → 回复 wav**。

## 当前状态

| 项 | 值 |
|----|-----|
| AVD 名称 | `TestAppGUI_API34` |
| 系统镜像 | `system-images;android-34;google_apis;x86_64` |
| 语音 App | Sherpa ASR (`com.k2fsa.sherpa.onnx`) |
| 流程图 | 双击 **[voice_flow.html](voice_flow.html)** 用浏览器打开（一张完整图） |

## 快速开始

```powershell
cd d:\pythonProj\MyGUITest\TestAppGUI\testApp\android-emulator
.\scripts\start-emulator.ps1
python check_voice_setup.py --download-samples
python voice_app_loop.py -i audio_samples/weather_question.wav
```

无虚拟声卡时（跳过真实灌麦）：

```powershell
python voice_app_loop.py -i audio_samples/weather_question.wav --mic-mode fallback
python test_voice_assistant_apk.py --allow-demo-fallback
```

### 逼真灌麦（VB-Cable，一次性配置）

把 `weather_question.wav` 经虚拟声卡送进模拟器麦克风，走真实 Sherpa ASR：

```powershell
# 1) 下载驱动 + 安装（需管理员，见脚本提示）
.\scripts\setup_virtual_mic.ps1

# 2) 配置：CABLE Output = 默认麦克风
.\scripts\setup_virtual_mic.ps1 -ConfigureOnly

# 3) 自测环回（看 CABLE Output 电平条是否跳）
.\scripts\setup_virtual_mic.ps1 -TestPlay

# 4) 跑方案 B APK（真实灌麦，不走示例按钮）
python test_voice_assistant_apk.py --real-mic

# 或跑 host 脚本环回
python voice_app_loop.py -i audio_samples/weather_question.wav
```

辅助命令：

```powershell
.\scripts\configure_vbcable_routing.ps1          # 只设默认麦克风
.\scripts\play_to_vbcable.ps1 -AudioPath .\audio_samples\weather_question.wav
python vbcable_play.py --status
.\scripts\setup_virtual_mic.ps1 -RestoreDefaults # 恢复原来的默认音频设备
```

链路：`wav → CABLE Input → CABLE Output(默认麦) → adb emu avd hostmicon → App ASR`

## 常用命令

```powershell
.\scripts\start-emulator.ps1
.\scripts\stop-emulator.ps1
.\scripts\status.ps1
python install_and_dump_ui.py --package com.k2fsa.sherpa.onnx --skip-install
python voice_app_loop.py -i your.wav
python check_voice_setup.py
```

## 输出

默认目录 `app_loop_output/<文件名>_<时间戳>/`：

| 文件 | 说明 |
|------|------|
| `asr.txt` | App 识别文本 |
| `llm.txt` | Kimi 回复 |
| `output.wav` | 合成回复语音 |
| `result.json` | 各步耗时 |

同时 push 到模拟器：`/sdcard/voice_lab/output.wav`

## 输入示例

**默认示例（问天气）**：`audio_samples/weather_question.wav`

其他样本：`welcome.mp3`、`hello_world_female2.wav`（`check_voice_setup.py --download-samples`）

## 方案 B：独立语音助手 APK

单 APK 内完成 **Sherpa 识别 → Kimi → 系统 TTS**，见 **[voice-assistant-app/README.md](voice-assistant-app/README.md)**。

```powershell
.\scripts\prepare_voice_assistant_app.ps1
.\scripts\build_voice_assistant_app.ps1
adb install -r voice-assistant-app\VoiceAssistant-debug-x86_64.apk
```

## AISF 目标 APK 说明

| 项目 | 值 |
|------|-----|
| 包名 | `com.huawei.aisftvassistant` |
| ABI | 仅 `armeabi-v7a`，minSdk 31 |
| Windows x86 模拟器 | 当前无法安装，需 ARM 真机或 x86_64 版 APK |

**EulerOS / openEuler ARM64 语音测试**：见 **[aisf-voice-lab/README.md](aisf-voice-lab/README.md)**（PulseAudio 灌麦 + 宿主机录音，一键脚本 `run_e2e.sh`）。

## 目录说明

- `scripts/` — 模拟器启停、虚拟麦克风配置
- `audio_samples/` — 测试输入音频
- `app_loop_output/` — 运行输出（可删）
- `voice-assistant-app/` — 方案 B 独立 APK 源码 overlay + 构建说明
