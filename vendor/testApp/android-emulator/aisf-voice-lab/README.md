# AISF 语音测试实验室（EulerOS / openEuler ARM64）

在 **EulerOS-V2.0SP11-aarch64**（或同类 openEuler ARM 服务器）上，安装虚拟安卓、部署 **AISF 语音助手 APK**，并完成 **语音灌入 + 回复录音** 测试。

> 适用 APK：`aisf_voice_assistant_tv-phoneRelease-20260715_103645.apk`  
> 包名：`com.huawei.aisftvassistant`  
> 与 `voice-assistant-app`（方案 B）是**不同的 App**，不要混用。

---

## 一、这套东西是干什么的？

| 步骤 | 做什么 |
|------|--------|
| 1 | 下载 Android 模拟器（ARM64）+ adb 等工具 |
| 2 | 创建并启动虚拟安卓机 |
| 3 | 安装 AISF APK |
| 4 | 用 **PulseAudio 虚拟麦克风** 把 `weather_question.wav` 灌进 App |
| 5 | 用 **ffmpeg 环回录音** 抓取 App 语音回复，保存为 `reply_recorded.wav` |

**和 Windows 方案 B 的区别：**

- 输入：Linux 用 PulseAudio 虚拟麦，不用 VB-Cable
- 输出：AISF **没有** App 内 `output.wav` 导出，改为宿主机录音

---

## 二、你需要准备什么？

### 硬件 / 系统

| 项目 | 建议 |
|------|------|
| 系统 | EulerOS V2.0 SP11 aarch64（或 openEuler 22.03+ ARM64） |
| 内存 | ≥ 8 GB（模拟器 + AISF 较吃内存） |
| 磁盘 | ≥ 30 GB 空闲 |
| 虚拟化 | 有 `/dev/kvm` 时模拟器快很多 |

### 软件（脚本会帮你装大部分）

```bash
sudo yum install -y java-17-openjdk java-17-openjdk-devel
sudo yum install -y curl unzip wget
sudo yum install -y pulseaudio pulseaudio-utils alsa-utils
sudo yum install -y ffmpeg python3
sudo yum install -y libGL mesa-libGL
```

### 文件

1. **AISF APK**（约 160MB）  
   放到下面任一位置：
   - `android-emulator/aisf_voice_assistant_tv-phoneRelease-20260715_103645.apk`
   - `aisf-voice-lab/apk/aisf_voice_assistant.apk`

2. **测试语音**（仓库已有）  
   `android-emulator/audio_samples/weather_question.wav`

---

## 三、5 分钟快速开始（复制粘贴）

```bash
# 1. 进入目录（按你实际路径改）
cd /path/to/testApp/android-emulator/aisf-voice-lab

# 2. 加载环境变量（每个新终端都要执行一次）
source ./env.sh

# 3. 检查环境
python3 check_setup.py

# 4. 下载 Android SDK + 复制 APK（需要联网，约 1~3 GB）
bash download_deps.sh

# 5. 创建虚拟安卓（只需一次）
bash create_avd.sh

# 6. 一键跑完整测试
bash run_e2e.sh
```

跑完后看结果目录：

```bash
ls -la runs/
# 最新一次 run 里有：
#   result.json          测试结果
#   reply_recorded.wav   录到的回复语音（若成功）
#   *.png                截图
#   ui_before.xml        测试前界面
```

---

## 四、分步操作（遇到问题按步排查）

### 步骤 0：检查 KVM

```bash
ls -l /dev/kvm
```

- **有输出**：可以用硬件加速，模拟器较快  
- **没有**：也能跑，但会慢；云虚拟机常没有 KVM

### 步骤 1：下载依赖

```bash
source ./env.sh
bash download_deps.sh
```

下载内容保存在：

| 路径 | 内容 |
|------|------|
| `sdk/` | Android SDK（模拟器、adb、系统镜像） |
| `downloads/` | 下载缓存 zip |
| `apk/aisf_voice_assistant.apk` | AISF 安装包副本 |

**离线部署：** 在一台能联网的机器执行 `download_deps.sh`，把整个 `aisf-voice-lab/` 目录（含 `sdk/`、`apk/`）打包拷到 EulerOS。

### 步骤 2：配置虚拟麦克风

```bash
bash setup_virtual_mic.sh
```

验证：

```bash
python3 linux_audio.py --status
paplay --device=VirtualMic ../audio_samples/weather_question.wav
```

恢复默认音频：

```bash
bash setup_virtual_mic.sh --restore
```

### 步骤 3：启动模拟器

```bash
bash start_emulator.sh
```

日志：`runs/emulator.log`

确认设备在线：

```bash
adb devices
# 应看到 emulator-5554    device
```

停止模拟器：

```bash
bash stop_emulator.sh
```

### 步骤 4：安装 AISF

```bash
bash install_aisf.sh
```

### 步骤 5：跑语音测试

```bash
python3 test_aisf_voice.py \
  --serial emulator-5554 \
  -i ../audio_samples/weather_question.wav \
  --output-dir runs/manual_test
```

---

