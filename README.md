# 语音助手无线 ADB 自动化评测

通过无线 ADB 连接语音助手设备，自动播放语音、录屏，并用多模态大模型依据「输入 + 录屏证据」评判效果。后续可扩展为数据集文本 → TTS → 播放 → 批量评测。

## 文档

完整开发计划见 [`docs/DEVELOPMENT_PLAN.md`](docs/DEVELOPMENT_PLAN.md)。

计划覆盖：

1. 无线 ADB 连接与会话保活  
2. 语音刺激（本机播放，预留 TTS）  
3. ADB `screenrecord` + 抽帧  
4. LLM / VLM Oracle（对齐 TestAppGUI 评判思路）  
5. 用例编排、报告与数据集扩展  

## 参考

评判模块设计对齐 [WeTestAI1/TestAppGUI `testApp`](https://github.com/WeTestAI1/TestAppGUI/tree/master/testApp) 的效果评判部分。若该仓可导出，请将 prompt / schema / 调用封装放入对照后接入 `judge/`。

## 状态

当前仓库交付物为**开发计划与架构约定**；实现按计划 Phase 0 → Phase 5 推进。
