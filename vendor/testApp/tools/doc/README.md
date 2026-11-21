# Excel to JSON 测试用例转换工具

## 概述

本工具用于将Excel格式的测试用例批量转换为TestApp所需的JSON格式测试文件。主要应用于聊天类应用的自动化测试场景。

## 功能特性

- 支持批量转换Excel测试用例
- 自动识别测试步骤列
- 支持按用例集创建文件夹分类
- 可配置目标应用包名和UI元素ID
- 自动生成聊天交互流程（输入->发送->等待回复）
- 支持创建示例Excel模板

## 安装依赖

```bash
pip install openpyxl
```

## 使用方法

### 1. 生成示例Excel模板

```bash
python3 tools/excel_to_json.py --sample tools/template.xlsx
```

### 2. 转换Excel到JSON

```bash
# 基本用法
python3 tools/excel_to_json.py <input_excel> <output_dir>

# 示例
python3 tools/excel_to_json.py tools/testcases.xlsx tools/output/
```

### 3. 自定义参数

```bash
# 指定目标应用包名
python3 tools/excel_to_json.py testcases.xlsx output/ --package com.example.myapp

# 指定UI元素ID
python3 tools/excel_to_json.py testcases.xlsx output/ \
    --input-id "com.example.myapp:id/input_field" \
    --send-id "com.example.myapp:id/send_btn"

# 自定义等待时间和超时
python3 tools/excel_to_json.py testcases.xlsx output/ \
    --wait 20000 \
    --timeout 600000
```

## 命令行参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `input` | 输入Excel文件路径 | 必填 |
| `output` | 输出JSON文件目录 | 必填 |
| `--sample FILE` | 生成示例Excel文件 | - |
| `--package PACKAGE` | 目标应用包名 | com.huawei.aisfdemo |
| `--input-id ID` | 输入框资源ID | com.huawei.aisfdemo:id/editTextMessage |
| `--send-id ID` | 发送按钮资源ID | com.huawei.aisfdemo:id/btnSend |
| `--voice-toggle-id ID` | 语音/文字切换按钮ID | com.huawei.aisfdemo:id/textVoice |
| `--wait MS` | 每条消息后等待时间(毫秒) | 15000 |
| `--timeout MS` | 测试总超时时间(毫秒) | 300000 |

## Excel格式要求

### 必需列

| 列名 | 说明 | 示例 |
|------|------|------|
| 用例编号 | 测试用例唯一标识 | TC-CHAT-001 |
| 用例名称 | 测试用例名称 | 聊天功能测试 |
| 用例集 | 用于分类的文件夹名 | 聊天功能 |
| 描述 | 测试用例描述 | 测试基本聊天功能 |

### 测试步骤列

- 列名格式：`测试步骤1`、`测试步骤2`、`测试步骤3`...
- 每个步骤列的内容是一条聊天消息
- 支持任意数量的步骤列
- 空单元格会被自动跳过

### 可选列

| 列名 | 说明 |
|------|------|
| 包名 | 指定该用例的目标应用包名（覆盖默认值） |

### 示例Excel内容

| 用例编号 | 用例名称 | 用例集 | 描述 | 测试步骤1 | 测试步骤2 | 测试步骤3 |
|----------|----------|--------|------|-----------|-----------|-----------|
| TC-CHAT-001 | 聊天测试1 | 聊天功能 | 测试基本聊天 | 你好 | 今天天气怎么样 | 谢谢 |
| TC-CHAT-002 | 聊天测试2 | 聊天功能 | 测试智能问答 | 帮我写一首诗 | | |
| TC-QUERY-001 | 查询测试 | 查询功能 | 测试查询能力 | 查询明天的天气 | 查询北京的温度 | |

## 输出JSON格式

### 目录结构

```
output/
├── 聊天功能/
│   ├── TC-CHAT-001.json
│   └── TC-CHAT-002.json
└── 查询功能/
    └── TC-QUERY-001.json
```

### JSON文件结构

```json
{
  "version": "1.0.0",
  "testId": "TC-CHAT-001",
  "testName": "聊天测试1",
  "description": "测试基本聊天",
  "targetApp": {
    "packageName": "com.huawei.aisfdemo"
  },
  "timeout": 300000,
  "steps": [
    {
      "stepId": "launch",
      "description": "启动目标应用",
      "action": "launchApp"
    },
    {
      "stepId": "wait-home",
      "description": "等待首页加载",
      "action": "wait",
      "params": { "duration": 1000 }
    },
    {
      "stepId": "click-input-1",
      "description": "点击输入框 1",
      "action": "click",
      "target": { "strategy": "id", "value": "com.huawei.aisfdemo:id/editTextMessage" }
    },
    {
      "stepId": "input-message-1",
      "description": "输入消息: 你好",
      "action": "input",
      "target": { "strategy": "id", "value": "com.huawei.aisfdemo:id/editTextMessage" },
      "params": { "text": "你好", "clearFirst": true }
    },
    {
      "stepId": "click-send-1",
      "description": "点击发送按钮 1",
      "action": "click",
      "target": { "strategy": "id", "value": "com.huawei.aisfdemo:id/btnSend" }
    },
    {
      "stepId": "wait-response-1",
      "description": "等待AI回复 1",
      "action": "wait",
      "params": { "duration": 15000 }
    }
  ]
}
```

## 转换逻辑

每个聊天消息会转换为以下步骤序列：

1. **点击输入框** - 聚焦到输入区域
2. **输入文本** - 输入聊天内容
3. **点击发送** - 发送消息
4. **等待回复** - 等待AI响应（默认15秒）

## 注意事项

1. Excel文件必须是`.xlsx`格式
2. 第一行必须是列名（表头）
3. 用例编号不能为空
4. 至少需要一个测试步骤
5. 空行和空单元格会被自动跳过
6. 生成的JSON文件可直接导入TestApp Web Server使用

## 常见问题

### Q: 如何修改默认的UI元素ID？

A: 使用命令行参数指定：
```bash
python3 tools/excel_to_json.py input.xlsx output/ \
    --input-id "你的输入框ID" \
    --send-id "你的发送按钮ID"
```

### Q: 如何调整等待AI回复的时间？

A: 使用`--wait`参数，单位为毫秒：
```bash
python3 tools/excel_to_json.py input.xlsx output/ --wait 20000  # 等待20秒
```

### Q: 生成的JSON文件如何使用？

A: 将生成的JSON文件上传到TestApp Web Server的测试用例管理页面，然后下发到设备执行。

## 更新日志

### v1.0.0 (2026-04-03)
- 初始版本
- 支持Excel到JSON批量转换
- 支持自定义UI元素ID
- 支持按用例集分类