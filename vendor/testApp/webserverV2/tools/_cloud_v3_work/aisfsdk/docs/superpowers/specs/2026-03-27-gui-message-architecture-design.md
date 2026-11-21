# GUI消息架构设计文档

**文档版本**: 1.0  
**创建日期**: 2026-03-27  
**最后更新**: 2026-03-27  

---

## 1. 概述

### 1.1 文档目的
本文档描述了 aisfsdk 项目中 GUI 消息的架构设计，包括消息模型、消息类型、处理流程和扩展机制。旨在为新增 GUI 消息提供清晰的指导。

### 1.2 适用范围
- GUI 操控消息的设计与实现
- 新 GUI 消息的添加流程
- 消息处理的最佳实践

---

## 2. 架构总览

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      GUI 消息架构                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   ┌─────────────┐      ┌─────────────┐     ┌─────────────┐ │
│   │   消息模型   │──────▶│  消息编解码  │────▶│  消息处理   │ │
│   └─────────────┘      └─────────────┘     └─────────────┘ │
│          │                    │                   │         │
│          ▼                    ▼                   ▼         │
│   ┌─────────────┐      ┌─────────────┐     ┌─────────────┐ │
│   │  命令定义    │      │  协议层      │     │  GUI执行    │ │
│   └─────────────┘      └─────────────┘     └─────────────┘ │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 核心组件

| 组件 | 职责 | 关键类 |
|------|------|--------|
| 消息模型 | 定义消息结构和字段 | `MsgInfo`, `HeaderMsgInfo`, `GuiParam` |
| 消息编解码 | JSON 序列化/反序列化 | `MsgCodec` |
| 消息处理 | 消息分发和队列管理 | `MessageProcess`, `GuiProcess` |
| 命令定义 | GUI 命令类型常量 | `MsgConst.MsgDc` |
| 协议层 | WebSocket 通信 | `WebsocketClient` |
| GUI 执行 | 具体 UI 操作执行 | `AccessibilityMgr`, `AppMgrImpl` |

---

## 3. 消息模型

### 3.1 三层消息结构

所有 GUI 消息采用三层结构：

```json
{
  "Header": {
    "MessageType": "DataExchange",
    "TaskID": "task-xxx",
    "Sender": "APP",
    "Receiver": "SystemAgent",
    "ClientSessionId": "session-xxx"
  },
  "Data": {
    "MessageType": "UserInput",
    "UserInputText": "xxx"
  },
  "Payload": {
    "Function": "GUI",
    "CmdType": 1006,
    "Params": {
      "StartX": 100,
      "StartY": 200
    }
  }
}
```

### 3.2 核心类定义

#### 3.2.1 MsgInfo（消息主模型）

```java
public class MsgInfo {
    // 头域
    private String eventType;
    private String conversationId;
    private String acmSessionId;
    private String taskId;
    private String seqNum;
    
    // 数据域
    private String text;
    private String fileUrl;
    private int sdkMsgType;
    
    // Payload 域
    private GuiParam guiParam;
    private ShowCtrlParam showParam;
    private MultiDeviceParam deviceParam;
}
```

#### 3.2.2 GuiParam（GUI参数）

```java
public static class GuiParam {
    private String guiMsgId;           // GUI消息ID
    private int cmdType;               // 命令类型（关键字段）
    private List<String> appList;      // 应用列表
    private String appName;            // 应用名称
    private int startX, startY;        // 起始坐标
    private int endX, endY;            // 结束坐标
    private String inputText;          // 输入文本
    private int errorCode;             // 错误码
    private boolean isNeedScreenShot;  // 是否需要截图
    private int duration;              // 持续时间
    private int sleepTime;             // 强制等待时间
}
```

#### 3.2.3 HeaderMsgInfo（消息头部）

```java
public class HeaderMsgInfo {
    private String messageType;
    private String sender;
    private String senderType;
    private String receiver;
    private String senderName;
    private String eventTimeStamp;
    private String clientSessionId;
    private String userName;
    private String deviceId;
    private String deviceCategory;
    private String deviceCategoryStr;
    private String uuid;
    private String deviceUUID;
    private String identifyId;
    private String taskId;  // 新增字段，JSON序列化为 "TaskID"
}
```

---

## 4. 消息类型定义

### 4.1 GUI 命令类型（MsgConst.MsgDc）

