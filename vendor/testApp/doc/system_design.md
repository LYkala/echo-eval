# TestApp 测试框架系统设计文档

> 版本：2.1.0
> 更新日期：2026-04-10

## 1. 概述

### 1.1 系统简介

本系统是一个用于 Android App 功能测试的自动化测试框架，由两个应用组成：

- **testapp（测试控制器）**：负责解析测试流程JSON文件、控制目标应用执行测试、截图保存结果、管理测试流程。
- **appforfunction（目标应用）**：被测试的功能应用，提供登录、聊天、列表等功能模块。

### 1.2 核心功能

| 功能 | 说明 |
|------|------|
| JSON测试流程 | 内置测试流程JSON文件，支持多步骤连贯测试 |
| 无障碍控制 | 通过AccessibilityService实现对目标应用的完全控制 |
| 操作类型 | 支持点击、输入、滑动、滚动、返回、Home键等操作 |
| 截图保存 | 测试过程中自动截图保存到应用私有目录 |
| 日志记录 | 完整的日志系统，支持导出到下载目录 |
| 文件管理 | 查看截图、日志等测试产物 |

### 1.3 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                        testapp                              │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  MainActivity │  │ LogViewer    │  │ FileManager  │      │
│  │  (主界面)     │  │ Activity     │  │ Activity     │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────────┐   │
│  │              TestExecuteService (前台服务)            │   │
│  │  - 解析JSON测试流程                                   │   │
│  │  - 按顺序执行测试步骤                                  │   │
│  │  - 截图保存                                          │   │
│  │  - 测试完成处理                                      │   │
│  └──────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────────┐   │
│  │         TestAccessibilityService (无障碍服务)         │   │
│  │  - 节点查找 (ID/Text)                                │   │
│  │  - 点击操作 (节点点击/坐标点击)                        │   │
│  │  - 输入操作                                          │   │
│  │  - 滑动手势                                          │   │
│  │  - 全局按键 (Back/Home/Recents)                      │   │
│  │  - 截图能力 (API 30+)                                │   │
│  └──────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────────┐   │
│  │              CommandExecutor (命令执行器)             │   │
│  │  - 解析测试步骤                                       │   │
│  │  - 分发执行动作                                      │   │
│  └──────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   JsonParser  │  │    Logger    │  │ Screenshot   │      │
│  │   (JSON解析)  │  │   (日志工具)  │  │ Util         │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### 1.4 技术选型

| 项目 | 技术方案 | 说明 |
|------|----------|------|
| 开发语言 | Java | Android原生开发 |
| 最低SDK | API 21 (Android 5.0) | 兼容性好 |
| 目标SDK | API 34 (Android 14) | 支持最新特性 |
| 构建工具 | Gradle 8.0+ | 标准构建工具 |
| UI框架 | 原生View + XML | 简单可靠 |
| 权限控制 | Accessibility Service | 无障碍服务实现UI自动化 |
| 前台服务 | Foreground Service | 保证测试过程不被杀死 |

---

## 2. 核心模块设计

### 2.1 TestAccessibilityService（无障碍服务）

**核心能力：**

```java
public class TestAccessibilityService extends AccessibilityService {
    // 单例模式获取实例
    public static TestAccessibilityService getInstance();

    // 节点查找
    public AccessibilityNodeInfo findNodeById(String id);
    public AccessibilityNodeInfo findNodeByText(String text);

    // 操作方法
    public boolean performClick(AccessibilityNodeInfo node);
    public boolean performInput(AccessibilityNodeInfo node, String text);
    public boolean performSwipe(int startX, int startY, int endX, int endY, int duration);
    public boolean performBack();
    public boolean performHome();

    // 截图能力 (API 30+)
    public boolean takeScreenshot(File outputFile);
}
```

**点击策略：**
1. 如果节点本身可点击，直接点击
2. 如果节点不可点击，向上查找可点击的父节点（最多10层）
3. 如果都不可点击，使用手势模拟点击节点中心坐标

### 2.2 TestExecuteService（测试执行服务）

**职责：**
- 前台服务运行，确保测试不被系统杀死
- 解析JSON测试文件
- 按顺序执行测试步骤
- 每步完成后截图
- 测试完成后返回testapp主界面

**执行流程：**

