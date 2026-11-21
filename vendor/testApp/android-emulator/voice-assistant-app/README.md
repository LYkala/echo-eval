# 语音助手 APK（方案 B）

单 APK：**Sherpa ONNX 语音识别 → Kimi 大模型 → Android 系统 TTS 播报**。

| 步骤 | 实现 |
|------|------|
| ASR | Sherpa ONNX（从已装 Sherpa APK 提取 int8 中英模型） |
| LLM | Kimi `kimi-k2.5`（App 内 HTTP） |
| TTS | Android `TextToSpeech` |

包名：`com.testappgui.voiceassistant`

## 前置条件

1. 模拟器/真机已安装 **Sherpa ASR**（`com.k2fsa.sherpa.onnx`）— 用于提取模型和 `.so`
2. **JDK 17+**（`winget install Microsoft.OpenJDK.17`）
3. Android SDK（`local.properties` 自动写入）

## 构建

```powershell
cd d:\pythonProj\MyGUITest\TestAppGUI\testApp\android-emulator
.\scripts\prepare_voice_assistant_app.ps1
.\scripts\build_voice_assistant_app.ps1
adb install -r voice-assistant-app\VoiceAssistant-debug-x86_64.apk
```

`prepare` 会：

- `adb pull` 模拟器里的 Sherpa APK
- 解压出 `jniLibs/x86_64` 与 `assets/` 模型
- 合并 `overlay/` 源码到 `project/`

输出 APK：`voice-assistant-app/VoiceAssistant-debug-x86_64.apk`（约 220MB）

## 使用

1. 打开 App「语音助手」，等待「就绪」
2. **问天气示例** — 一键走 Kimi + 播报（无需录音）
3. **开始说话 → 停止** — 完整 ASR → Kimi → 播报

模拟器灌麦（wav → 真实 ASR，可选）：

```powershell
# 一次性：安装 VB-Cable + 设 CABLE Output 为默认麦克风
..\scripts\setup_virtual_mic.ps1
..\scripts\setup_virtual_mic.ps1 -ConfigureOnly
..\scripts\setup_virtual_mic.ps1 -TestPlay

# 端到端测试（不走「问天气示例」按钮）
cd ..
python test_voice_assistant_apk.py --real-mic
```

链路：`weather_question.wav → CABLE Input → CABLE Output → hostmicon → App 录音 → Sherpa ASR`

仅 hostmicon（无 VB-Cable 时无效）：

```powershell
adb emu avd hostmicon
adb shell pm grant com.testappgui.voiceassistant android.permission.RECORD_AUDIO
```

## 目录

```
voice-assistant-app/
├── overlay/     # MainActivity、KimiClient、Sherpa Kotlin API、UI
├── template/    # Gradle 工程骨架
├── project/     # prepare 生成（可删后重跑）
└── VoiceAssistant-debug-x86_64.apk
```

## 环境变量（可选）

| 变量 | 说明 |
|------|------|
| `KIMI_API_KEY` | 覆盖内置 Key |
| `KIMI_BASE_URL` | 默认 `https://api.moonshot.cn/v1` |
| `KIMI_MODEL` | 默认 `kimi-k2.5` |

## 真机

```powershell
.\scripts\prepare_voice_assistant_app.ps1 -Abi arm64-v8a -PullSherpaApk
.\scripts\build_voice_assistant_app.ps1 -Abi arm64-v8a
```

需先在真机安装对应 ABI 的 Sherpa ASR APK。

## 说明

- API Key 写在 App 内，仅适合演示
- ASR 模型配置为 **type 8**（int8 encoder），与官方 Sherpa APK 资产一致
- 若 `gradlew` 不可用，build 脚本会回退到本机 Gradle 缓存离线构建
