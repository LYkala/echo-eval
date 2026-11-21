# TestReply SDK

轻量 Android SDK，供 **DeviceAPP**（如 `com.example.devicesdkdemo`）集成，将 aisfsdk 收到的 AI 回复通过广播发给 **TestApp**，再由 TestApp 上报 WebServer。

## 集成方式

### 1. 添加依赖

**同工程模块：**

```gradle
// settings.gradle
include ':testreply-sdk'

// app/build.gradle
dependencies {
    implementation project(':testreply-sdk')
}
```

**AAR 方式：**

```bash
cd code
./gradlew :testreply-sdk:assembleRelease
# 产物: testreply-sdk/build/outputs/aar/testreply-sdk-release.aar
```

```gradle
implementation files('libs/testreply-sdk-release.aar')
```

### 2. DeviceAPP 初始化（Application 或 QA 页）

```java
import com.example.testreply.TestReplyConfig;
import com.example.testreply.TestReplySdk;

TestReplySdk.init(getApplicationContext(), new TestReplyConfig.Builder()
        .targetPackage("com.example.testapp")  // TestApp 包名
        .taskId("001")                         // 可选，测试任务 ID
        .caseId("CK_GUI_FHCK_22_001")          // 可选，用例 ID
        .build());
```

### 3. 在 QAActivity / SessionCallback 中挂钩

**用户发送问题时：**

```java
int round = TestReplySdk.onUserMessageSent(currentSessionId, message);
```

**流式 TEXT 片段（聚焦模式）：**

```java
TestReplySdk.onReplyChunk(currentSessionId, data.getTextInfo().getText());
```

**流式结束（finalizeStreaming 内）：**

```java
TestReplySdk.finalizeReply(getApplicationContext(), sid, TestReplyContract.OUTPUT_TEXT);
```

**GUI 任务完成（TASK_STATUS taskCompleted=true）：**

```java
TestReplySdk.onTaskCompleted(getApplicationContext(), sid, statusCode);
```

**非流式单条回复：**

```java
TestReplySdk.publishReply(getApplicationContext(), new TestReplyEvent.Builder()
        .sessionId(currentSessionId)
        .round(round)
        .userMessage(userMessage)
        .replyText(data.getTextInfo().getText())
        .outputType(TestReplyContract.OUTPUT_TEXT)
        .finalReply(true)
        .build());
```

### 4. TestApp 侧（已内置依赖时可自动接收）

```java
BroadcastReceiver receiver = TestReplySdk.registerListener(context, event -> {
    // 通过 WebSocket 上报 webserver: type=case_reply
});
// 测试结束时
TestReplySdk.unregisterListener(context, receiver);
```

## 广播协议

| 字段 | 说明 |
|------|------|
| `session_id` | SDK 会话 ID |
| `round` | 第几轮问答 |
| `user_message` | 用户提问 |
| `reply_text` | AI 回复正文 |
| `output_type` | TEXT / TASK_STATUS / GUI_TASK 等 |
| `is_final` | 是否最终回复（流式结束为 true） |
| `task_completed` | GUI 任务是否完成（TASK_STATUS） |
| `sent_at_ms` / `latency_ms` | 发问到回复完成的时延 |
| `task_id` / `case_id` | 测试上下文（可选） |

Action: `com.example.testreply.ACTION_SESSION_REPLY`

## 与 aisfsdk 的关系

本 SDK **不依赖** `aisfsdk`，只负责把 DeviceAPP 已收到的回复转发给 TestApp。aisfsdk 的 `SessionCallback` 仍在 DeviceAPP 内使用，在回调里调用本 SDK 即可。

## MobileAPK (DeviceSDK Demo) 改造要点

在 `QAActivity.java` 中：

1. `sendMessage()` 末尾调用 `TestReplySdk.onUserMessageSent`
2. `finalizeStreaming()` 末尾调用 `TestReplySdk.finalizeReply`
3. `TASK_STATUS` 完成时调用 `TestReplySdk.onTaskCompleted`
4. TestApp 的 `wait-response-N` 会监听广播并提前结束等待（超时兜底）

参考补丁见 `doc/QAActivity.integration.example.md`（如已生成）。
