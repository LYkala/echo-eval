package com.example.testreply;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 供 DeviceAPP 发布回复、TestApp 订阅回复的统一 SDK 入口。
 */
public final class TestReplySdk {

    private static volatile TestReplyConfig config;
    private static final SessionReplyAggregator aggregator = new SessionReplyAggregator();
    private static final ConcurrentHashMap<Long, PendingGuiCapture> pendingGuiCaptures =
            new ConcurrentHashMap<>();

    private TestReplySdk() {
    }

    public static void init(Context context, TestReplyConfig replyConfig) {
        config = replyConfig;
    }

    public static TestReplyConfig getConfig() {
        return config;
    }

    public static void updateContext(String taskId, String caseId) {
        updateContext(taskId, caseId, null);
    }

    public static void updateContext(String taskId, String caseId, String serverUrl) {
        TestReplyConfig old = config;
        if (old == null) {
            config = new TestReplyConfig.Builder()
                    .taskId(taskId)
                    .caseId(caseId)
                    .serverUrl(serverUrl)
                    .build();
            return;
        }
        config = new TestReplyConfig.Builder()
                .targetPackage(old.getTargetPackage())
                .taskId(taskId != null ? taskId : old.getTaskId())
                .caseId(caseId != null ? caseId : old.getCaseId())
                .serverUrl(serverUrl != null ? serverUrl : old.getServerUrl())
                .build();
    }

    /** 本地重置轮次（Device 进程内）。 */
    public static void resetAllRounds() {
        aggregator.resetAllRounds();
    }

    /**
     * TestApp 在每条用例开始时调用：通知 Device 重置问答轮次，使新用例从 round=1 计。
     */
    public static void notifyCaseStart(
            Context context, String taskId, String caseId, String devicePackage) {
        notifyCaseStart(context, taskId, caseId, devicePackage, null);
    }

    public static void notifyCaseStart(
            Context context, String taskId, String caseId, String devicePackage, String serverUrl) {
        updateContext(taskId, caseId, serverUrl);
        if (context == null) {
            return;
        }
        Intent intent = new Intent(TestReplyContract.ACTION_CASE_CONTROL);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.putExtra(TestReplyContract.EXTRA_CONTROL, TestReplyContract.CONTROL_CASE_START);
        if (taskId != null) {
            intent.putExtra(TestReplyContract.EXTRA_TASK_ID, taskId);
        }
        if (caseId != null) {
            intent.putExtra(TestReplyContract.EXTRA_CASE_ID, caseId);
        }
        if (serverUrl != null && !serverUrl.isEmpty()) {
            intent.putExtra(TestReplyContract.EXTRA_SERVER_URL, serverUrl);
        }
        String pkg = devicePackage != null && !devicePackage.isEmpty()
                ? devicePackage
                : TestReplyContract.DEFAULT_DEVICE_PACKAGE;
        intent.setPackage(pkg);
        context.getApplicationContext().sendBroadcast(intent);
    }

