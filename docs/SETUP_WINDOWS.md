# Windows 端到端环境安装与使用

面向 **PC1 一体机**（与语音助手设备同局域网）：本机跑 `echo-eval` + Web 控制台「秒控测试平台」，经无线 ADB 录屏/播语音/评判。

推荐终端：**Git Bash**（下文命令默认 Bash；PowerShell 需把 `export` 换成 `set` / `$env:`）。

相关文档：[`WEB_CONSOLE.md`](./WEB_CONSOLE.md) · [`PHASE4_RUN.md`](./PHASE4_RUN.md) · [`LATENCY_MOS.md`](./LATENCY_MOS.md)

---

## 0. 你需要准备什么

| 类别 | 组件 | 是否必须 | 用途 |
|------|------|----------|------|
| 运行时 | Python **3.9+** | 必须 | CLI / 评测管线 / 环境检查 |
| 运行时 | Node.js **18+**（建议 20 LTS） | 必须（用控制台时） | Next.js 控制台 |
| 运行时 | Git | 必须 | 拉代码 |
| 系统工具 | Android **platform-tools**（`adb`） | 必须 | 无线连接、录屏、Home、预置 ADB |
| 系统工具 | **FFmpeg**（含 `ffplay` 更佳） | 必须 | 抽帧、edge-tts 转 wav、MOS 抽音/录麦 |
| 系统工具 | **scrcpy** 5.x | 推荐（带声录屏） | 本机录 `capture.mp4`（含内录音频）；华为需 AAC |
| Python 包 | `edge-tts` | 强烈建议 | 默认在线 TTS；失败可回退 Windows SAPI |
| Python 包 | `openpyxl` | 读 `.xlsx` 时必须 | Excel 数据集；CSV 可不装 |
| Python 包 | `PyYAML` | 可选 | 配置解析更稳；未装时有内置子集解析 |
| 配置 | `configs/device.yaml` | 必须 | 设备 IP、adb/ffmpeg 路径、TTS/MOS/预置等 |
| 配置 | `configs/model.yaml` | VL 评判必须 | 网关 `api_key` / `base_url` / `model_id`；可先用 mock |
| 配置 | `configs/packages.yaml` | 预置打开 App 时需要 | 显示名 → 包名 |
| 硬件 | 手机/助手无线调试已开 | 真机测评必须 | `adb connect` |
| 硬件 | PC 扬声器 +（可选）麦克风 | 刺激音 / MOS | 扬声器朝设备麦；MOS 用主机麦录设备外放 |

`adb shell screenrecord` **不录设备外放声音**；TTS MOS 依赖主机麦克风另录，见 [`LATENCY_MOS.md`](./LATENCY_MOS.md)。

---

## 1. 一键核对：本机是否已齐

在仓库根目录：

```bash
cd /d/code/voiceass/echo-eval   # 改成你的路径
export PYTHONPATH=src

python --version          # >= 3.9
node --version            # >= 18
npm --version

adb version
where adb

ffmpeg -version
ffplay -version
where ffmpeg

python -m echo_eval doctor
python scripts/env_doctor.py --quick
```

- `doctor: OK`：adb / ffmpeg / 基础配置可用  
- `env_doctor`：控制台同款检查（config / adb / device / ffmpeg / tts / vl / mos…）

---

## 2. 安装基础运行时

### 2.1 Python 3.9+

1. https://www.python.org/downloads/windows/  
2. 安装时勾选 **Add python.exe to PATH**  
3. 新开终端：`python --version`、`python -m pip --version`

若有多个 Python，请固定用同一个解释器装包与跑控制台（控制台会调本机 `python`）。

### 2.2 Node.js 18+（Web 控制台）

1. https://nodejs.org/（LTS）或：

```powershell
winget install --id OpenJS.NodeJS.LTS -e --source winget
```

2. 新开终端：`node -v`、`npm -v`

### 2.3 Git

```powershell
winget install --id Git.Git -e --source winget
```

用 **Git Bash** 打开仓库目录最省事。

---

## 3. 拉取代码与配置文件

```bash
cd /d/code/voiceass
git clone <你的仓库 URL> echo-eval
cd echo-eval
git checkout main
git pull origin main
```

首次配置（仓库里通常已有本地 `device.yaml` / `model.yaml`；若没有则从示例复制）：

```bash
cp configs/device.yaml.example configs/device.yaml
cp configs/model.yaml.example configs/model.yaml
```

> **注意**：`configs/device.yaml`、`configs/model.yaml`、`configs/packages.yaml` 常含本机路径或密钥，勿强行用远程覆盖。更新代码时若有冲突，可先：
>
> ```bash
> git stash push -u -m "local configs" -- configs/device.yaml configs/model.yaml configs/packages.yaml
> git pull origin main
> git stash pop
> ```

---

## 4. 安装 Python 依赖

在仓库根目录：