| 常量名 | 值 | 说明 | 参数 |
|--------|-----|------|------|
| GUI_CMD_NOTIFY_START | 1000 | GUI操控开始通知 | - |
| GUI_CMD_GET_APP_LIST | 1001 | 获取应用列表 | - |
| GUI_CMD_UPLOAD_LIST | 1002 | 上报应用列表 | `appList` |
| GUI_CMD_LAUNCH_APP | 1003 | 启动应用 | `appName`, `flag` |
| GUI_CMD_SCREEN_SHOT | 1004 | 截图命令 | `isNeedScreenShot` |
| GUI_CMD_UPLOAD_SCREEN_SHOT | 1005 | 上报截图 | - |
| GUI_CMD_CLICK | 1006 | 点击操作 | `startX`, `startY` |
| GUI_CMD_SWIPE | 1007 | 滑动操作 | `startX`, `startY`, `endX`, `endY`, `duration` |
| GUI_CMD_LONG_CLICK | 1008 | 长按操作 | `startX`, `startY`, `duration` |
| GUI_CMD_INPUT_TEXT | 1009 | 输入文本 | `startX`, `startY`, `inputText` |
| GUI_CMD_COMPLETED | 1010 | 操控完成 | - |
| GUI_CMD_STOP | 1011 | 中断GUI操控 | - |
| GUI_CMD_HOME | 1012 | Home键操作 | - |
| GUI_CMD_BACK | 1013 | Back键操作 | - |
| GUI_CMD_ENTER | 1014 | Enter键操作 | - |
| GUI_CMD_TASKS | 1015 | 多任务界面 | - |
| GUI_CMD_CHECK_INSTALL | 1018 | 检查应用安装状态 | `appName` |
| GUI_CMD_INSTALL_NOTIFY | 1019 | 上报安装状态 | `appName`, `isInstalled` |

### 4.2 SDK 消息类型（MsgConst.SdkMsgType）

| 常量名 | 值 | 说明 |
|--------|-----|------|
| SDK_MSG_TEXT | 0 | 文本消息 |
| SDK_MSG_FILE | 1 | 文件消息 |
| SDK_MSG_NOTIFY | 2 | 通知消息 |
| SDK_MSG_GUI | 3 | GUI操控消息 |
| SDK_MSG_TASK_STOP | 4 | 任务停止通知 |
| SDK_MSG_CLARIFY | 5 | 澄清消息 |
| SDK_MSG_MULTI_DEVICE | 6 | 跨端流转 |
| SDK_MSG_AUTH | 7 | 鉴权消息 |
| SDK_MSG_LOGOUT | 8 | 退出登录 |
| SDK_MSG_ROUTE | 9 | 路由消息 |
| SDK_MSG_POSITION | 10 | 位置消息 |
| SDK_MSG_UPLOAD_LOG | 11 | 上传日志 |

### 4.3 GUI 业务类型（GUIMessageCodeType）

| 枚举值 | Code | 说明 |
|--------|------|------|
| UNKNOWN | 0 | 未知类型 |
| ADD_TO_CALENDAR | 200003 | 添加到日历 |
| LINK | 200004 | 链接跳转 |
| IMAGE | 200006 | 图片消息 |
| GUI | 300001 | GUI操控操作 |
| START_SKILL | 210001 | 开始技能 |
| STOP_SKILL | 210003 | 停止技能 |
| STOP_ERROR_SKILL | 210006 | 异常停止技能 |

---

## 5. 消息处理流程

### 5.1 发送流程

```
业务层
   │
   ▼
构造 MsgInfo
   │
   ▼
MsgCodec.encodeMsg() → JSON 字符串
   │
   ▼
MessageProcess.sendRequestMsg()
   │
   ▼
WebsocketClient.send()
   │
   ▼
服务端
```

### 5.2 接收流程

```
服务端
   │
   ▼
WebsocketClient.onMessage()
   │
   ▼
MessageProcess.onMsgReceived()
   │
   ▼
放入接收队列
   │
   ▼
接收线程
   │
   ▼
dispatchReceiveMsg()
   │
   ├─ SDK_MSG_GUI → GuiProcess.onRecvAisfMsg()
   ├─ SDK_MSG_TEXT → handleTextMsg()
   └─ SDK_MSG_NOTIFY → handleNotifyMsg()
   │
   ▼
GuiProcess 处理
   │
   ▼
根据 cmdType 查表调用处理器
   │
   ▼
执行具体操作
```

### 5.3 GUI 命令处理流程

```
GuiProcess
   │
   ▼
放入 guiQueue (BlockingQueue)
   │
   ▼
处理线程取出消息
   │
   ▼
根据 msg.getGuiParam().getCmdType() 查表
   │
   ├─ 1006 (CLICK) → handleClick()
   ├─ 1007 (SWIPE) → handleSwipe()
   └─ ... 其他命令
   │
   ▼
调用 AccessibilityMgr / AppMgrImpl 执行
   │
   ▼
返回结果 / 上报截图
```