```
startTest(testFile)
    ↓
解析JSON → 检查无障碍服务 → 启动目标应用
    ↓
executeNextStep() ← 循环执行
    ↓
执行步骤 → 截图 → 延迟500ms → executeNextStep()
    ↓
所有步骤完成（包括在steps中显式执行的返回键操作）
    ↓
startExitFlow() → 截图 → 启动testapp主界面 → 停止服务
```

**注意：** 退出被测应用的操作（返回键）需要在测试用例 JSON 的 `steps` 中显式执行，建议在最后添加6次 `press_back` 操作。

### 2.3 CommandExecutor（命令执行器）

**支持的操作类型：**

| 动作 | 说明 | 参数 |
|------|------|------|
| click | 点击元素 | target (id/text) |
| input | 输入文本 | target, params.text |
| scroll | 滚动 | params.direction (up/down) |
| swipe | 滑动 | params.startX/Y, endX/Y, duration |
| wait | 等待 | params.duration |
| press_back / back | 返回键 | - |
| home | Home键 | - |
| launchApp | 启动应用 | - |
| screenshot | 截图 | - |

### 2.4 JsonParser（JSON解析器）

**解析模型：**

```java
public class TestFlow {
    private String version;
    private String testId;
    private String testName;
    private String description;
    private String targetPackage;
    private List<TestStep> steps;
}

public class TestStep {
    private String stepId;
    private String description;
    private String action;
    private Target target;
    private Params params;
}

public class Target {
    private String strategy;  // "id" 或 "text"
    private String value;
}

public class Params {
    private String text;
    private String direction;
    private int duration;
    private int startX, startY, endX, endY;
}
```

---

## 3. 测试流程设计

### 3.1 测试用例文件结构

```
testapp/src/main/assets/testflows/
├── login_basic_001.json      # 登录测试
├── chat_send_001.json        # 聊天发送测试
├── list_scroll_001.json      # 列表滚动测试
└── ...
```

### 3.2 测试执行流程

```
┌─────────────────────────────────────────────────────────────┐
│                      用户操作                                │
│  1. 打开testapp                                             │
│  2. 开启无障碍服务                                          │
│  3. 选择测试文件                                            │
│  4. 点击"启动测试"                                          │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    TestExecuteService                        │
│  1. 解析JSON测试流程                                        │
│  2. 检查无障碍服务                                          │
│  3. 启动appforfunction                                      │
│  4. 循环执行测试步骤                                        │
│  5. 每步完成后截图                                          │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    测试完成处理                              │
│  1. 最终截图保存                                            │
│  2. 启动testapp主界面（多种方案确保成功）                    │
│  3. 发送广播通知UI更新                                      │
│  4. 停止前台服务                                            │
│                                                                 │
│ 注意：退出被测应用的操作在JSON steps中显式执行               │
└─────────────────────────────────────────────────────────────┘
```

#### 启动testapp主界面的多种方案

测试完成后，需要返回testapp主界面，采用以下方案按顺序尝试：

1. **全屏通知启动**（最可靠）
   - 发送带全屏通知Intent的PendingIntent
   - 用户点击通知后启动MainActivity

2. **最近任务查找**
   - 打开最近任务列表
   - 递归遍历所有节点，查找包含"testapp"的节点
   - 点击找到的节点（不滑动，避免误关闭其他app）

3. **Intent方式启动**
   - 使用PackageManager获取MainActivity
   - 通过Intent直接启动

4. **am命令启动**
   - 使用`am start`命令启动应用

### 3.3 截图保存位置

```
/storage/emulated/0/Android/data/com.example.testapp/files/Pictures/
└── screenshots/
    └── {testId}/
        ├── step_1_xxx.png
        ├── step_2_xxx.png
        └── result_xxx.png
```

---

## 4. 权限要求

### 4.1 testapp权限

```xml
<!-- 悬浮窗权限 -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- 前台服务权限 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<!-- 存储权限 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

<!-- 网络权限 -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- 查询所有包（Android 11+） -->
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
```

### 4.2 无障碍服务配置

```xml
<accessibility-service
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:canTakeScreenshot="true"
    android:description="@string/accessibility_service_description" />
```

---

## 5. 界面设计

### 5.1 MainActivity（主界面）

