# Excel to JSON 转换工具 - 需求设计文档

## 1. 需求背景

### 1.1 问题陈述

在自动化测试过程中，测试用例通常以Excel格式编写和管理。然而，TestApp自动化测试框架需要JSON格式的测试文件。手动转换不仅耗时，而且容易出错。

### 1.2 目标

开发一个工具，实现Excel测试用例到JSON格式的批量自动转换，提高测试用例编写效率。

### 1.3 使用场景

- 测试人员编写Excel格式的聊天测试用例
- 使用工具批量转换为JSON格式
- 将JSON文件上传到TestApp Web Server
- 下发到设备执行自动化测试

## 2. 需求分析

### 2.1 功能需求

| ID | 需求描述 | 优先级 |
|----|----------|--------|
| F01 | 支持读取Excel(.xlsx)格式文件 | P0 |
| F02 | 支持批量转换多行测试用例 | P0 |
| F03 | 每行生成一个独立的JSON文件 | P0 |
| F04 | 支持按用例集创建文件夹分类 | P1 |
| F05 | 支持自定义目标应用包名 | P1 |
| F06 | 支持自定义UI元素ID | P1 |
| F07 | 支持配置等待时间和超时 | P1 |
| F08 | 支持生成示例Excel模板 | P2 |

### 2.2 非功能需求

| ID | 需求描述 |
|----|----------|
| NF01 | 工具应支持命令行运行 |
| NF02 | 转换过程应有清晰的日志输出 |
| NF03 | 支持跳过空行和无效数据 |
| NF04 | 生成符合TestApp规范的JSON格式 |

### 2.3 输入输出规格

#### 输入：Excel文件

```
必需列：
- 用例编号：测试用例唯一标识
- 用例名称：测试用例名称
- 用例集：用于分类的文件夹名
- 描述：测试用例描述

测试步骤列：
- 测试步骤1, 测试步骤2, ... 测试步骤N
- 每列代表一条聊天消息
```

#### 输出：JSON文件

```
目录结构：
output/
├── {用例集1}/
│   ├── {用例编号1}.json
│   └── {用例编号2}.json
└── {用例集2}/
    └── {用例编号3}.json
```

## 3. 系统设计

### 3.1 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                    CLI Interface                         │
│  (argparse - command line argument parsing)              │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                   Main Controller                        │
│  - parse_arguments()                                     │
│  - create_sample_excel()                                 │
│  - convert_excel_to_json()                               │
└─────────────────────────────────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Excel Parser │  │ JSON Builder │  │ File Writer  │
│ (openpyxl)   │  │              │  │              │
└──────────────┘  └──────────────┘  └──────────────┘
```

### 3.2 模块设计

#### 3.2.1 Excel解析模块

```python
def parse_excel(excel_path) -> list[dict]:
    """
    解析Excel文件，提取测试用例数据

    处理流程：
    1. 读取Excel文件
    2. 识别列名和列索引
    3. 逐行解析数据
    4. 返回测试用例列表
    """
```

#### 3.2.2 JSON构建模块

```python
def create_test_case(test_id, test_name, description, chat_texts, config) -> dict:
    """
    构建单个测试用例JSON结构

    处理流程：
    1. 创建基础结构
    2. 添加启动应用步骤
    3. 为每条消息添加聊天步骤
    4. 添加截图步骤
    """

def create_chat_step(step_index, text, config) -> list[dict]:
    """
    创建单条聊天消息的步骤序列

    步骤序列：
    1. 点击输入框
    2. 输入文本
    3. 点击发送
    4. 等待回复
    """
```

#### 3.2.3 文件输出模块

```python
def convert_excel_to_json(excel_path, output_dir, config):
    """
    执行完整转换流程

    处理流程：
    1. 解析Excel
    2. 为每个用例创建JSON
    3. 按用例集创建文件夹
    4. 写入JSON文件
    """
```

### 3.3 数据流设计

```
Excel File
    │
    ▼
┌─────────────────┐
│ Row Data        │  用例编号: TC-CHAT-001
│                 │  用例名称: 聊天测试
│                 │  用例集: 聊天功能
│                 │  步骤: ["你好", "谢谢"]
└─────────────────┘
    │
    ▼
┌─────────────────┐
│ Test Case JSON  │  {
│                 │    "testId": "TC-CHAT-001",
│                 │    "steps": [...]
│                 │  }
└─────────────────┘
    │
    ▼
File: output/聊天功能/TC-CHAT-001.json
```

### 3.4 JSON步骤生成规则

对于每条聊天消息，生成以下步骤序列：

```
输入: "你好"
         │
         ▼
