# Echo Eval Web 控制台（PC1 一体机）

首期全部跑在 **PC1**（与设备同网）：浏览器 + Next.js + 本机调用 `echo-eval`。

## 功能

- 上传 Excel/CSV 数据集
- 环境检查（adb / 设备 / ffmpeg / TTS / VL 配置）
- 开始 / 停止测试（TTS 外放 + 录屏 + 评判）
- 任务列表与用例详情：截帧、步骤音频、评判结果

## 本机启动（Windows Git Bash）

```bash
cd /d/code/voiceass/echo-eval
git checkout cursor/phase5-web-console-4bfa   # 或 main（合入后）
git pull

# 可选：VL 评判
export VL_EVAL_CONFIG="D:/code/voiceass/TestAppGUI-master/testApp/configs/model.yaml"
export PYTHONPATH=src

cd web
npm install
npm run dev
```

浏览器打开：`http://127.0.0.1:3847`  
局域网其它设备可用：`http://<PC1的IP>:3847`

## 使用顺序

1. 打开工作台 → **重新检查** 环境  
2. 上传数据集或选用内置 `sample_weather.csv`  
3. 选 backend（`mock` 联调 / `vl` 真评判）→ **开始测试**  
4. 扬声器朝向设备麦克风；任务结束后点用例查看截帧与结果  

环境未就绪时可用 **打开演示任务** 预览界面（不连真机）。

## 目录

```
artifacts/web/datasets/   # 上传的数据集
artifacts/web/jobs/       # 任务元数据与日志
artifacts/<run>/          # echo-eval 原有用例产物
```
