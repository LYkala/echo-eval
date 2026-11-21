# TestApp Web Server V2 API 接口文档

> 版本：1.0.0
> 更新日期：2026-04-11

---

## 1. 概述

本文档定义 Web Server V2 的 REST API 和 WebSocket 接口。

**基础URL**: `http://localhost:9527`

---

## 2. 日志接口

### 2.1 获取运行日志

**请求**
```
GET /api/logs?hours=24
```

**参数**
| 参数 | 类型 | 说明 |
|------|------|------|
| hours | int | 查询过去多少小时的日志，默认24 |

**响应**
```json
{
  "success": true,
  "logs": [
    "2026-04-11 10:00:00,123 - INFO - Server started",
    "2026-04-11 10:00:05,456 - INFO - Device registered: device_abc123"
  ]
}
```

---

## 3. 设备接口

### 3.1 获取在线设备

**请求**
```
GET /api/devices
```

**响应**
```json
{
  "success": true,
  "devices": [
    {
      "device_id": "device_abc123",
      "device_name": "Samsung SM-G991B",
      "manufacturer": "Samsung",
      "model": "SM-G991B",
      "android_version": "13",
      "last_seen": 1712822400
    }
  ]
}
```

---

## 4. 用例集接口

### 4.1 获取所有用例集

**请求**
```
GET /api/testsets
```

**响应**
```json
{
  "success": true,
  "testsets": [
    {
      "name": "login_tests",
      "create_time": 1712822400,
      "files": [
        {"name": "login_001.json", "size": 1024, "last_modified": 1712822400}
      ]
    }
  ]
}
```

### 4.2 创建用例集

**请求**
```
POST /api/testsets
Content-Type: application/json

{
  "name": "new_testset"
}
```

**响应**
```json
{
  "success": true,
  "message": "Testset new_testset created"
}
```

### 4.3 删除用例集

**请求**
```
DELETE /api/testsets/{testset_name}
```

**响应**
```json
{
  "success": true,
  "message": "Testset new_testset deleted"
}
```

### 4.4 获取用例文件（分页）

**请求**
```
GET /api/testsets/{testset_name}/files?page=1&per_page=20
```

**响应**
```json
{
  "success": true,
  "files": [
    {
      "name": "login_001.json",
      "size": 1024,
      "last_modified": 1712822400
    }
  ],
  "total": 50,
  "page": 1,
  "per_page": 20,
  "total_pages": 3
}
```

---

## 5. 文件接口

### 5.1 上传文件

**请求**
```
POST /api/files/upload
Content-Type: multipart/form-data

file: <json文件>
testset: 用例集名称
```

**响应**
```json
{
  "success": true,
  "message": "File uploaded",
  "file": "login_001.json"
}
```

### 5.2 删除文件

**请求**
```
POST /api/files/delete
Content-Type: application/json

{
  "files": [
    {"testset": "login_tests", "filename": "login_001.json"},
    {"testset": "login_tests", "filename": "login_002.json"}
  ]
}
```

**响应**
```json
{
  "success": true,
  "deleted": ["login_001.json", "login_002.json"]
}
```

---

## 6. 任务接口

### 6.1 获取任务列表

**请求**
```
GET /api/tasks
```

**响应**
```json
{
  "success": true,
  "tasks": [
    {
      "id": "task_001",
      "devices": ["device_abc123", "device_def456"],
      "testset": "login_tests",
      "files": ["login_001.json", "login_002.json"],
      "start_type": "immediate",
      "start_time": 1712822400,
      "status": "completed",
      "results": []
    }
  ]
}
```

### 6.2 创建任务

**请求**
```
POST /api/tasks
Content-Type: application/json

{
  "task_id": "task_001",
  "devices": ["device_abc123"],
  "testset": "login_tests",
  "start_type": "immediate",
  "schedule_time": "2026-04-11T14:00:00"  // 仅当start_type为scheduled时
}
```

**响应**
```json
{
  "success": true,
  "message": "Task created",
  "task_id": "task_001"
}
```

### 6.3 删除任务

**请求**
```
DELETE /api/tasks/{task_id}
```

**响应**
```json
{
  "success": true,
  "message": "Task task_001 deleted"
}
```

---

## 7. 结果接口

### 7.1 获取所有结果

**请求**
```
GET /api/results
```

**响应**
```json
{
  "success": true,
  "results": [
    {
      "task_id": "task_001",
      "testsets": [
        {
          "testset_name": "login_tests",
          "testcases": [
            {
              "name": "login_001",
              "screenshots": [
                {"name": "step_1.png", "size": 102400}
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

### 7.2 获取任务结果

**请求**
```
GET /api/results/{task_id}
```

**响应**
```json
{
  "success": true,
  "result": {
    "task_id": "task_001",
    "testsets": [
      {
        "testset_name": "login_tests",
        "testcases": [
          {
            "name": "login_001",
            "screenshots": [
              {
                "name": "step_1.png",
                "size": 102400,
                "url": "/api/results/task_001/login_tests/login_001/step_1.png"
              }
            ]
          }
        ]
      }
    ]
  }
}
```

### 7.3 获取截图

**请求**
```
GET /api/results/{task_id}/{testset}/{testcase}/{filename}
```

**响应**
返回图片文件 (image/png)

### 7.4 删除结果

**请求**
```
POST /api/results/delete
Content-Type: application/json

// 删除整个任务
{"task_id": "task_001"}

// 删除特定截图
{"task_id": "task_001", "testset": "login_tests", "testcase": "login_001", "filename": "step_1.png"}
```

**响应**
```json
{
  "success": true,
  "message": "Task task_001 deleted"
}
```

---

## 8. WebSocket 接口

### 8.1 连接

```
ws://<服务器IP>:9527/socket.io/?EIO=4&transport=websocket
```

### 8.2 消息类型

#### 设备注册 (客户端 → 服务器)

```json
{
  "type": "register",
  "deviceId": "device_abc123",
  "deviceName": "Samsung SM-G991B",
  "deviceInfo": {
    "manufacturer": "Samsung",
    "model": "SM-G991B",
    "android_version": "13",
    "appVersion": "1.0.0"
  }
}
```

#### 注册确认 (服务器 → 客户端)

```json
{
  "type": "registered"
}
```

#### 心跳 (客户端 → 服务器)

```json
{
  "type": "heartbeat",
  "deviceId": "device_abc123"
}
```

#### 下发文件 (服务器 → 客户端)

```json
{
  "type": "files",
  "files": [
    {
      "name": "login_001.json",
      "content": "{\"version\":\"1.0\",\"testId\":\"login_001\"...}"
    }
  ]
}
```

#### 执行测试 (服务器 → 客户端)

```json
{
  "type": "execute_test",
  "filename": "login_001.json"
}
```

#### 删除文件 (服务器 → 客户端)

```json
{
  "type": "delete_file",
  "filename": "login_001.json"
}
```

#### 测试完成 (客户端 → 服务器)

```json
{
  "type": "test_completed",
  "taskId": "task_001",
  "testId": "login_001",
  "success": true,
  "message": "Test completed"
}
```

#### 上传截图 (客户端 → 服务器)

```json
{
  "type": "screenshot_upload",
  "testId": "login_001",
  "filename": "result.png",
  "data": "data:image/png;base64,iVBORw0KGgo..."
}
```