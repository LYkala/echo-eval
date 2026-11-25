# ASR 准确率评判（P1）

在正式跑测「GUI 成功率」之外，**并行**增加 ASR 准确率指标；二者互不影响。

## 流程

```text
每句 TTS 播完
  → 每 0.5s screencap（最长 window_sec=4s，约 8 张）
  → 底部条相对首帧连续变化达 2 帧可提前停采
  → 后台 VL：综合多帧读出底部识别气泡，对照播报文案
  → 写入 asr/step_XX.json + asr_summary.json；report.md 单独一节
```

时间线字段：`play_ended_at` / `play_end_offset_sec` / `asr_poll`（为 P2 时延预留「说完」时刻）。

## 规则

- **只读**屏幕下方语音气泡中的识别文。
- **忽略**固定提示，默认：`我还在听，说再见退出`（可配 `asr.ignore_texts`）。
- 比对忽略标点与空白（`点击电影。` ≈ `点击电影`）。
- 多帧中始终无有效识别文，或与播报文案不一致 → 该句 ASR **失败**。
- **每句都评**；用例 ASR 准确率 = 通过句数 / 总句数。

## 配置（`configs/device.yaml`）

见 `asr:` 段。`enabled: false` 可关闭采图与评判。

## 与后续阶段

| 阶段 | 内容 |
|------|------|
| P1（本文） | ASR |
| P2 / P3 | 见 [LATENCY_MOS.md](./LATENCY_MOS.md) |
