# echo-eval — 语音助手无线 ADB 自动化评测

通过无线 ADB 连接语音助手设备，自动播放语音、录屏抽帧，并用多模态大模型（对齐 TestAppGUI 评判）依据「输入 + 画面证据」判定效果。后续可扩展为数据集文本 → TTS → 播放 → 批量评测。

## 文档

| 文档 | 说明 |
|------|------|
| [`docs/DEVELOPMENT_PLAN.md`](docs/DEVELOPMENT_PLAN.md) | 分阶段开发计划 |
| [`docs/TESTAPP_JUDGE_MAPPING.md`](docs/TESTAPP_JUDGE_MAPPING.md) | TestAppGUI 评判模块对照与接入映射 |

## 参考源码

`vendor/testApp/` 来自 TestAppGUI 的 `testApp` 目录。评判请只关注：

- `webserverV2/llm_eval.py` — VL / 多帧 / 回复+图 / QA  
- `webserverV2/local_adb_runner/case_eval.py` — 用例级评判与 `evaluation.json`  

配置模板：`vendor/testApp/configs/model.yaml.example`（复制为 `model.yaml` 并填写密钥，勿提交密钥）。

## 状态

已完成：开发计划 + TestApp 评判对照。下一步：Phase 0–1 骨架与录屏抽帧，再薄封装移植 `evaluate_gui_poll_images`。
