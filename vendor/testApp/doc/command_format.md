# 测试命令 JSON 格式规范

> 版本：1.4.0
> 更新日期：2026-04-10

本文档定义 testapp 解析和执行测试流程的 JSON 文件格式规范。

---

## 1. 概述

### 1.1 文件位置

测试流程 JSON 文件存放在 testapp 的 `assets/testflows/` 目录下，每个 JSON 文件代表一个独立的测试流程。

### 1.2 文件命名规范

```
{测试模块}_{测试类型}_{序号}.json

示例：
login_basic_001.json      # 登录模块基础测试001
chat_send_001.json        # 聊天模块发送消息测试001
profile_edit_001.json     # 个人资料编辑测试001
```

### 1.3 字符编码

统一使用 **UTF-8** 编码。

---

## 2. JSON 结构定义

### 2.1 完整结构

```json
{
  "version": "string",           // 格式版本号
  "testId": "string",            // 测试流程唯一标识
  "testName": "string",          // 测试流程名称
  "description": "string",       // 测试流程描述
  "exitBackCount": 6,            // 退出时按返回键次数（建议在steps中显式执行）
  "targetApp": {                 // 目标应用配置
    "packageName": "string"      // 目标应用包名
  },
  "timeout": 60000,              // 超时时间（毫秒），可选
  "steps": [ ]                   // 测试步骤列表
}
```

### 2.2 必填字段

| 字段 | 类型 | 说明 |
|------|------|------|
| version | string | 格式版本号，当前为 "1.0" |
| testId | string | 测试流程唯一标识，如 "TC-LOGIN-001" |
| targetApp.packageName | string | 目标应用包名 |
| steps | array | 测试步骤列表，至少包含一个步骤 |

### 2.3 可选字段

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| testName | string | "" | 测试流程名称 |
| description | string | "" | 测试流程描述 |
| timeout | int | 60000 | 整体超时时间（毫秒），用于并发测试时的超时控制 |
| exitBackCount | int | 2 | 保留字段，返回键操作建议在steps中显式执行 |

---

## 3. 测试步骤结构

每个步骤的结构：

```json
{
  "stepId": "string",        // 步骤标识（可选）
  "description": "string",   // 步骤描述（可选）
  "action": "string",        // 动作类型（必填）
  "target": { },             // 目标元素定位（部分动作必填）
  "params": { }              // 动作参数（可选）
}
```

---

## 4. 支持的动作类型

### 4.1 动作类型总览

| 动作 | 说明 | 是否需要 target | 是否需要 params |
|------|------|----------------|----------------|
| **点击类** |
| click | 点击元素 | ✅ | ❌ |
| **输入类** |
| input | 输入文本 | ✅ | text（必填） |
| **滑动类** |
| swipe | 坐标滑动 | ❌ | startX, startY, endX, endY, duration |
| scroll | 滚动 | ❌ | direction（必填） |
| **等待类** |
| wait | 固定等待 | ❌ | duration（必填） |
| **应用控制类** |
| launchApp | 启动应用 | ❌ | ❌ |
| press_back | 按返回键 | ❌ | ❌ |
| back | 按返回键（等价于press_back） | ❌ | ❌ |
| home | 主页键 | ❌ | ❌ |
| **工具类** |
| screenshot | 截图 | ❌ | ❌ |

### 4.2 动作详细说明

#### 4.2.1 click（点击元素）

```json
{
  "stepId": "click-login",
  "description": "点击登录按钮",
  "action": "click",
  "target": {
    "strategy": "id",
    "value": "com.example.appforfunction:id/btn_login"
  }
}
```

#### 4.2.2 input（输入文本）

```json
{
  "stepId": "input-username",
  "description": "输入用户名",
  "action": "input",
  "target": {
    "strategy": "id",
    "value": "com.example.appforfunction:id/et_username"
  },
  "params": {
    "text": "testuser"
  }
}
```

#### 4.2.3 swipe（坐标滑动）

