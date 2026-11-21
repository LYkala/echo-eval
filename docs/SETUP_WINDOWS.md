# Windows 环境安装：ADB 与 FFmpeg

本机穿刺（UCRT64 / Git Bash / PowerShell）所需工具。`echo-eval` 通过 `configs/device.yaml` 读取路径；若 Windows 版 Python 找不到命令，请在配置里填写绝对路径。

## 1. 确认是否已安装

```bash
adb version
where adb

ffmpeg -version
ffplay -version
where ffmpeg
where ffplay
```

能输出版本与路径即表示可用。

也可用项目自检：

```bash
export PYTHONPATH=src
python -m echo_eval doctor
```

---

## 2. 安装 ADB（platform-tools）

### 方式 A：官网 zip（推荐）

1. 打开：https://developer.android.com/tools/releases/platform-tools  
2. 下载 **SDK Platform-Tools for Windows**  
3. 解压到固定目录，例如：`D:\software\platform-tools`  
4. 确认存在：`D:\software\platform-tools\adb.exe`  
5. 将该目录加入系统 **PATH**（环境变量 → Path → 新建）  
6. **新开终端** 后执行 `adb version`

### 方式 B：winget

```powershell
winget install --id Google.PlatformTools -e --source winget
```

装完后新开终端验证。

### 写入 echo-eval 配置

编辑 `configs/device.yaml`：

```yaml
adb:
  host: "192.168.43.244"
  port: 5555
  adb_path: "D:/software/platform-tools/adb.exe"   # 按本机 where adb 修改
```

### 无线连接备忘

```bash
adb connect 192.168.x.x:5555
adb devices
# 期望: 192.168.x.x:5555    device
```

Git Bash / UCRT64 下向设备传 POSIX 路径时，若遇路径被改写，可加：

```bash
MSYS_NO_PATHCONV=1 adb -s IP:5555 shell ls /sdcard
```

（`echo-eval` 内部已处理该问题。）

---

## 3. 安装 FFmpeg（含 ffplay）

抽帧需要 `ffmpeg`；本机播放刺激音频优先用 `ffplay`（无 ffplay 时 Windows 可用 `winsound` 播放 `.wav`）。

### 方式 A：winget（推荐）

```powershell
winget install --id Gyan.FFmpeg -e --source winget
```

新开终端后：

```bash
ffmpeg -version
ffplay -version
where ffmpeg
```

### 方式 B：手动 zip

1. 打开：https://www.gyan.dev/ffmpeg/builds/  
2. 下载 **ffmpeg-release-essentials.zip**  
3. 解压到例如：`C:\ffmpeg`  
4. 将 `C:\ffmpeg\bin` 加入 PATH  
5. 确认：`C:\ffmpeg\bin\ffmpeg.exe`、`ffplay.exe`

### 写入 echo-eval 配置

```yaml
capture:
  ffmpeg_path: "C:/Users/Administrator/AppData/Local/Microsoft/WinGet/Links/ffmpeg.exe"
  # 或: "C:/ffmpeg/bin/ffmpeg.exe"
```

`ffplay` 一般与 `ffmpeg` 同目录；doctor 会尝试自动发现。

### 暂不安装 FFmpeg 时

可跳过抽帧：

```bash
export PYTHONPATH=src
python -m echo_eval record --no-frames
python -m echo_eval spike --no-frames
```

---

## 4. 推荐自检流程

```bash
cd /d/code/voiceass/echo-eval   # 你的仓库路径
export PYTHONPATH=src

python -m echo_eval doctor
python -m echo_eval connect
python -m echo_eval record --seconds 10
python -m echo_eval spike --seconds 10 --audio assets/audio/beep_1s.wav
```

期望：

- `doctor: OK`
- `connect: OK`
- `artifacts/capture_*/capture.mp4` 可播放
- `artifacts/capture_*/frames/frame_*.jpg` 非空（未加 `--no-frames` 时）
- `spike` 时本机可听到音频，且有 `timeline.json`

---

## 5. 常见问题

| 现象 | 处理 |
|------|------|
| 终端里 `adb` 可用，但 `doctor` 找不到 | 在 `device.yaml` 设置 `adb.adb_path` 绝对路径 |
| 同样找不到 `ffmpeg` | 设置 `capture.ffmpeg_path` |
| `pip install` SSL / 代理错误 | 本项目 Phase 0–2 **无需 pip**；`export PYTHONPATH=src` 即可 |
| `screenrecord` 报 Must specify output file | Git Bash 路径转换；用 `echo-eval record` 或 `MSYS_NO_PATHCONV=1` |
| 无线 `unauthorized` / `offline` | 设备点允许调试；重开无线调试或 `adb disconnect` 后再 `connect` |