## 五、目录说明

```
aisf-voice-lab/
├── README.md                 ← 本说明书
├── env.sh                    ← 环境变量（先 source）
├── download_deps.sh          ← 下载 SDK + APK
├── check_setup.py            ← 检查是否装齐
├── create_avd.sh             ← 创建 AVD（一次）
├── start_emulator.sh         ← 启动模拟器
├── stop_emulator.sh          ← 停止模拟器
├── setup_virtual_mic.sh      ← PulseAudio 虚拟麦
├── install_aisf.sh           ← 安装 AISF
├── run_e2e.sh                ← 一键全流程
├── linux_audio.py            ← 灌麦 / 录音工具
├── test_aisf_voice.py        ← 语音 E2E 测试
├── sdk/                      ← Android SDK（下载生成）
├── apk/                      ← AISF APK
├── downloads/                ← 下载缓存
└── runs/                     ← 测试结果
```

---

## 六、常见问题（小白向）

### Q1：`download_deps.sh` 很慢或失败？

- 检查网络能否访问 `dl.google.com` 和 `ci.android.com`
- 可设置代理后重试
- 或在其他机器下载后，把 `sdk/` 和 `downloads/` 整包拷贝过来

### Q2：模拟器启动后 SIGSEGV 崩溃？

openEuler / EulerOS 上 Google 官方 ARM 模拟器有已知兼容问题（线程局部存储冲突）。

**处理顺序：**

1. 看 `runs/emulator.log` 是否有 `SIGSEGV`
2. 换较新的 emulator 包（改 `download_deps.sh` 里 `EMULATOR_URL`）
3. 若仍失败：改用 **ARM 真机** adb 测试，或换 **Ubuntu 22.04 ARM64** 宿主机

### Q3：`INSTALL_FAILED_NO_MATCHING_ABIS`？

说明当前模拟器不是 ARM64。确认 AVD 用的是 `arm64-v8a` 系统镜像，不要用 x86 模拟器。

### Q4：虚拟麦配置了但 App 没反应？

```bash
# 确认默认输入源
pactl get-default-source
# 应包含 VirtualMic.monitor

# 重新打开模拟器麦克风注入
adb -s emulator-5554 emu avd hostmicon

# 手动试播
paplay --device=VirtualMic ../audio_samples/weather_question.wav
```

### Q5：没有 `reply_recorded.wav`？

AISF 不会写 `output.wav` 到文件系统，回复靠 **宿主机录音**：

- 确认装了 `ffmpeg` 和 `pulseaudio`
- 看 `result.json` 里 `reply_capture_error`
- 服务器无音频服务时，先执行：`pulseaudio --start`

### Q6：找不到语音按钮？

测试会保存 `ui_before.xml`。先手动看界面：

```bash
adb shell am start -n com.huawei.aisftvassistant/.activity.PhoneLaunchActivity
python3 ../install_and_dump_ui.py --apk apk/aisf_voice_assistant.apk --skip-install
```

把 dump 里的按钮 `resource-id` 反馈给维护者，可写进测试脚本。

### Q7：权限弹窗？

```bash
adb shell pm grant com.huawei.aisftvassistant android.permission.RECORD_AUDIO
adb shell appops set com.huawei.aisftvassistant RECORD_AUDIO allow
```

---

## 七、环境变量（可选）

```bash
export ANDROID_SDK_ROOT=/data/android-sdk-aisf   # 自定义 SDK 路径
export AISF_AVD_NAME=MyAISF_AVD                  # 自定义 AVD 名
export AISF_SERIAL=emulator-5554                 # adb 设备号
export AISF_APK=/path/to/your.apk                # 自定义 APK 路径
export AISF_VIRTUAL_MIC=VirtualMic             # 虚拟麦名称
```

---

## 八、成功标准

一次测试 **基本成功** 应满足：

| 检查项 | 说明 |
|--------|------|
| `result.json` 里 `"ok": true` | 流程跑通 |
| `seen_ui_text` 非空 | UI 上出现新文本（识别/回复） |
| `reply_recorded.wav` 存在且 > 1KB | 录到回复语音 |
| 截图 `02_recording.png` | 录音过程中界面正常 |

---

## 九、和方案 B（voice-io-lab）对照

| | AISF（本目录） | 方案 B（voice-io-lab） |
|--|----------------|------------------------|
| APK | 外部成品 AISF | 自己编译 VoiceAssistant |
| 包名 | `com.huawei.aisftvassistant` | `com.testappgui.voiceassistant` |
| 宿主机 | EulerOS / Linux ARM64 | Windows x86_64 |
| 灌麦 | PulseAudio VirtualMic | VB-Cable |
| 输出 | ffmpeg 环回录音 | adb 拉 App 内 output.wav |

---

## 十、联系 / 排错信息收集

出问题请打包这些文件：

```bash
tar czf aisf-debug.tgz runs/emulator.log runs/*/result.json runs/*/ui_before.xml
```

并附上：

```bash
uname -a
cat /etc/os-release
adb devices
ls -l /dev/kvm
python3 check_setup.py
```