---

## 6. 核心接口

### 6.1 GUI 屏幕管理接口（GUIScreenMgrApi）

```java
public interface GUIScreenMgrApi {
    boolean click(int x, int y);
    boolean longClick(int x, int y, int duration);
    boolean swipe(int startX, int startY, int endX, int endY, int duration);
    boolean textInput(int x, int y, String text);
    boolean home();
    boolean back();
    boolean enter();
    boolean recentTasks();
    Bitmap captureScreen();
    Bitmap takeValidScreenshot();
    Bitmap takeScreenshot();
    int[] getScreenResolution();
    long getScreenState();
}
```

### 6.2 GUI 应用管理接口（GUIAppMgrApi）

```java
public interface GUIAppMgrApi {
    List<String> getAppList();
    boolean launchApp(String appName, int flag);
    void handleDeepLink(String url);
    int isAppInstalled(String appName);
}
```

### 6.3 GUI 状态回调（GuiStatusCallback）

```java
public interface GuiStatusCallback {
    void onStarted();
    void onEnd(int reasonCode, String errMsg);
}
```

---

## 7. 扩展机制

### 7.1 新增 GUI 命令的步骤

1. **定义命令常量**（在 `MsgConst.MsgDc` 中）
   ```java
   public static final int GUI_CMD_NEW_FEATURE = 1020; // 使用下一个可用值
   ```

2. **添加命令处理器**（在 `GuiProcess` 中）
   ```java
   handleGuiMsgMap.put(MsgDc.GUI_CMD_NEW_FEATURE, 
                       guiHandlerWrapper(this::handleNewFeature));
   ```

3. **实现处理方法**
   ```java
   private void handleNewFeature(MsgInfo msg) {
       GuiParam param = msg.getGuiParam();
       // 获取参数
       int customParam = param.getCustomParam();
       // 执行操作
       boolean result = mgrApi.newOperation(customParam);
       // 处理结果
       handleResult(msg, result);
   }
   ```

4. **更新 API 接口**（如果需要）
   - 在 `GUIScreenMgrApi` 或 `GUIAppMgrApi` 中添加方法
   - 在实现类中实现方法

5. **更新文档**
   - 在本文档 4.1 节添加新命令说明
   - 更新版本历史

### 7.2 新增消息类型的步骤

1. **定义类型常量**（在 `MsgConst.SdkMsgType` 中）
2. **添加消息处理器**（在 `MessageProcess.dispatchReceiveMsg` 中）
3. **实现具体处理逻辑**
4. **更新回调接口**（如果需要通知上层）

---

## 8. 最佳实践

### 8.1 消息设计原则

1. **单一职责**：每个消息只做一件事
2. **向后兼容**：新增字段要有默认值，不要修改已有字段含义
3. **参数校验**：在处理前校验必要参数
4. **错误处理**：统一错误码，提供清晰的错误信息
5. **资源释放**：及时释放 AccessibilityNodeInfo 等资源

### 8.2 命名规范

1. **命令常量**：`GUI_CMD_` + 动词 + 名词（大写下划线）
2. **消息类型**：`SDK_MSG_` + 类型名（大写下划线）
3. **JSON 字段**：PascalCase（如 `MessageType`, `TaskID`）
4. **Java 字段**：camelCase（如 `taskId`, `cmdType`）

### 8.3 性能考虑

1. **队列容量**：设置合理的队列上限，防止内存溢出
2. **超时处理**：设置操作超时，避免无限等待
3. **截图压缩**：大尺寸截图要先压缩再传输
4. **资源回收**：及时 recycle Bitmap 和 AccessibilityNodeInfo

---

## 9. 版本历史

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| 1.0 | 2026-03-27 | 初始版本，整理现有 GUI 消息架构 |

---

## 10. 待办事项

- [ ] 定义新的 GUI 命令需求
- [ ] 设计新命令的参数结构
- [ ] 实现新命令的处理器
- [ ] 添加单元测试
- [ ] 更新 API 文档

---

## 附录 A：命令值范围

- **GUI 命令**：1000-1999
  - 已使用：1000-1019
  - 可用范围：1020-1999
  
- **SDK 消息类型**：0-99
  - 已使用：0-11
  - 可用范围：12-99

## 附录 B：参考资料

- Android AccessibilityService 官方文档
- WebSocket 协议规范
- JSON 序列化最佳实践

---

**文档维护者**: Development Team  
**审核状态**: Draft