```
┌─────────────────────────────────────┐
│           TestApp 主界面             │
├─────────────────────────────────────┤
│  测试状态: 待执行                    │
│  测试结果: -                         │
│  无障碍服务: 已启用 ✓                │
├─────────────────────────────────────┤
│  测试文件列表:                       │
│  ┌─────────────────────────────┐   │
│  │ login_basic_001.json        │   │
│  │ chat_send_001.json          │   │
│  │ list_scroll_001.json        │   │
│  └─────────────────────────────┘   │
├─────────────────────────────────────┤
│  ┌───────────┐  ┌───────────┐     │
│  │ 启动测试   │  │ 停止测试   │     │
│  └───────────┘  └───────────┘     │
│  ┌───────────┐  ┌───────────┐     │
│  │ 文件管理   │  │ 查看日志   │     │
│  └───────────┘  └───────────┘     │
└─────────────────────────────────────┘
```

### 5.2 FileManagerActivity（文件管理）

- 截图列表Tab：显示所有测试截图
- 日志列表Tab：显示测试日志文件

---

## 6. 异常处理

### 6.1 无障碍服务未启用

- 检测到服务未启用时弹出对话框引导用户开启
- 提供"去开启"按钮跳转到无障碍设置页面

### 6.2 测试执行异常

- 每个步骤执行都有try-catch包裹
- 异常时记录日志，继续执行下一步
- 最终显示测试结果（成功/失败）

### 6.3 节点查找失败

- 记录警告日志
- 步骤标记为失败
- 继续执行后续步骤

### 6.4 截图失败

- 尝试多种截图方式：
  1. AccessibilityService.takeScreenshot() (API 30+)
  2. screencap shell命令
  3. 创建占位图片

---

## 7. 日志系统

### 7.1 Logger工具类

```java
public class Logger {
    // 日志级别
    public static final int DEBUG = 0;
    public static final int INFO = 1;
    public static final int WARN = 2;
    public static final int ERROR = 3;

    // 记录日志
    public void info(String tag, String message);
    public void error(String tag, String message, Throwable e);

    // 导出日志到下载目录
    public File exportToDownload(String testId);
}
```

### 7.2 日志文件位置

```
/storage/emulated/0/Android/data/com.example.testapp/files/logs/
└── {testId}_{timestamp}.log
```

---

## 8. 广播通信

### 8.1 广播Action定义

```java
// 启动测试
public static final String ACTION_START_TEST = "com.example.testapp.START_TEST";

// 停止测试
public static final String ACTION_STOP_TEST = "com.example.testapp.STOP_TEST";

// 测试完成通知
public static final String ACTION_TEST_FINISHED = "com.example.testapp.TEST_FINISHED";
```

### 8.2 通信流程

```
MainActivity                    TestExecuteService
    │                                  │
    │──── ACTION_START_TEST ──────────>│
    │                                  │ 执行测试
    │                                  │
    │<─── ACTION_TEST_FINISHED ────────│
    │                                  │
```

---

## 9. 项目结构

```
testapp/
├── src/main/
│   ├── java/com/example/testapp/
│   │   ├── MainActivity.java              # 主界面
│   │   ├── FileManagerActivity.java       # 文件管理
│   │   ├── LogViewerActivity.java         # 日志查看
│   │   ├── TestApplication.java           # Application类
│   │   ├── adapter/
│   │   │   └── TestFileAdapter.java       # 测试文件列表适配器
│   │   ├── core/
│   │   │   └── CommandExecutor.java       # 命令执行器
│   │   ├── fragment/
│   │   │   └── ScreenshotsFragment.java   # 截图列表Fragment
│   │   ├── model/
│   │   │   ├── TestFlow.java              # 测试流程模型
│   │   │   ├── TestStep.java              # 测试步骤模型
│   │   │   ├── Target.java                # 目标元素模型
│   │   │   └── Params.java                # 参数模型
│   │   ├── service/
│   │   │   ├── TestAccessibilityService.java  # 无障碍服务
│   │   │   └── TestExecuteService.java    # 测试执行服务
│   │   └── util/
│   │       ├── JsonParser.java            # JSON解析器
│   │       ├── Logger.java                # 日志工具
│   │       └── ScreenshotUtil.java        # 截图工具
│   ├── res/
│   │   ├── layout/                        # 布局文件
│   │   └── xml/
│   │       ├── accessibility_service_config.xml  # 无障碍服务配置
│   │       └── file_paths.xml             # FileProvider配置
│   └── assets/
│       └── testflows/                     # 测试流程JSON文件
│           ├── login_basic_001.json
│           ├── chat_send_001.json
│           └── list_scroll_001.json
└── build.gradle
```

