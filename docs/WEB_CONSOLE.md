# Echo Eval Web 控制台（PC1 一体机）

首期全部跑在 **PC1**（与设备同网）：浏览器 + Next.js + 本机调用 `echo-eval`。

## 功能

- 上传 Excel/CSV 数据集；列表显示上传时间，支持下载与删除（内置样例不可删）
- 内置样例含 `sample_weather.csv` 与 `sample_v012_system_iqiyi_10.csv`（预置校准）
- 环境检查（adb / 设备 / ffmpeg / TTS / VL 配置）
- **预置校准**：查看覆盖情况 / 开始校准 / 强制重试 failed；与开测互斥；日志流 + 步骤汇总
- 开始 / 停止测试（TTS 外放 + 录屏；评判默认后台并行，不挡下一条；每条结束后按 Home 回桌面）；预置未就绪仅提示不硬拦
- 任务进度区分「设备」与「评判」两条线；用例详情含截帧、步骤音频、评判结果

## 本机启动（Windows Git Bash）

```bash
cd /d/code/voiceass/echo-eval
git checkout main
git pull

# 可选：VL 评判（默认用 configs/model.yaml；也可覆盖路径）
# export VL_EVAL_CONFIG="D:/code/voiceass/TestAppGUI-master/testApp/configs/model.yaml"
export PYTHONPATH=src

cd web
npm install
npm run dev
```

浏览器打开：`http://127.0.0.1:58261`（不要用 Cursor 云端 Port 预览，否则会打到云端 Linux）  
局域网其它设备可用：`http://<PC1的IP>:58261`

自检是否本机：

```bash
curl -s http://127.0.0.1:58261/api/ping
# 期望 platform=win32，hostRole=local_pc，repoRoot 在 D:/… 之类
# 若 platform=linux 且 repoRoot 含 /agent/repos → 打到了云端
```

## 界面结构

左侧栏（秒控测试平台）：

1. **环境检查** `/env`
2. **数据集管理** → 数据集 `/datasets` · 预合成语音 `/datasets/tts` · 预置校准 `/datasets/calibrate`
3. **开始测试** `/run`
4. **任务管理** `/runs`

## 使用顺序

1. **环境检查** → 重新检查（分步进度；设备短超时）  
2. **数据集** → 选用内置 `sample_v012_…` 或上传（用例编号默认留空=全部）  
3. （可选）**预合成语音** → 预合成当前数据集  
4. **预置校准** → 覆盖情况 → 开始校准  
5. **开始测试** → 参数与评判项 → 开测（校准进行中会禁用；有缺口黄条提示仍可开测并 skip）  
6. 扬声器朝向设备麦克风；语音由本机进程播放，切换页面不中断任务  
7. **任务管理** / 任务详情回看截帧与结果  

环境未就绪时可用 **打开演示任务** 预览界面（不连真机）。演示含两个现场样例：`SYS2-009`（取消静音 PASS）与 `IQ-SCR-026`（热播榜 FAIL）；真实录屏/截帧/评判，ASR 与时延为仿真，MOS 跳过。素材在 `web/fixtures/demo_sys2_009/`、`web/fixtures/demo_iq_scr_026/`。  
若检查一直失败，可在仓库根目录手动跑：`python scripts/env_doctor.py --quick`

### 环境检查无反应时

1. 确认浏览器地址是 `http://127.0.0.1:58261`（不要用 `file://`）  
2. 看页面里的「调试日志」黑框是否出现 `hydration OK` / `用户点击`  
3. 看运行 `npm run dev` 的 Git Bash 窗口，搜索 `[echo-eval]`：  
   - 有 `[ping]` / `[env/step]` → API 通了  
   - 完全没有 → 浏览器没打到这个 dev server（端口/旧进程）  
4. 手动测 API：

```bash
curl http://127.0.0.1:58261/api/ping
curl "http://127.0.0.1:58261/api/env/step?step=config"
```

## 目录

```
artifacts/web/datasets/   # 上传的数据集
artifacts/web/jobs/       # 任务元数据与日志
artifacts/<run>/          # echo-eval 原有用例产物
```
