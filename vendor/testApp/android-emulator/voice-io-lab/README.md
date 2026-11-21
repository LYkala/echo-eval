# voice-io-lab

针对 **voice-assistant-app** 的语音文件灌入 / 回复 wav 导出测试工作区（不改 App 源码）。

## 环境

- AVD：`TestAppGUI_API34`（x86_64）
- App 包名：`com.testappgui.voiceassistant`
- 输入：VB-Cable 虚拟麦（`weather_question.wav` → CABLE Input → hostmicon → App 录音）
- 输出：`adb exec-out run-as ... cat files/voice_lab/output.wav`

## 一键运行

```powershell
cd d:\pythonProj\MyGUITest\TestAppGUI\testApp\android-emulator\voice-io-lab
.\run_e2e.ps1
```

首次或换机后先构建并安装：

```powershell
.\build_and_install.ps1
```

已安装 APK、只跑测试：

```powershell
.\run_e2e.ps1 -SkipBuild -SkipInstall
python ..\test_voice_assistant_apk.py --real-mic --allow-demo-fallback
```

可选参数：

```powershell
.\run_e2e.ps1 -SkipBuild          # 使用已有 APK
.\run_e2e.ps1 -SkipInstall        # 不重复 install（避免 force-stop）
.\run_e2e.ps1 -InputWav ..\audio_samples\weather_question.wav
.\run_e2e.ps1 -SkipVbConfigure    # 已配置过 VB-Cable 时跳过
.\prep_emulator.ps1               # 关闭 ANR 对话框并前台 App
```

## 产物

| 路径 | 说明 |
|------|------|
| `apk/VoiceAssistant-debug-x86_64.apk` | 当前模拟器 ABI 的 debug 包 |
| `runs/<timestamp>/output.wav` | 拉取的 App 回复语音 |
| `runs/<timestamp>/result.json` | ASR / LLM / 路径摘要 |
| `runs/<timestamp>/*.png` | 各步骤截图 |

## 分步

```powershell
.\build_and_install.ps1
python ..\test_voice_assistant_apk.py --real-mic -i ..\audio_samples\weather_question.wav
```