---

## 10. 版本兼容性

| Android版本 | API Level | 兼容性 | 说明 |
|------------|-----------|--------|------|
| Android 5.0-6.0 | 21-23 | 基本兼容 | 无障碍服务可用，部分手势功能受限 |
| Android 7.0-10 | 24-29 | 完全兼容 | 支持手势操作 |
| Android 11+ | 30+ | 完全兼容 | 支持截图API |

---

## 11. 后续扩展

### 11.1 计划功能

- [ ] 截图对比验证
- [ ] 性能数据采集
- [ ] 测试报告生成
- [ ] 云端测试结果同步
- [ ] 测试用例在线编辑

### 11.2 优化方向

- [ ] 提高截图成功率
- [ ] 优化节点查找性能
- [ ] 增加更多定位策略
- [ ] 支持条件判断和循环

---

## 12. Web Server 功能

### 12.1 功能概述

Web Server 提供基于浏览器的测试文件管理功能，支持：

- 通过 Web 界面管理测试文件（JSON 文件）
- 上传、删除、选择测试文件
- 下发测试文件到已连接的设备
- 查看已连接设备状态和文件列表
- 远程管理设备上的测试文件（删除、刷新）

### 12.2 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                      Web Server (Python Flask)                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │  Flask App   │  │  REST API    │  │  WebSocket   │         │
│  │  (Web界面)    │  │  (文件管理)   │  │  (设备通信)   │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
│  ┌──────────────────────────────────────────────────────┐     │
│  │              testfiles/ (JSON文件存储目录)            │     │
│  └──────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────┘
                              ↕ WebSocket (实时双向通信)
