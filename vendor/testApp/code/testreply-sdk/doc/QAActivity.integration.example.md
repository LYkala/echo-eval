# QAActivity 集成 TestReply SDK 示例

在 `sendMessage()` 中，发送成功后：

```java
int round = TestReplySdk.onUserMessageSent(currentSessionId, message);
```

在 `finalizeStreaming(String sid)` 末尾追加：

```java
Context appCtx = getApplicationContext();
TestReplySdk.finalizeReply(appCtx, sid, TestReplyContract.OUTPUT_TEXT);
```

在 `SessionCallback.onReceived` 聚焦模式流式片段处：

```java
} else if (focusMode && data.getOutputType() == OutputTypeEnum.TEXT && data.getTextInfo() != null) {
    String textContent = data.getTextInfo().getText();
    TestReplySdk.onReplyChunk(sid, textContent);
    // ... 原有 streamingTextMap 逻辑保留
```

非聚焦模式单条 TEXT：

```java
} else if (!focusMode && data.getOutputType() == OutputTypeEnum.TEXT && data.getTextInfo() != null) {
    TestReplySdk.publishReply(
            getApplicationContext(),
            new TestReplyEvent.Builder()
                    .sessionId(sid)
                    .replyText(data.getTextInfo().getText())
                    .outputType(TestReplyContract.OUTPUT_TEXT)
                    .finalReply(true)
                    .build());
    appendMessageToSession(sid, "服务端", "text=" + data.getTextInfo().getText());
```

在 `DemoApp.onCreate()` 或 `QAActivity.onCreate()` 初始化：

```java
TestReplySdk.init(getApplicationContext(), new TestReplyConfig.Builder().build());
```

`TASK_STATUS` 完成时（聚焦模式）：

```java
TestReplySdk.onTaskCompleted(appCtx, sid, data.getTaskInfo().getTaskStatusCode());
```
