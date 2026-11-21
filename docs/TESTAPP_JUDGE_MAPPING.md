# TestAppGUI 评判模块对照（echo-eval）

> 源码位置：`vendor/testApp/`（自 `TestAppGUI-master/testApp` 导入）  
> 本文只关注**效果评判（Oracle）**，不展开 WebServer / APK / 模拟器整仓。

---

## 1. 评判相关文件地图

| 文件 | 作用 |
|------|------|
| `webserverV2/llm_eval.py` | 主 Oracle：截图 VL、多帧 gui_poll、回复+图、多轮 QA |
| `webserverV2/local_adb_runner/llm_eval.py` | 精简自包含版（单图 / 回复+图 / QA） |
| `webserverV2/local_adb_runner/case_eval.py` | 用例目录编排：取图、组 replies、写 `evaluation.json` |
| `webserverV2/local_adb_runner/runner_core.py` | 跑完 case 后调用 `evaluate_case_dir` |
| `webserverV2/local_adb_runner/device_app.py` | ADB `screencap`（本仓参考的是截图，不是 screenrecord） |
| `configs/model.yaml` | `models.vl_eval`（OpenAI 兼容网关，如豆包） |

---

## 2. 四种评判模式

### 2.1 `evaluate(image, exceptions)` — 终态单图

- 输入：最终截图 + `exceptions` 期望列表  
- Prompt：严格 GUI 评判员；逐步反思；全部步骤成功才整体成功  
- 输出：JSON 数组

```json
[
  {"expectation":"...", "think":"...", "result":"success|failed"},
  {"final_result":"...", "think":"...", "result":"success|failed"}
]
```

### 2.2 `evaluate_reply_and_image` — 回复文本 + 终态图（local_adb 默认）

- 输入：截图 + exceptions + DeviceSDK 对话回复轮次  
- 规则：声称成功但截图未达成 → failed；截图已达成但回答含糊 → 可 success  
- `case_eval.evaluate_case_dir` 走此模式，产物 `evaluation.json`

### 2.3 `evaluate_qa_rounds` — 纯文本多轮

- 输入：`user_message` / `expected` / `actual`  
- 无图；空回答直接 failed  

### 2.4 `evaluate_gui_poll_images` — 过程多帧（最接近「录屏抽帧」）

- 输入：按时间排序的多张 `gui_poll_*` 截图（最多抽样 16，保留首尾）  
- 从严：证据不足 → failed  
- `final_result.evidence[]` 必须引用**真实文件名**  
- 后处理：证据接地（单图重写 reason）、引用修复、占位成功拒绝  

对语音助手场景：**录屏 → 均匀抽帧 → 等价于 gui_poll 序列**，应优先对齐本模式。

---

## 3. 通过判定（`parse_evaluation_passed`）

1. 若有 `passed` 字段，直接用  
2. 若有 `round` 条目，任一非 `success` → false  
3. 找带 `final_result` 的条目，`result == "success"` 才通过  
4. 否则所有非 final 步骤均为 `success` 才通过  

**无 `uncertain` 态**；不确定在 gui_poll prompt 里被要求判 `failed`。

---

## 4. 配置契约

```yaml
models:
  vl_eval:
    api_format: "openai"
    base_url: "..."
    api_key: "..."
    model_id: "..."   # 需支持 vision
    timeout: 120
```

环境变量：`VL_EVAL_CONFIG` 可覆盖配置路径；`VL_EVAL_TIMEOUT_SEC` 覆盖超时。

密钥：仓库内请用 `model.yaml.example`；真实 `model.yaml` 已加入 `.gitignore`。

---

## 5. 产物 `evaluation.json`（local_adb）

```json
{
  "mode": "reply_and_image",
  "source": "doubao_vl_eval",
  "image": "zz_after.jpg",
  "exceptions": ["..."],
  "rounds": [{"round": 1, "user_message": "...", "reply_text": "..."}],
  "result_raw": "...",
  "result_parsed": [ ... ],
  "passed": true,
  "timestamp": 0
}
```

截图优选名：`final_result.*` → `zz_after.*` → `result_final.*` → 其余按 mtime。

---

## 6. 映射到 echo-eval

| TestAppGUI | echo-eval |
|------------|-----------|
| `exceptions` / `expections` | `cases/*.yaml` → `expect.must_see` / `goal`（导出为 exceptions 列表） |
| `screencap` / `gui_poll_*` | `capture/screenrecord` + ffmpeg 抽帧 |
| `evaluate_gui_poll_images` | `judge.evaluate_frames`（首期主路径） |
| `evaluate_reply_and_image` | 可选：ASR/助手回复文本 + 末帧 |
| `evaluate_qa_rounds` | 可选：仅文本金标对比 |
| `evaluation.json` | `artifacts/.../judgment.json`（字段兼容适配器） |
| `models.vl_eval` | `configs/model.yaml` + OpenAI 兼容客户端 |
| `parse_evaluation_passed` | `judge.schema.parse_passed` |
| 无 uncertain | 首期与参考仓一致用 success/failed；需要时可后加 uncertain |

### 建议实现顺序（评判）

1. 移植 `llm_eval.evaluate_gui_poll_images` 的 prompt + 抽样 + JSON 解析（可先不做 evidence ground/repair）  
2. 移植 `parse_evaluation_passed`  
3. 帧序列来自 screenrecord 抽帧，文件名写入 evidence  
4. 用例 YAML 的期望转成 `exceptions: string[]`  
5. 可选再接 `evaluate_reply_and_image`（若后续能拿到助手回复文本）  

### 刻意不搬

- `webserver_v2.py` 任务调度 / 结果页  
- APK TestApp / DeviceSDK Demo 点击执行  
- Excel 用例转换（Phase 5 数据集可另设计）  

---

## 7. 与原开发计划的差异修正

| 原计划假设 | TestApp 实际 | 调整 |
|------------|--------------|------|
| 三态 pass/fail/uncertain | 二态 success/failed（从严） | 评判输出对齐二态；对外报告可映射 pass/fail |
| 主证据为视频本身 | 主证据为截图序列 | 录屏仅作采集手段，送模型的是抽帧 |
| 自研 prompt | 已有成熟 VL prompt + 证据校准 | **复用/薄封装**，不重写 |
| 无线 ADB + 外放语音 | local_adb 偏 USB/模拟器 + DeviceSDK 文本轮次 | 连接与刺激仍按 echo-eval 自研；评判对齐 TestApp |

---

## 8. 安全提示

导入的 `configs/model.yaml` 曾含明文 API Key，已脱敏为占位符，并加入 `.gitignore`。请在本机本地填写密钥，并视情况在网关侧轮换已暴露的 Key。