    /**
     * DeviceAPP 注册用例控制监听（收到 case_start 时自动重置轮次）。
     */
    public static BroadcastReceiver registerCaseControlListener(
            Context context, final CaseControlListener listener) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null
                        || !TestReplyContract.ACTION_CASE_CONTROL.equals(intent.getAction())) {
                    return;
                }
                String control = intent.getStringExtra(TestReplyContract.EXTRA_CONTROL);
                if (!TestReplyContract.CONTROL_CASE_START.equals(control)) {
                    return;
                }
                String taskId = intent.getStringExtra(TestReplyContract.EXTRA_TASK_ID);
                String caseId = intent.getStringExtra(TestReplyContract.EXTRA_CASE_ID);
                String serverUrl = intent.getStringExtra(TestReplyContract.EXTRA_SERVER_URL);
                updateContext(taskId, caseId, serverUrl);
                resetAllRounds();
                if (listener != null) {
                    listener.onCaseStart(taskId, caseId);
                }
            }
        };
        IntentFilter filter = new IntentFilter(TestReplyContract.ACTION_CASE_CONTROL);
        Context appContext = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            appContext.registerReceiver(receiver, filter);
        }
        return receiver;
    }

    public interface CaseControlListener {
        void onCaseStart(String taskId, String caseId);
    }

    /**
     * AISF 下发澄清弹窗(DIALOG)时调用，通知 TestApp 在本轮发送澄清消息。
     */
    public static void onDialogReceived(Context context, String sessionId, String dialogTitle) {
        TestReplyEvent event = aggregator.buildDialogReceivedEvent(sessionId, dialogTitle);
        if (event != null) {
            publishReply(context, event);
        }
    }

    /**
     * 发送澄清消息（不增加问答轮次）。
     */
    public static void sendClarify(String sessionId, String clarifyText) {
        aggregator.sendClarify(sessionId, clarifyText);
    }

    /**
     * DeviceAPP 注册澄清发送监听（TestApp 在收到 DIALOG 后触发）。
     */
    public static BroadcastReceiver registerClarifyListener(
            Context context, final ClarifyListener listener) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null
                        || !TestReplyContract.ACTION_CLARIFY_SEND.equals(intent.getAction())) {
                    return;
                }
                if (listener == null) {
                    return;
                }
                String text = intent.getStringExtra(TestReplyContract.EXTRA_CLARIFY_TEXT);
                int round = intent.getIntExtra(TestReplyContract.EXTRA_ROUND, 0);
                listener.onClarifySend(text, round);
            }
        };
        IntentFilter filter = new IntentFilter(TestReplyContract.ACTION_CLARIFY_SEND);
        Context appContext = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            appContext.registerReceiver(receiver, filter);
        }
        return receiver;
    }

    public interface ClarifyListener {
        void onClarifySend(String text, int round);
    }

    /**
     * Device 侧 GUI 接管被强制结束（倒计时/平台循环）时通知 TestApp，结束当前 wait-response。
     */
    public static void publishGuiTakeoverForceComplete(Context context, String reason) {
        String text = reason != null && !reason.isEmpty() ? reason : "GUI接管强制结束";
        if (!text.startsWith("[强制结束]")) {
            text = "[强制结束] " + text;
        }
        TestReplyEvent event = new TestReplyEvent.Builder()
                .sessionId(null)
                .round(0)
                .replyText(text)
                .outputType(TestReplyContract.OUTPUT_TEXT)
                .finalReply(true)
                .taskCompleted(false)
                .timestampMs(System.currentTimeMillis())
                .build();
        publishReply(context, event);
    }

    /**
     * TestApp → Device：注册强制结束 GUI 接管监听。
     */
    /**
     * DeviceAPP 注册：TestApp / watchdog 请求强制结束 GUI 并停止 AISF。
     */
    public interface ForceEndTakeoverListener {
        void onForceEndTakeover(String reason);
    }

    public static BroadcastReceiver registerForceEndTakeoverListener(
            Context context, final Runnable listener) {
        return registerForceEndTakeoverListener(context, reason -> {
            if (listener != null) {
                listener.run();
            }
        });
    }

    public static BroadcastReceiver registerForceEndTakeoverListener(
            Context context, final ForceEndTakeoverListener listener) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null
                        || !TestReplyContract.ACTION_FORCE_END_TAKEOVER.equals(intent.getAction())) {
                    return;
                }
                if (listener != null) {
                    String reason = intent.getStringExtra(TestReplyContract.EXTRA_REASON);
                    listener.onForceEndTakeover(reason != null ? reason : "force_end");
                }
            }
        };
        IntentFilter filter = new IntentFilter(TestReplyContract.ACTION_FORCE_END_TAKEOVER);
        Context appContext = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            appContext.registerReceiver(receiver, filter);
        }
        return receiver;
    }

    /**
     * TestApp 请求 Device 强制结束 GUI / 停止 AISF（等同聊天页「停止任务」）。
     * 优先 ContentProvider（绕过小米对 cached 广播的拦截），失败再广播。
     */
    public static void requestForceEndTakeover(Context context, String devicePackage, String reason) {
        if (context == null) {
            return;
        }
        Context app = context.getApplicationContext();
        String why = reason != null ? reason : "force_end";
        try {
            Bundle extras = new Bundle();
            extras.putString(TestReplyContract.EXTRA_REASON, why);
            Uri uri = Uri.parse("content://" + TestReplyContract.DEVICE_CAPTURE_AUTHORITY);
            Bundle result = app.getContentResolver().call(
                    uri,
                    TestReplyContract.PROVIDER_METHOD_STOP_AISF,
                    null,
                    extras);
            boolean ok = result != null && result.getBoolean(
                    TestReplyContract.EXTRA_STOP_AISF_OK, false);
            Log.i("TestReplySdk", "provider stop_aisf ok=" + ok + " reason=" + why);
            if (ok) {
                return;
            }
        } catch (Exception e) {
            Log.w("TestReplySdk", "provider stop_aisf failed: " + e.getMessage());
        }
        Intent intent = new Intent(TestReplyContract.ACTION_FORCE_END_TAKEOVER);
        intent.putExtra(TestReplyContract.EXTRA_REASON, why);
        String pkg = devicePackage != null && !devicePackage.isEmpty()
                ? devicePackage
                : TestReplyContract.DEFAULT_DEVICE_PACKAGE;
        intent.setPackage(pkg);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        app.sendBroadcast(intent);
        Log.i("TestReplySdk", "broadcast FORCE_END_TAKEOVER pkg=" + pkg + " reason=" + why);
    }

    /**
     * DeviceAPP 注册 GUI MediaProjection 截图请求（TestApp wait-response 轮询帧使用）。
     */
    public static BroadcastReceiver registerGuiScreenCaptureListener(
            Context context, final GuiScreenCaptureListener listener) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null
                        || !TestReplyContract.ACTION_GUI_SCREEN_CAPTURE_REQUEST.equals(
                                intent.getAction())) {
                    return;
                }
                if (listener == null) {
                    return;
                }
                long requestId = intent.getLongExtra(TestReplyContract.EXTRA_REQUEST_ID, 0L);
                String stepName = intent.getStringExtra(TestReplyContract.EXTRA_STEP_NAME);
                if (requestId == 0L) {
                    return;
                }
                listener.onCaptureRequest(requestId, stepName);
            }
        };
        IntentFilter filter = new IntentFilter(
                TestReplyContract.ACTION_GUI_SCREEN_CAPTURE_REQUEST);
        Context appContext = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            appContext.registerReceiver(receiver, filter);
        }
        return receiver;
    }

    public interface GuiScreenCaptureListener {
        void onCaptureRequest(long requestId, String stepName);
    }

    /**
     * TestApp 请求 Device 侧 MediaProjection 截图。
     * 优先 ContentProvider.call（唤醒 Greezer 冻结的 Demo），失败再广播。
     */
    public static void requestGuiScreenCapture(
            Context context, String devicePackage, long requestId, String stepName) {
        if (context == null || requestId == 0L) {
            return;
        }
        Intent intent = new Intent(TestReplyContract.ACTION_GUI_SCREEN_CAPTURE_REQUEST);
        intent.putExtra(TestReplyContract.EXTRA_REQUEST_ID, requestId);
        if (stepName != null) {
            intent.putExtra(TestReplyContract.EXTRA_STEP_NAME, stepName);
        }
        String pkg = devicePackage != null && !devicePackage.isEmpty()
                ? devicePackage
                : TestReplyContract.DEFAULT_DEVICE_PACKAGE;
        intent.setPackage(pkg);
        context.getApplicationContext().sendBroadcast(intent);
    }

    /**
     * 同步等待 Device MediaProjection 截图（在后台线程调用）。
     * 优先走 Demo {@code GuiCaptureProvider}，避免小米冻结 cached 广播导致整段 GUI 窗口无帧。
     */
    public static GuiCaptureResult awaitGuiScreenCapture(
            Context context, String devicePackage, long requestId, String stepName, long timeoutMs)
            throws InterruptedException {
        if (context == null || requestId == 0L) {
            return GuiCaptureResult.failed("invalid_request");
        }
        GuiCaptureResult providerResult = tryCaptureViaProvider(context, requestId, stepName);
        if (providerResult != null) {
            return providerResult;
        }
        PendingGuiCapture pending = new PendingGuiCapture();
        pendingGuiCaptures.put(requestId, pending);
        try {
            requestGuiScreenCapture(context, devicePackage, requestId, stepName);
            boolean done = pending.latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!done) {
                return GuiCaptureResult.timeout();
            }
            if (pending.ok && pending.uri != null && !pending.uri.isEmpty()) {
                return GuiCaptureResult.success(pending.uri);
            }
            return GuiCaptureResult.failed(
                    pending.error != null ? pending.error : "capture_failed");
        } finally {
            pendingGuiCaptures.remove(requestId, pending);
        }
    }

    private static GuiCaptureResult tryCaptureViaProvider(
            Context context, long requestId, String stepName) {
        try {
            Bundle extras = new Bundle();
            extras.putLong(TestReplyContract.EXTRA_REQUEST_ID, requestId);
            if (stepName != null) {
                extras.putString(TestReplyContract.EXTRA_STEP_NAME, stepName);
            }
            Uri uri = Uri.parse("content://" + TestReplyContract.DEVICE_CAPTURE_AUTHORITY);
            Bundle result = context.getApplicationContext().getContentResolver().call(
                    uri,
                    TestReplyContract.PROVIDER_METHOD_GUI_CAPTURE,
                    stepName,
                    extras);
            if (result == null) {
                Log.w("TestReplySdk", "gui_capture provider returned null, fallback broadcast");
                return null;
            }
            if (result.getBoolean(TestReplyContract.EXTRA_CAPTURE_OK, false)) {
                String captureUri = result.getString(TestReplyContract.EXTRA_CAPTURE_URI);
                if (captureUri != null && !captureUri.isEmpty()) {
                    Log.i("TestReplySdk", "gui_capture via provider ok requestId=" + requestId);
                    return GuiCaptureResult.success(captureUri);
                }
                return GuiCaptureResult.failed("provider_empty_uri");
            }
            String err = result.getString(TestReplyContract.EXTRA_CAPTURE_ERROR);
            return GuiCaptureResult.failed(err != null ? err : "provider_failed");
        } catch (Exception e) {
            Log.w("TestReplySdk", "gui_capture provider failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * 通过 Demo ContentProvider 唤醒进程并自拉 QAActivity（聊天框）。
     * Binder 调用可解冻 cached Demo；比应用内 {@code am start}（无 shell 权限）可靠。
     */
    public static boolean bringDeviceToForeground(Context context) {
        if (context == null) {
            return false;
        }
        try {
            Uri uri = Uri.parse("content://" + TestReplyContract.DEVICE_CAPTURE_AUTHORITY);
            Bundle result = context.getApplicationContext().getContentResolver().call(
                    uri,
                    TestReplyContract.PROVIDER_METHOD_BRING_FRONT,
                    null,
                    null);
            boolean ok = result != null
                    && result.getBoolean(TestReplyContract.EXTRA_BRING_FRONT_OK, false);
            if (!ok) {
                String err = result != null
                        ? result.getString(TestReplyContract.EXTRA_BRING_FRONT_ERROR) : "null";
                Log.w("TestReplySdk", "bring_front failed: " + err);
            } else {
                Log.i("TestReplySdk", "bring_front ok via provider");
            }
            return ok;
        } catch (Exception e) {
            Log.w("TestReplySdk", "bring_front exception: " + e.getMessage());
            return false;
        }
    }

    /**
     * Device 侧截图完成后回传 FileProvider URI。
     */
    public static void publishGuiScreenCaptureResult(
            Context context, long requestId, String captureUri, boolean ok, String error) {
        if (context == null || requestId == 0L) {
            return;
        }
        Intent intent = new Intent(TestReplyContract.ACTION_GUI_SCREEN_CAPTURE_RESULT);
        intent.putExtra(TestReplyContract.EXTRA_REQUEST_ID, requestId);
        intent.putExtra(TestReplyContract.EXTRA_CAPTURE_OK, ok);
        if (captureUri != null) {
            intent.putExtra(TestReplyContract.EXTRA_CAPTURE_URI, captureUri);
        }
        if (error != null) {
            intent.putExtra(TestReplyContract.EXTRA_CAPTURE_ERROR, error);
        }
        TestReplyConfig cfg = config;
        String targetPackage = cfg != null
                ? cfg.getTargetPackage()
                : TestReplyContract.DEFAULT_TARGET_PACKAGE;
        intent.setPackage(targetPackage);
        context.getApplicationContext().sendBroadcast(intent);
    }

    /**
     * TestApp 注册截图结果监听（配合 {@link #awaitGuiScreenCapture}）。
     */
    public static BroadcastReceiver registerGuiScreenCaptureResultListener(Context context) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null
                        || !TestReplyContract.ACTION_GUI_SCREEN_CAPTURE_RESULT.equals(
                                intent.getAction())) {
                    return;
                }
                long requestId = intent.getLongExtra(TestReplyContract.EXTRA_REQUEST_ID, 0L);
                if (requestId == 0L) {
                    return;
                }
                PendingGuiCapture pending = pendingGuiCaptures.get(requestId);
                if (pending == null) {
                    return;
                }
                pending.ok = intent.getBooleanExtra(TestReplyContract.EXTRA_CAPTURE_OK, false);
                pending.uri = intent.getStringExtra(TestReplyContract.EXTRA_CAPTURE_URI);
                pending.error = intent.getStringExtra(TestReplyContract.EXTRA_CAPTURE_ERROR);
                pending.latch.countDown();
            }
        };
        IntentFilter filter = new IntentFilter(
                TestReplyContract.ACTION_GUI_SCREEN_CAPTURE_RESULT);
        Context appContext = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            appContext.registerReceiver(receiver, filter);
        }
        return receiver;
    }

    public static final class GuiCaptureResult {
        private final boolean success;
        private final boolean timedOut;
        private final String uri;
        private final String error;

        private GuiCaptureResult(boolean success, boolean timedOut, String uri, String error) {
            this.success = success;
            this.timedOut = timedOut;
            this.uri = uri;
            this.error = error;
        }

        public static GuiCaptureResult success(String uri) {
            return new GuiCaptureResult(true, false, uri, null);
        }

        public static GuiCaptureResult failed(String error) {
            return new GuiCaptureResult(false, false, null, error);
        }

        public static GuiCaptureResult timeout() {
            return new GuiCaptureResult(false, true, null, "timeout");
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isTimedOut() {
            return timedOut;
        }

        public String getUri() {
            return uri;
        }

        public String getError() {
            return error;
        }
    }

    private static final class PendingGuiCapture {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile boolean ok;
        volatile String uri;
        volatile String error;
    }

    /**
     * 用户发送问题后调用，用于记录轮次与问题文本。
     */
    public static int onUserMessageSent(String sessionId, String userMessage) {
        return aggregator.onUserMessageSent(sessionId, userMessage);
    }

    /**
     * 流式回复片段（可选）。
     */
    public static void onReplyChunk(String sessionId, String chunk) {
        aggregator.appendChunk(sessionId, chunk);
    }

    /**
     * 流式回复结束，自动拼接并发布最终回复。
     */
    public static void finalizeReply(Context context, String sessionId, String outputType) {
        TestReplyEvent event = aggregator.finalizeReply(sessionId, outputType);
        if (event != null) {
            publishReply(context, event);
        }
    }

    /**
     * 非流式单条 TEXT 回复。
     */
    public static void publishTextReply(Context context, String sessionId, String replyText) {
        TestReplyEvent event = aggregator.buildTextReply(
                sessionId, replyText, TestReplyContract.OUTPUT_TEXT);
        if (event != null) {
            publishReply(context, event);
        }
    }

    /**
     * GUI 任务完成（TASK_STATUS taskCompleted=true）时调用。
     */
    public static void onTaskCompleted(Context context, String sessionId, int statusCode) {
        TestReplyEvent event = aggregator.buildTaskCompletedEvent(
                sessionId, statusCode, TestReplyContract.OUTPUT_TASK_STATUS);
        if (event != null) {
            publishReply(context, event);
        }
    }

    /**
     * 直接发布一条回复事件。
     */
    public static void publishReply(Context context, TestReplyEvent event) {
        if (context == null || event == null) {
            return;
        }
        TestReplyConfig cfg = config;
        String targetPackage = cfg != null
                ? cfg.getTargetPackage()
                : TestReplyContract.DEFAULT_TARGET_PACKAGE;

        TestReplyEvent enriched = mergeConfig(event, cfg);
        Context app = context.getApplicationContext();

        // 优先 ContentProvider.call：小米 Greezer 会丢掉打到 cached TestApp 的广播
        boolean delivered = false;
        try {
            Uri uri = Uri.parse("content://" + TestReplyContract.PROVIDER_AUTHORITY);
            Bundle result = app.getContentResolver().call(
                    uri,
                    TestReplyContract.PROVIDER_METHOD_PUBLISH,
                    null,
                    enriched.toBundle());
            delivered = result != null && result.getBoolean("ok", false);
            Log.i("TestReplySdk", "provider publish ok=" + delivered
                    + " round=" + enriched.getRound()
                    + " type=" + enriched.getOutputType());
        } catch (Exception e) {
            Log.w("TestReplySdk", "provider publish failed: " + e.getMessage());
        }

        if (delivered) {
            return;
        }

        Intent intent = enriched.toBroadcastIntent(targetPackage);
        intent.setComponent(new android.content.ComponentName(
                targetPackage,
                "com.example.testapp.receiver.TestReplyBridgeReceiver"));
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        app.sendBroadcast(intent);
    }

    /** 操控指令的结果截图已经上传，通知 TestApp 再拍一张 gui_poll。 */
    public static void publishGuiPollAfterControl(Context context, String cmdName) {
        if (context == null) {
            return;
        }
        TestReplyConfig cfg = config;
        String targetPackage = cfg != null
                ? cfg.getTargetPackage()
                : TestReplyContract.DEFAULT_TARGET_PACKAGE;
        Context app = context.getApplicationContext();
        String name = cmdName != null ? cmdName : "";
        try {
            Bundle extras = new Bundle();
            extras.putString(TestReplyContract.EXTRA_GUI_CMD_NAME, name);
            Uri uri = Uri.parse("content://" + TestReplyContract.PROVIDER_AUTHORITY);
            Bundle result = app.getContentResolver().call(
                    uri,
                    "publish_gui_poll_after_control",
                    null,
                    extras);
            boolean ok = result != null && result.getBoolean("ok", false);
            Log.i("TestReplySdk", "provider gui_poll_after_control ok=" + ok + " cmd=" + name);
            if (ok) {
                return;
            }
        } catch (Exception e) {
            Log.w("TestReplySdk", "provider gui_poll_after_control failed: " + e.getMessage());
        }
        Intent intent = new Intent(TestReplyContract.ACTION_GUI_POLL_AFTER_CONTROL);
        intent.setPackage(targetPackage);
        intent.setComponent(new android.content.ComponentName(
                targetPackage,
                "com.example.testapp.receiver.TestReplyBridgeReceiver"));
        intent.putExtra(TestReplyContract.EXTRA_GUI_CMD_NAME, name);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        app.sendBroadcast(intent);
    }

    /** 上报 GUI 单步时延 JSON 到 TestApp。 */
    public static void publishGuiStepLatency(Context context, int round, String jsonPayload) {
        if (context == null || jsonPayload == null || jsonPayload.isEmpty()) {
            return;
        }
        TestReplyConfig cfg = config;
        String targetPackage = cfg != null
                ? cfg.getTargetPackage()
                : TestReplyContract.DEFAULT_TARGET_PACKAGE;
        Context app = context.getApplicationContext();
        try {
            Bundle extras = new Bundle();
            extras.putString(TestReplyContract.EXTRA_GUI_STEP_LATENCY_JSON, jsonPayload);
            extras.putInt(TestReplyContract.EXTRA_GUI_STEP_LATENCY_ROUND, Math.max(0, round));
            if (cfg != null) {
                if (cfg.getTaskId() != null) {
                    extras.putString(TestReplyContract.EXTRA_TASK_ID, cfg.getTaskId());
                }
                if (cfg.getCaseId() != null) {
                    extras.putString(TestReplyContract.EXTRA_CASE_ID, cfg.getCaseId());
                }
            }
            Uri uri = Uri.parse("content://" + TestReplyContract.PROVIDER_AUTHORITY);
            Bundle result = app.getContentResolver().call(
                    uri,
                    "publish_gui_step_latency",
                    null,
                    extras);
            boolean ok = result != null && result.getBoolean("ok", false);
            Log.i("TestReplySdk", "provider gui_step_latency ok=" + ok + " round=" + round);
            if (ok) {
                return;
            }
        } catch (Exception e) {
            Log.w("TestReplySdk", "provider gui_step_latency failed: " + e.getMessage());
        }
        Intent intent = new Intent(TestReplyContract.ACTION_GUI_STEP_LATENCY);
        intent.setPackage(targetPackage);
        intent.putExtra(TestReplyContract.EXTRA_GUI_STEP_LATENCY_JSON, jsonPayload);
        intent.putExtra(TestReplyContract.EXTRA_GUI_STEP_LATENCY_ROUND, Math.max(0, round));
        if (cfg != null) {
            if (cfg.getTaskId() != null) {
                intent.putExtra(TestReplyContract.EXTRA_TASK_ID, cfg.getTaskId());
            }
            if (cfg.getCaseId() != null) {
                intent.putExtra(TestReplyContract.EXTRA_CASE_ID, cfg.getCaseId());
            }
        }
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        app.sendBroadcast(intent);
    }

    private static TestReplyEvent mergeConfig(TestReplyEvent event, TestReplyConfig cfg) {
        if (cfg == null) {
            return event;
        }
        TestReplyEvent.Builder builder = new TestReplyEvent.Builder()
                .sessionId(event.getSessionId())
                .round(event.getRound())
                .userMessage(event.getUserMessage())
                .replyText(event.getReplyText())
                .outputType(event.getOutputType())
                .finalReply(event.isFinalReply())
                .taskCompleted(event.isTaskCompleted())
                .timestampMs(event.getTimestampMs())
                .sentAtMs(event.getSentAtMs())
                .latencyMs(event.getLatencyMs())
                .firstTokenLatencyMs(event.getFirstTokenLatencyMs())
                .segments(event.getSegments());
        builder.taskId(event.getTaskId() != null ? event.getTaskId() : cfg.getTaskId());
        builder.caseId(event.getCaseId() != null ? event.getCaseId() : cfg.getCaseId());
        return builder.build();
    }

    /**
     * TestApp 侧注册回复监听。
     */
    public static BroadcastReceiver registerListener(
            Context context, final TestReplyListener listener) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (listener == null) {
                    return;
                }
                TestReplyEvent event = TestReplyEvent.fromIntent(intent);
                if (event != null) {
                    listener.onReply(event);
                }
            }
        };
        IntentFilter filter = new IntentFilter(TestReplyContract.ACTION_REPLY);
        Context appContext = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                    receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            appContext.registerReceiver(receiver, filter);
        }
        return receiver;
    }

    public static void unregisterListener(Context context, BroadcastReceiver receiver) {
        if (context == null || receiver == null) {
            return;
        }
        try {
            context.getApplicationContext().unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
            // already unregistered
        }
    }

    public interface TestReplyListener {
        void onReply(TestReplyEvent event);
    }
}
