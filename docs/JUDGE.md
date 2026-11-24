# Phase 3 评判器说明

对齐 TestAppGUI `evaluate_gui_poll_images`（**E1**：主评判 + 解析 + 本地占位规则；不做证据接地/引用修复）。

与**预置条件 VL 验收**的区分与对照见 [EVALUATION_METHODS.md](./EVALUATION_METHODS.md)。

## 决策摘要

| 项 | 选择 |
|----|------|
| 范围 | 只做评判器（不做完整 Excel runner） |
| 模式 | 多帧截图 + 预期结果（exceptions） |
| 代码 | R2：逻辑迁入 `echo_eval/judge/`，配置兼容 TestApp `models.vl_eval` |
| 后处理 | E1 |
| Mock | M2（有图+有期望 → pass；否则 fail） |

## 配置

默认读取仓库内 **`configs/model.yaml`**（结构与 TestApp `configs/model.yaml` 相同）。  
把 TestApp 里已跑通的 `api_key` / `base_url` / `model_id` 填进去即可。

```bash
# 可选：覆盖路径
export VL_EVAL_CONFIG="D:/code/voiceass/TestAppGUI-master/testApp/configs/model.yaml"
```

## 用法

```bash
export PYTHONPATH=src

# Mock 联调
python -m echo_eval judge --run-dir artifacts/capture_XXX \
  --expect "界面出现天气" --backend mock

# 真实 VL
python -m echo_eval judge --run-dir artifacts/capture_XXX \
  --expect "界面出现天气" --expect "显示温度" --backend vl
```

也可 `--expect-file expects.txt`（一行一条），或 `--image path` 重复指定图片。

退出码：`passed` → 0；`failed` → 1；配置/调用错误或未知 → 2。

## 产物

`{run_dir}/evaluation.json`，字段对齐 TestApp：`result_parsed` / `passed` / `exceptions` / `images`。