┌─────────────────────────────────────────────────────────────────┐
│                         testapp (Android)                       │
│  ┌──────────────────────────────────────────────────────┐      │
│  │          WebServerClientService (后台服务)            │      │
│  │  - WebSocket 实时连接                                │      │
│  │  - 心跳保活（10秒间隔）                               │      │
│  │  - 重连间隔（2秒）                                    │      │
│  │  - 同步设备文件列表                                   │      │
│  │  - 接收文件下发和删除命令                              │      │
│  └──────────────────────────────────────────────────────┘      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │  MainActivity │  │ JsonParser   │  │ File Manager │         │
│  │  (显示连接状态)│  │ (解析文件)    │  │ (本地文件管理) │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└─────────────────────────────────────────────────────────────────┘
```

### 12.3 通信协议

#### WebSocket 端点
```
ws://<服务器IP>:9527/socket.io
```

#### 消息类型

| 类型 | 方向 | 说明 |
|------|------|------|
| register | 客户端→服务器 | 设备注册，包含设备信息和文件列表 |
| registered | 服务器→客户端 | 注册成功确认 |
| heartbeat | 客户端→服务器 | 心跳保活，携带文件列表 |
| heartbeat_ack | 服务器→客户端 | 心跳响应 |
| ping | 服务器→客户端 | 服务器端心跳 |
| pong | 客户端→服务器 | 客户端响应服务器心跳 |
| files | 服务器→客户端 | 下发测试文件 |
| delete_file | 服务器→客户端 | 删除设备上的文件命令 |
| refresh_files | 服务器→客户端 | 刷新文件列表命令 |
| file_deleted | 客户端→服务器 | 文件删除确认 |

### 12.4 REST API 设计

| 端点 | 方法 | 说明 |
|------|------|------|
| /api/health | GET | 健康检查 |
| /api/files | GET | 获取服务器文件列表 |
| /api/files | POST | 上传文件 |
| /api/files/{filename} | DELETE | 删除服务器文件 |
| /api/files/download | POST | 下发文件到设备 |
| /api/device/status | GET | 获取已连接设备列表（含文件列表） |
| /api/device/{id}/files | GET | 获取设备文件列表 |
| /api/device/{id}/files/{filename} | DELETE | 删除设备文件 |
| /api/device/{id}/refresh | POST | 刷新设备文件列表 |
| /api/device/{id}/execute | POST | 远程执行测试用例 |
| /api/testresults | GET | 获取所有设备测试结果（含离线设备） |
| /api/testresults/{device_id} | GET | 获取指定设备测试结果 |
| /api/testresults/{device_id}/{test_id}/{filename} | GET | 获取截图图片 |
| /api/testresults/{device_id}/{test_id}/{filename} | DELETE | 删除截图 |
| /api/testresults/batch-delete | POST | 批量删除截图 |

### 12.5 Web 前端功能

- **设备管理**：显示已连接设备列表，点击展开查看设备文件
- **文件上传**：上传 JSON 测试文件到服务器
- **文件下发**：选择服务器文件，发送到设备
- **远程删除**：直接删除设备上的测试文件
- **远程执行**：点击 Run 按钮远程执行设备上的测试用例
- **测试结果**：测试完成后自动上传截图，在设备卡片下显示测试结果
- **截图管理**：以文件列表形式查看截图，支持全选、批量下载
- **测试结果历史**：独立页面查看所有设备（包括离线）的历史测试结果，支持批量删除截图
- **并发测试**：选择多个设备和多个测试用例，同时执行批量测试
- **状态监控**：实时显示服务器和设备连接状态

### 12.6 并发测试功能

#### 12.6.1 功能概述

并发测试功能允许用户选择多个在线设备，同时执行多个测试用例，实现批量并发测试。

**核心特性：**
- 多设备并行执行：同一测试用例同时发送到多个设备
- 用例顺序执行：多个测试用例按选择顺序依次执行
- 超时控制：每个测试用例可独立配置超时时间
- 实时进度：显示当前执行的用例和设备完成状态
- 执行日志：记录所有执行过程信息

#### 12.6.2 执行流程

```
┌─────────────────────────────────────────────────────────────────┐
│                   Web Server (并发测试页面)                      │
│  ┌──────────────────────┐  ┌──────────────────────┐            │
│  │    左栏：在线设备     │  │   右栏：测试用例      │            │
│  │  - 设备列表(可勾选)   │  │  - 用例列表(可勾选)   │            │
│  │  - SelectAll按钮      │  │  - 显示超时时间       │            │
│  │  - DeselectAll按钮    │  └──────────────────────┘            │
│  └──────────────────────┘                                       │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  菜单栏: [Run Selected]  [状态显示: 当前执行进度]          │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              ↓ WebSocket并发下发
┌─────────────────────────────────────────────────────────────────┐
│                    TestApp 设备 (多个并行执行)                   │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐    │
│  │   Device 1     │  │   Device 2     │  │   Device 3     │    │
│  │  执行用例A     │  │  执行用例A     │  │  执行用例A     │    │
│  │  超时监控      │  │  超时监控      │  │  超时监控      │    │
│  └────────────────┘  └────────────────┘  └────────────────┘    │
│         ↓                  ↓                  ↓                │
│    test_completed     test_completed     test_completed        │
└─────────────────────────────────────────────────────────────────┘
```

#### 12.6.3 并发执行逻辑

```python
# 并发测试状态管理
concurrent_test_state = {
    'running': False,
    'selected_devices': [],       # 选中的设备ID列表
    'selected_files': [],         # 选中的用例文件列表
    'current_file_index': 0,      # 当前执行的用例索引
    'completed_devices': set(),   # 当前用例已完成的设备集合
    'results': [],                # 执行结果
    'logs': [],                   # 执行日志
    'lock': threading.RLock()     # 可重入锁
}

# 执行流程
1. 用户选择设备和用例
2. 点击 Run Selected
3. 获取第一个用例的超时时间
4. 向所有选中设备发送 execute_test 命令
5. 等待所有设备返回 test_completed 或超时
6. 间隔2秒
7. 如果还有下一个用例，回到步骤3
8. 否则完成，显示结果汇总
```

#### 12.6.4 并发测试 API

| 端点 | 方法 | 说明 |
|------|------|------|
| /concurrent | GET | 并发测试页面 |
| /api/concurrent/start | POST | 开始并发测试 |
| /api/concurrent/status | GET | 获取执行状态 |
| /api/concurrent/stop | POST | 停止并发测试 |

### 12.6 启动 Web Server V2

```bash
cd webserverV2
pip install -r requirements.txt
python webserver_v2.py
```

默认访问地址：http://localhost:9527

### 12.7 详细文档

参见 [webserverV2/doc/guide.md](../webserverV2/doc/guide.md)