┌─────────────────────────────────────────────────────┐
│ Step 1: Click Input Field                           │
│   - action: "click"                                 │
│   - target: { strategy: "id", value: input_id }     │
├─────────────────────────────────────────────────────┤
│ Step 2: Input Text                                  │
│   - action: "input"                                 │
│   - target: { strategy: "id", value: input_id }     │
│   - params: { text: "你好", clearFirst: true }      │
├─────────────────────────────────────────────────────┤
│ Step 3: Click Send Button                           │
│   - action: "click"                                 │
│   - target: { strategy: "id", value: send_id }      │
├─────────────────────────────────────────────────────┤
│ Step 4: Wait for Response                           │
│   - action: "wait"                                  │
│   - params: { duration: 15000 }                     │
└─────────────────────────────────────────────────────┘
```

### 3.5 配置参数设计

```python
DEFAULT_CONFIG = {
    "package_name": "com.huawei.aisfdemo",
    "input_id": "com.huawei.aisfdemo:id/editTextMessage",
    "send_id": "com.huawei.aisfdemo:id/btnSend",
    "voice_toggle_id": "com.huawei.aisfdemo:id/textVoice",
    "wait_ms": 15000,      # 每条消息后等待时间
    "timeout_ms": 300000   # 测试总超时时间
}
```

## 4. 接口设计

### 4.1 命令行接口

```bash
# 基本用法
python3 excel_to_json.py <input_excel> <output_dir>

# 完整参数
python3 excel_to_json.py <input> <output> \
    --package <package_name> \
    --input-id <input_field_id> \
    --send-id <send_button_id> \
    --voice-toggle-id <toggle_button_id> \
    --wait <wait_ms> \
    --timeout <timeout_ms>

# 生成示例
python3 excel_to_json.py --sample <output_file>
```

### 4.2 退出码

| 退出码 | 说明 |
|--------|------|
| 0 | 成功 |
| 1 | 参数错误或执行失败 |

## 5. 异常处理

### 5.1 输入验证

- 检查输入文件是否存在
- 检查文件格式是否为.xlsx
- 检查必需列是否存在

### 5.2 数据处理

- 跳过空行
- 跳过无测试步骤的行
- 处理特殊字符（文件名安全）

### 5.3 输出处理

- 创建不存在的目录
- 覆盖已存在的文件

## 6. 测试用例

### 6.1 正常场景

| 用例ID | 描述 | 预期结果 |
|--------|------|----------|
| TC01 | 转换单行数据 | 生成1个JSON文件 |
| TC02 | 转换多行数据 | 生成多个JSON文件 |
| TC03 | 多个用例集 | 创建多个文件夹 |
| TC04 | 空步骤单元格 | 自动跳过 |

### 6.2 异常场景

| 用例ID | 描述 | 预期结果 |
|--------|------|----------|
| TC05 | 文件不存在 | 提示错误并退出 |
| TC06 | 空Excel文件 | 提示无测试用例 |
| TC07 | 缺少必需列 | 使用默认值或跳过 |

## 7. 扩展性设计

### 7.1 支持新的步骤类型

可通过扩展`create_chat_step`函数支持更多步骤类型：
- 长按操作
- 滑动操作
- 截图验证

### 7.2 支持不同的目标应用

通过配置文件支持不同应用的UI映射：
```json
{
  "com.huawei.aisfdemo": {
    "input_id": "...",
    "send_id": "..."
  },
  "com.example.other": {
    "input_id": "...",
    "send_id": "..."
  }
}
```

### 7.3 支持GUI界面

未来可添加图形界面，提供：
- Excel预览
- 转换进度显示
- 结果验证

## 8. 版本规划

### v1.0.0 (当前)
- 基本转换功能
- 命令行接口
- 示例模板生成

### v1.1.0 (计划)
- 支持配置文件
- 支持批量Excel转换
- 转换日志报告

### v2.0.0 (计划)
- GUI界面
- Excel在线编辑
- 转换结果预览

## 9. 附录

### 9.1 依赖库

| 库名 | 版本 | 用途 |
|------|------|------|
| openpyxl | >=3.0 | Excel文件读写 |
| argparse | 内置 | 命令行参数解析 |

### 9.2 文件清单

```
tools/
├── excel_to_json.py      # 主程序
├── sample_testcases.xlsx # 示例Excel
└── doc/
    ├── README.md         # 使用指导
    └── DESIGN.md         # 需求设计文档
```

### 9.3 参考资料

- [TestApp JSON用例格式](../webserverV2/testfiles/)
- [openpyxl文档](https://openpyxl.readthedocs.io/)
- [TestExecuteService源码](../code/testapp/src/main/java/com/example/testapp/service/TestExecuteService.java)