```bash
export PYTHONPATH=src

# 推荐：可编辑安装（写入 edge-tts 等）
python -m pip install -e .

# 或最小安装
python -m pip install "edge-tts>=7.0.0"

# 读 Excel（.xlsx）时需要
python -m pip install openpyxl

# 可选：完整 YAML
python -m pip install "PyYAML>=6.0"
```

国内 pip 慢时可加镜像，例如：

```bash
python -m pip install -e . -i https://pypi.tuna.tsinghua.edu.cn/simple
python -m pip install openpyxl -i https://pypi.tuna.tsinghua.edu.cn/simple
```

验证：

```bash
python -c "import edge_tts; print('edge-tts OK', getattr(edge_tts,'__version__','?'))"
python -c "import openpyxl; print('openpyxl OK')"
```

TTS 说明：

- 默认 `tts.backend: edge`（需访问 `speech.platform.bing.com`）  
- 失败时若 `tts.fallback_sapi: true`，自动回退 **Windows 本机 SAPI**  
- 控制台可「预合成语音」写入 `assets/tts_library/`；运行时优先命中库

---

## 5. 安装 ADB（platform-tools）

### 方式 A：官网 zip（推荐）

1. https://developer.android.com/tools/releases/platform-tools  
2. 下载 **SDK Platform-Tools for Windows**  
3. 解压到固定目录，例如 `D:\software\platform-tools`  
4. 确认有 `adb.exe`，将该目录加入系统 **PATH**  
5. **新开终端**：`adb version`

### 方式 B：winget

```powershell
winget install --id Google.PlatformTools -e --source winget
```

### 写入 `configs/device.yaml`

```yaml
adb:
  host: "192.168.x.x"          # 设备无线调试 IP
  port: 5555
  adb_path: "D:/software/platform-tools/adb.exe"  # where adb 得到的路径
```

### 无线连接

手机与 PC 同 Wi‑Fi；开启**无线调试**，记下 IP:端口。

```bash
adb connect 192.168.x.x:5555
adb devices
# 期望: 192.168.x.x:5555    device
```

Git Bash 下若设备路径被改写，可加 `MSYS_NO_PATHCONV=1`（`echo-eval` 内部已处理常见情况）。

---

## 6. 安装 FFmpeg（含 ffplay）

用途：录屏后抽帧、edge-tts mp3→wav、MOS 主机麦录音。

### 方式 A：winget（推荐）

```powershell
winget install --id Gyan.FFmpeg -e --source winget
```

新开终端：`ffmpeg -version`、`ffplay -version`、`where ffmpeg`

### 方式 B：手动 zip

1. https://www.gyan.dev/ffmpeg/builds/  
2. 下载 **ffmpeg-release-essentials.zip**  
3. 解压后把 `bin` 加入 PATH（需含 `ffmpeg.exe`、`ffplay.exe`）

### 写入配置

```yaml
capture:
  ffmpeg_path: "C:/ffmpeg/bin/ffmpeg.exe"
  # 或 WinGet Links 下的 ffmpeg.exe；留空则依赖 PATH
```

无 `ffplay` 时，Windows 播放 `.wav` 可回退 `winsound`。

---

## 7. VL 评判与包名映射

### 7.1 `configs/model.yaml`

从 `model.yaml.example` 复制后填写（结构同 TestApp）：

```yaml
models:
  vl_eval:
    api_format: "openai"
    base_url: "https://YOUR_GATEWAY/v1"
    api_key: "YOUR_API_KEY"
    model_id: "YOUR_VL_MODEL_ID"
    temperature: 1
    timeout: 120
    max_retries: 3
```

也可用环境变量覆盖路径：

```bash
export VL_EVAL_CONFIG="D:/path/to/model.yaml"
```

未配置时可用 `--backend mock` 或控制台选 mock，不调真实 VL。

### 7.2 `configs/packages.yaml`

预置「打开爱奇艺」等依赖显示名 → 包名。按真机核对：

```bash
adb shell pm list packages | grep -i qiyi
```

---

## 8. scrcpy（带声音录屏，推荐）

```powershell
winget install --id Genymobile.scrcpy -e --source winget
```

新开终端：`scrcpy --version`。在 `device.yaml`：

```yaml
capture:
  backend: scrcpy
  scrcpy_path: ""   # 或 scrcpy.exe 绝对路径
  # 华为 Android 12：必须指定 AAC（默认 opus 会失败）
  scrcpy_audio_codec: aac
  scrcpy_audio_encoder: OMX.google.aac.encoder
mos:
  source: video               # 只从 mp4 抽音
  ignore_non_speech: true     # 忽略歌曲/视频床底，只评语音
```

指定 adb（与穿刺相同）：

```bash
export ADB="/d/software/platform-tools/adb.exe"
```

回退旧无声录屏：`capture.backend: screenrecord`。

## 9. TTS MOS 主机麦（仅 host_mic / 降级）

`mos.source: host_mic` 或 scrcpy 无音轨时需要。Windows 查设备：