```json
{
  "stepId": "swipe-custom",
  "description": "从下往上滑动",
  "action": "swipe",
  "params": {
    "startX": 540,
    "startY": 1500,
    "endX": 540,
    "endY": 500,
    "duration": 300
  }
}
```

#### 4.2.4 scroll（滚动）

```json
{
  "stepId": "scroll-down",
  "description": "向下滑动",
  "action": "scroll",
  "params": {
    "direction": "down"
  }
}
```

**direction 取值：** up, down

#### 4.2.5 wait（等待）

```json
{
  "stepId": "wait-load",
  "description": "等待加载完成",
  "action": "wait",
  "params": {
    "duration": 1000
  }
}
```

#### 4.2.6 press_back（返回键）

```json
{
  "stepId": "exit-back-1",
  "description": "按返回键退出应用",
  "action": "press_back"
}
```

**注意：** 返回键操作建议在测试用例的最后步骤中显式执行，用于退出被测应用。

#### 4.2.7 home（主页键）

```json
{
  "stepId": "home",
  "description": "按主页键",
  "action": "home"
}
```

#### 4.2.8 launchApp（启动应用）

```json
{
  "stepId": "launch",
  "description": "启动目标应用",
  "action": "launchApp"
}
```

#### 4.2.9 screenshot（截图）

```json
{
  "stepId": "screenshot-result",
  "description": "截图保存结果",
  "action": "screenshot"
}
```

---

## 5. 元素定位策略

### 5.1 target 结构

```json
{
  "target": {
    "strategy": "string",    // 定位策略（必填）
    "value": "string"        // 定位值（必填）
  }
}
```

### 5.2 支持的定位策略

| 策略 | 说明 | value 示例 |
|------|------|------------|
| id | 通过 resource-id 定位 | `com.example.appforfunction:id/btn_login` |
| text | 通过文本完全匹配定位 | `登录` |

### 5.3 定位策略示例

```json
// 通过 ID 定位
{
  "strategy": "id",
  "value": "com.example.appforfunction:id/btn_submit"
}

// 通过文本定位
{
  "strategy": "text",
  "value": "确定"
}
```

---

## 6. 完整示例

### 6.1 登录流程测试

```json
{
  "version": "1.0",
  "testId": "login_basic_001",
  "testName": "登录功能测试",
  "description": "测试用户登录流程",
  "targetPackage": "com.example.appforfunction",
  "timeout": 60000,
  "steps": [
    {
      "stepId": "launch",
      "description": "启动目标应用",
      "action": "launchApp"
    },
    {
      "stepId": "wait-load",
      "description": "等待页面加载",
      "action": "wait",
      "params": {
        "duration": 1000
      }
    },
    {
      "stepId": "input-username",
      "description": "输入用户名",
      "action": "input",
      "target": {
        "strategy": "id",
        "value": "com.example.appforfunction:id/et_username"
      },
      "params": {
        "text": "testuser"
      }
    },
    {
      "stepId": "input-password",
      "description": "输入密码",
      "action": "input",
      "target": {
        "strategy": "id",
        "value": "com.example.appforfunction:id/et_password"
      },
      "params": {
        "text": "123456"
      }
    },
    {
      "stepId": "click-login",
      "description": "点击登录按钮",
      "action": "click",
      "target": {
        "strategy": "id",
        "value": "com.example.appforfunction:id/btn_login"
      }
    },
    {
      "stepId": "wait-result",
      "description": "等待登录结果",
      "action": "wait",
      "params": {
        "duration": 2000
      }
    },
    {
      "stepId": "screenshot-result",
      "description": "截图保存结果",
      "action": "screenshot"
    }
  ]
}
```

