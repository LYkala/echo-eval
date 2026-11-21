# TestApp Web Server V2 系统设计

> 版本：1.0.0
> 更新日期：2026-04-11

---

## 1. 系统概述

Web Server V2 是 TestApp 测试框架的配套服务端，提供测试用例管理和结果收集功能。

### 1.1 设计目标

- 清晰的界面呈现
- 清晰的数据存储结构
- 数据一致性保证

---

## 2. 技术架构

### 2.1 技术选型

| 项目 | 技术方案 | 说明 |
|------|----------|------|
| 后端框架 | Flask 3.0 | Python Web 框架 |
| 实时通信 | Flask-SocketIO | WebSocket 支持 |
| 前端框架 | Bootstrap 5.3 | UI 框架 |
| 前端通信 | 原生 JavaScript + Socket.IO Client | 页面交互 |
| 存储 | 文件系统 | JSON 文件存储 |

### 2.2 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    Web Browser (Client)                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │   首页    │ │ 用例管理  │ │ 用例下发  │ │ 结果管理  │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
└────────────────────────┬────────────────────────────────────┘
                         │ HTTP / WebSocket
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   Web Server V2 (Port 9527)                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Flask App   │  │  REST API    │  │  SocketIO    │      │
│  │  (Web界面)    │  │  (数据接口)   │  │  (设备通信)   │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                    testapp (Android)                         │
│  ┌──────────────────────────────────────────────────────┐   │
│  │          WebServerClientService (后台服务)            │   │
│  │  - WebSocket 连接                                    │   │
│  │  - 接收用例文件                                      │   │
│  │  - 执行测试                                          │   │
│  │  - 上传截图                                          │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. 数据结构设计

### 3.1 用例集结构

```
testfiles/
├── login_tests/          # 用例集文件夹
│   ├── login_001.json
│   ├── login_002.json
│   └── ...
├── chat_tests/
│   ├── chat_001.json
│   └── ...
└── default/              # 根目录 JSON 文件
    └── sample.json
```

**数据模型**
```python
{
    "name": "login_tests",      # 用例集名称
    "create_time": 1712822400,  # 创建时间戳
    "files": [                   # 文件列表
        {
            "name": "login_001.json",
            "size": 1024,
            "last_modified": 1712822400
        }
    ]
}
```

### 3.2 任务结构

```
tasks/
└── task_001.json
```

**数据模型**
```python
{
    "id": "task_001",           # 任务ID
    "devices": ["device_abc"],  # 执行设备列表
    "testset": "login_tests",   # 用例集名称
    "files": ["login_001.json"],# 用例文件列表
    "start_type": "immediate",  # 执行类型: immediate/scheduled
    "schedule_time": None,      # 定时时间
    "start_time": 1712822400,   # 开始时间戳
    "end_time": 1712822600,     # 结束时间戳
    "status": "completed",      # 状态: pending/running/completed
    "results": []               # 执行结果
}
```

### 3.3 测试结果结构

```
testresults/
└── task_001/                   # 按任务ID隔离
    └── login_tests/            # 按用例集隔离
        └── login_001/          # 按测试用例隔离
            ├── step_1_xxx.png  # 步骤截图
            ├── step_2_xxx.png
            └── result_xxx.png  # 结果截图
```

### 3.4 设备在线状态

**内存存储**
```python
devices = {
    "device_abc123": {
        "id": "device_abc123",
        "name": "Samsung SM-G991B",
        "info": {
            "manufacturer": "Samsung",
            "model": "SM-G991B",
            "android_version": "13"
        },
        "last_seen": 1712822400,
        "sid": "socket_session_id"
    }
}
```

**在线判定**: 当前时间 - last_seen < 300秒 (5分钟)

---

## 4. 核心模块设计

### 4.1 路由模块

| 路由 | 方法 | 功能 |
|------|------|------|
| / | GET | 首页 |
| /testcases | GET | 用例管理页面 |
| /dispatch | GET | 用例下发页面 |
| /results | GET | 结果管理页面 |

### 4.2 API 模块

| 模块 | 功能 |
|------|------|
| 日志模块 | 读取服务器运行日志 |
| 设备模块 | 管理在线设备列表 |
| 用例集模块 | 用例集 CRUD 操作 |
| 文件模块 | 文件上传删除 |
| 任务模块 | 任务创建执行删除 |
| 结果模块 | 结果查询删除 |

### 4.3 WebSocket 模块

| 事件 | 方向 | 功能 |
|------|------|------|
| connect | - | 客户端连接 |
| disconnect | - | 客户端断开 |
| message | 双向 | 消息处理 |

### 4.4 后台任务模块

任务执行采用后台线程：
```python
def execute_task(task_id):
    # 1. 加载任务
    task = load_task(task_id)

    # 2. 逐个用例下发执行
    for filename in files:
        # 发送文件
        # 发送执行命令
        # 等待完成
        # 删除文件

    # 3. 更新任务状态
    task['status'] = 'completed'
    save_task(task)
```

---

## 5. 前端设计

### 5.1 页面结构

使用 Bootstrap 5.3 + 响应式布局

**公共组件**
- 顶部导航栏
- 卡片容器
- 模态框
- 分页组件

### 5.2 页面列表

| 页面 | 功能 |
|------|------|
| index.html | 日志查看 + 在线设备 |
| testcases.html | 用例集/文件管理 |
| dispatch.html | 任务创建执行 |
| results.html | 结果查看下载 |

---

## 6. 接口一致性

### 6.1 REST API 设计风格

- 返回格式统一: `{"success": true, "data": ...}`
- 错误格式: `{"success": false, "message": "错误信息"}`
- 分页格式: `{"page": 1, "per_page": 20, "total": 100}`

### 6.2 WebSocket 消息格式

- 消息类型: `{"type": "xxx", ...data}`
- 文本编码: UTF-8
- 传输格式: JSON

---

## 7. 安全考虑

### 7.1 当前实现

- CORS: 允许所有来源 (`cors_allowed_origins="*"`)
- 无认证机制

### 7.2 生产环境建议

- 添加用户认证
- 使用 HTTPS
- 限制访问 IP
- 添加请求频率限制

---

## 8. 性能优化

### 8.1 已实现

- 用例文件分页加载 (每页20条)
- 日志文件限制返回最近500行
- 线程安全锁保护共享数据

### 8.2 可选优化

- 图片压缩存储
- 结果数据数据库存储
- 缓存机制

---

## 9. 文件结构

```
webserverV2/
├── app.py                 # 主应用入口
├── requirements.txt       # Python 依赖
├── templates/            # HTML 模板
│   ├── base.html        # 基础模板
│   ├── index.html       # 首页
│   ├── testcases.html   # 用例管理
│   ├── dispatch.html    # 用例下发
│   └── results.html     # 结果管理
├── static/              # 静态资源 (预留)
├── logs/                # 运行日志
│   └── webserver.log
├── testfiles/           # 测试用例文件
│   └── {用例集}/
│       └── *.json
├── tasks/               # 任务数据
│   └── {task_id}.json
├── testresults/         # 测试结果
│   └── {task_id}/
│       └── {用例集}/
│           └── {测试用例}/
│               └── *.png
└── doc/                 # 文档
    ├── guide.md         # 使用指南
    ├── api.md           # API 文档
    └── system_design.md # 系统设计
```