```bash
ffmpeg -list_devices true -f dshow -i dummy
```

```yaml
mos:
  source: host_mic
  input_format: dshow
  input_device: audio=麦克风 (Realtek ...)
```

不测 MOS：`mos.enabled: false`。

---

## 10. 启动 Web 控制台（日常主入口）

```bash
cd /d/code/voiceass/echo-eval
export PYTHONPATH=src

cd web
npm install          # 首次或 package.json 变更后
npm run dev
```

浏览器打开：**http://127.0.0.1:58261**  
（不要用 Cursor 云端 Port 预览；局域网其它机器：`http://<PC1的IP>:58261`）

自检：

```bash
curl -s http://127.0.0.1:58261/api/ping
# 期望 platform=win32，hostRole=local_pc，repoRoot 在本机盘符下
```

### 推荐使用顺序

1. **环境检查** `/env` → 重新检查  
2. **数据集** `/datasets` → 上传或选样例  
3. （可选）**预合成语音** `/datasets/tts`  
4. **预置校准** `/datasets/calibrate`  
5. **开始测试** `/run` → 设参数 → 开测（扬声器朝设备麦）  
6. **任务管理** `/runs` → 回看；可「清理媒体」瘦身（删 mp4/截帧/wav，保留结果）

演示任务可不连真机预览界面。细节见 [`WEB_CONSOLE.md`](./WEB_CONSOLE.md)。

---

## 11. CLI 快速验收（不经过控制台）

```bash
cd /d/code/voiceass/echo-eval
export PYTHONPATH=src

python -m echo_eval doctor
python -m echo_eval connect
python -m echo_eval record --seconds 10
python -m echo_eval spike --seconds 10 --audio assets/audio/beep_1s.wav

# mock 端到端（不调 VL）
python -m echo_eval run --excel cases/examples/sample_weather.csv \
  --case-id demo_weather_001 --backend mock

# 真实 VL（需 model.yaml）
python -m echo_eval run --excel cases/examples/sample_weather.csv \
  --case-id demo_weather_001 --backend vl
```

期望：

- `doctor: OK`、`connect: OK`  
- `artifacts/.../capture.mp4` 可播；有抽帧时 `frames/` 非空  
- `spike` 本机有声音，且有 `timeline.json`

更多编排见 [`PHASE4_RUN.md`](./PHASE4_RUN.md)。

---

## 12. 常见问题

| 现象 | 处理 |
|------|------|
| 终端有 `adb`，`doctor` 找不到 | `device.yaml` 设 `adb.adb_path` 绝对路径 |
| 找不到 `ffmpeg` | 设 `capture.ffmpeg_path`；新开终端确认 PATH |
| `pip` SSL / 代理失败 | 换清华源；或仅 `PYTHONPATH=src` + 已装好的 site-packages |
| edge-tts 合成失败 | 检查出网；或依赖 `fallback_sapi` / 预合成库 |
| 读 xlsx 报缺 openpyxl | `python -m pip install openpyxl` |
| 无线 `unauthorized` / `offline` | 手机点允许调试；`adb disconnect` 后再 `connect` |
| `screenrecord` 路径异常 | 用 `echo-eval` 封装，或 `MSYS_NO_PATHCONV=1` |
| 控制台环境检查无反应 | 确认地址是 `127.0.0.1:58261`；看 Git Bash 里 `[echo-eval]` 日志；`curl` 测 `/api/ping` |
| ping 显示 `platform=linux` | 打到了云端预览，改回本机 URL |
| MOS 拾音失败 | scrcpy+`mos.source=auto/video` 可不配麦；否则配 `mos.input_device` |
| scrcpy opus 编码失败 | 设 `scrcpy_audio_codec: aac` + `OMX.google.aac.encoder` |
| scrcpy 找不到 adb | `export ADB=.../adb.exe`；`scrcpy_path` 指向 scrcpy.exe |
| 预置打不开 App | 核对 `packages.yaml` 包名与真机安装 |
| 磁盘被 artifacts 撑满 | 任务管理「清理媒体」；勿删 `artifacts/precondition_cache` |

---

## 13. 清单（装完打勾）

- [ ] Python 3.9+、`pip` 可用  
- [ ] `pip install -e .`（或至少 `edge-tts`）；xlsx 则已装 `openpyxl`  
- [ ] Node 18+，`web/` 下 `npm install` 成功  
- [ ] `adb` / `ffmpeg` / `scrcpy` 在 PATH 或已写入 `device.yaml`  
- [ ] `adb devices` 为 `device`  
- [ ] `configs/device.yaml` 主机/端口正确；`capture.backend: scrcpy` + AAC  
- [ ] （可选）`model.yaml` 已填密钥  
- [ ] （仅 host_mic）MOS `input_device` 已配  
- [ ] `python -m echo_eval doctor` → OK  
- [ ] `npm run dev` → `http://127.0.0.1:58261` 可开，`/api/ping` 为本机  