**字段说明：**
- `timeout: 60000`：设置整体超时时间为60秒，用于并发测试时的超时控制
```

### 6.2 列表滑动测试

```json
{
  "version": "1.0",
  "testId": "list_scroll_001",
  "testName": "列表滑动测试",
  "description": "测试列表滑动功能",
  "targetPackage": "com.example.appforfunction",
  "steps": [
    {
      "stepId": "launch",
      "action": "launchApp"
    },
    {
      "stepId": "wait-load",
      "action": "wait",
      "params": {
        "duration": 1000
      }
    },
    {
      "stepId": "scroll-down",
      "description": "向下滑动",
      "action": "scroll",
      "params": {
        "direction": "down"
      }
    },
    {
      "stepId": "wait",
      "description": "等待加载",
      "action": "wait",
      "params": {
        "duration": 1000
      }
    },
    {
      "stepId": "scroll-up",
      "description": "向上滑动回到顶部",
      "action": "scroll",
      "params": {
        "direction": "up"
      }
    },
    {
      "stepId": "screenshot",
      "action": "screenshot"
    }
  ]
}
```

### 6.3 聊天发送消息测试

```json
{
  "version": "1.0",
  "testId": "chat_send_001",
  "testName": "聊天发送消息测试",
  "description": "测试聊天功能发送消息",
  "targetPackage": "com.example.appforfunction",
  "steps": [
    {
      "stepId": "launch",
      "action": "launchApp"
    },
    {
      "stepId": "click-chat",
      "description": "点击聊天入口",
      "action": "click",
      "target": {
        "strategy": "text",
        "value": "消息"
      }
    },
    {
      "stepId": "click-input",
      "description": "点击输入框",
      "action": "click",
      "target": {
        "strategy": "id",
        "value": "com.example.appforfunction:id/et_message"
      }
    },
    {
      "stepId": "input-message",
      "description": "输入消息内容",
      "action": "input",
      "target": {
        "strategy": "id",
        "value": "com.example.appforfunction:id/et_message"
      },
      "params": {
        "text": "这是一条测试消息"
      }
    },
    {
      "stepId": "click-send",
      "description": "点击发送按钮",
      "action": "click",
      "target": {
        "strategy": "id",
        "value": "com.example.appforfunction:id/btn_send"
      }
    },
    {
      "stepId": "wait-send",
      "description": "等待发送完成",
      "action": "wait",
      "params": {
        "duration": 1000
      }
    },
    {
      "stepId": "screenshot",
      "action": "screenshot"
    }
  ]
}
```

---

## 7. testapp 解析规范

### 7.1 解析流程

```
1. 读取 JSON 文件
2. 校验必填字段（version, testId, targetPackage, steps）
3. 解析 targetPackage 获取目标应用包名
4. 遍历 steps 数组，解析每个步骤
5. 根据步骤 action 类型，校验必要参数
6. 按顺序执行步骤
```

### 7.2 默认值处理

| 字段 | 默认值 |
|------|--------|
| params.duration | 300 |
| params.direction | down |

### 7.3 执行结果存储

每个测试流程执行完成后，生成以下文件：

```
/storage/emulated/0/Android/data/com.example.testapp/files/
├── Pictures/screenshots/    # 截图目录
│   └── {testId}/
│       ├── step_001_xxx.png
│       ├── step_002_xxx.png
│       └── result_xxx.png
└── logs/                    # 日志目录
    └── {testId}_{timestamp}.log
```

---

## 8. 版本兼容性

### 8.1 版本号规则

采用语义化版本号：`MAJOR.MINOR`

- **MAJOR**：不兼容的格式变更
- **MINOR**：向后兼容的新增功能

### 8.2 当前版本

**1.0**

---

## 9. 附录

### 9.1 常用控件 ID 命名建议

建议 appforfunction 遵循以下命名规范：

```
{模块}_{控件类型}_{功能描述}

示例：
et_username            # 用户名输入框
et_password            # 密码输入框
btn_login              # 登录按钮
btn_send               # 发送按钮
et_message             # 消息输入框
```

### 9.2 屏幕坐标说明

- 坐标原点在屏幕左上角
- X 轴向右递增
- Y 轴向下递增
- duration 单位为毫秒

### 9.3 点击策略说明

testapp 在执行点击操作时采用以下策略：

1. 如果节点本身可点击，直接点击
2. 如果节点不可点击，向上查找可点击的父节点（最多10层）
3. 如果都不可点击，使用手势模拟点击节点中心坐标