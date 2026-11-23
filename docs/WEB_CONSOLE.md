# Echo Eval Web 控制台（PC1 一体机）

首期全部跑在 **PC1**（与设备同网）：浏览器 + Next.js + 本机调用 `echo-eval`。

## 功能

- 上传 Excel/CSV 数据集；列表显示上传时间，支持下载与删除（内置样例不可删）
- 环境检查（adb / 设备 / ffmpeg / TTS / VL 配置）
- 开始 / 停止测试（TTS 外放 + 录屏 + 评判；每条结束后默认按 Home 回桌面）
- 任务列表与用例详情：截帧、步骤音频、评判结果

## 本机启动（Windows Git Bash）

```bash
cd /d/code/voiceass/echo-eval
git checkout main
git pull

# 可选：VL 评判
export VL_EVAL_CONFIG="D:/code/voiceass/TestAppGUI-master/testApp/configs/model.yaml"
export PYTHONPATH=src

cd web
npm install
npm run dev
```

浏览器打开：`http://127.0.0.1:47391`（不要用 Cursor 云端 Port 预览，否则会打到云端 Linux）  
局域网其它设备可用：`http://<PC1的IP>:47391`

自检是否本机：

```bash
curl -s http://127.0.0.1:47391/api/ping
# 期望 platform=win32，hostRole=local_pc，repoRoot 在 D:/… 之类
# 若 platform=linux 且 repoRoot 含 /agent/repos → 打到了云端
```

## 使用顺序

1. 打开工作台 → **重新检查** 环境（会分步显示进度；设备连接使用短超时，避免卡死）  
2. 上传数据集或选用内置 `sample_weather.csv`（用例编号默认留空=跑全部）  
3. 选 backend（`mock` 联调 / `vl` 真评判）→ **开始测试**；「当前任务」会显示用例进度  
4. 扬声器朝向设备麦克风；语音由本机进程播放，切换页面不中断任务  
5. 任务结束后点用例查看截帧与结果  

环境未就绪时可用 **打开演示任务** 预览界面（不连真机）。  
若检查一直失败，可在仓库根目录手动跑：`python scripts/env_doctor.py --quick`

### 环境检查无反应时

1. 确认浏览器地址是 `http://127.0.0.1:47391`（不要用 `file://`）  
2. 看页面里的「调试日志」黑框是否出现 `hydration OK` / `用户点击`  
3. 看运行 `npm run dev` 的 Git Bash 窗口，搜索 `[echo-eval]`：  
   - 有 `[ping]` / `[env/step]` → API 通了  
   - 完全没有 → 浏览器没打到这个 dev server（端口/旧进程）  
4. 手动测 API：

```bash
curl http://127.0.0.1:47391/api/ping
curl "http://127.0.0.1:47391/api/env/step?step=config"
```

## 目录

```
artifacts/web/datasets/   # 上传的数据集
artifacts/web/jobs/       # 任务元数据与日志
artifacts/<run>/          # echo-eval 原有用例产物
```
