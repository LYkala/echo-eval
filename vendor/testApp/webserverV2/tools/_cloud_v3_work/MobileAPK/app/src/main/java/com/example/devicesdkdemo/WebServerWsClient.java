package com.example.devicesdkdemo;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.view.View;

import com.huawei.aisfsdkv2.component.androidfunc.screenshot.MediaScreenshotService;
import com.huawei.aisfsdkv2.component.androidfunc.screenshot.ScreenshotManagerImpl;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * DeviceSDK Demo 直连 webserver /ws/device：
 * ws_shot_capture 截图；files/execute_test/stop_test 用例执行与 AISF 回复上报。
 */
public final class WebServerWsClient {
    public static final String TAG = "WebServerWsClient";

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ws-device-client");
        t.setDaemon(true);
        return t;
    });

    private static volatile WebServerWsClient instance;
    private static volatile Context appContext;

    private final OkHttpClient httpClient;
    private final String deviceId;
    private final WebServerCaseRunner caseRunner = new WebServerCaseRunner();
    private final DeviceConsoleLogStreamer consoleLogStreamer = new DeviceConsoleLogStreamer();
    private final Map<String, String> pendingFiles = new HashMap<>();
    private WebSocket webSocket;
    private boolean registered;
    private volatile boolean shouldRun;
    private volatile String wsUrl = "";
    private volatile String currentTaskId = "";
    private volatile String currentCaseId = "";
    private volatile String currentBaseUrl = "";
    /** 忽略主动关闭/旧连接回调，避免 scheduleReconnect 误杀刚连上的新 WS。 */
    private volatile int connectEpoch = 0;

    private WebServerWsClient() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .pingInterval(15, TimeUnit.SECONDS)
                .build();
        deviceId = buildDeviceId();
    }

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(
                ConfigActivity.PREFS_NAME, Context.MODE_PRIVATE);
        ConfigActivity.ensureWebServerDefaults(prefs);
        if (!prefs.getBoolean(WebServerReporter.KEY_WEBSERVER_ENABLED, false)) {
            Log.i(TAG, "webserver disabled, skip WS client (relay via TestApp)");
            return;
        }
        getInstance().startFromPrefs(prefs);
    }

    public static void refreshFromPrefs(Context context) {
        appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(
                ConfigActivity.PREFS_NAME, Context.MODE_PRIVATE);
        ConfigActivity.ensureWebServerDefaults(prefs);
        WebServerWsClient client = instance;
        if (client == null) {
            init(context);
            return;
        }
        if (!prefs.getBoolean(WebServerReporter.KEY_WEBSERVER_ENABLED, false)) {
            client.stop();
            return;
        }
        client.startFromPrefs(prefs);
    }

    /** 供 WebServerReporter 补发 WS case_reply（可能为 null）。 */
    public static WebServerWsClient getInstanceIfAny() {
        return instance;
    }

    private static WebServerWsClient getInstance() {
        if (instance == null) {
            synchronized (WebServerWsClient.class) {
                if (instance == null) {
                    instance = new WebServerWsClient();
                }
            }
        }
        return instance;
    }

    private void startFromPrefs(SharedPreferences prefs) {
        String base = prefs.getString(WebServerReporter.KEY_WEBSERVER_URL, "");
        if (base == null) {
            base = "";
        }
        base = base.trim();
        if (base.isEmpty()) {
            base = ConfigActivity.DEFAULT_WEBSERVER_URL;
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String nextUrl = base.replace("https://", "wss://")
                .replace("http://", "ws://") + "/ws/device";
        if (nextUrl.equals(wsUrl) && webSocket != null) {
            return;
        }
        wsUrl = nextUrl;
        shouldRun = true;
        connect();
    }

    public void stop() {
        shouldRun = false;
        registered = false;
        consoleLogStreamer.stop();
        connectEpoch++;
        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            try {
                ws.close(1000, "stop");
            } catch (Exception ignored) {
            }
        }
    }

    /** 供控制台日志流经当前 WS 发送。 */
    boolean sendConsoleLogJson(JSONObject msg) {
        WebSocket ws = webSocket;
        if (ws == null || !registered || msg == null) {
            return false;
        }
        try {
            msg.put("deviceId", deviceId);
            return ws.send(msg.toString());
        } catch (Exception e) {
            Log.w(TAG, "sendConsoleLogJson: " + e.getMessage());
            return false;
        }
    }

    private void connect() {
        EXEC.execute(() -> {
            if (!shouldRun || wsUrl.isEmpty()) {
                return;
            }
            final int epoch = ++connectEpoch;
            WebSocket old = webSocket;
            webSocket = null;
            if (old != null) {
                try {
                    old.close(1000, "reconnect");
                } catch (Exception ignored) {
                }
            }
            registered = false;
            Log.i(TAG, "connecting " + wsUrl + " epoch=" + epoch);
            Request request = new Request.Builder().url(wsUrl).build();
            WebSocket created = httpClient.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    if (epoch != connectEpoch) {
                        try {
                            webSocket.close(1000, "stale");
                        } catch (Exception ignored) {
                        }
                        return;
                    }
                    Log.i(TAG, "ws open");
                    sendRegister(webSocket);
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    if (epoch != connectEpoch) {
                        return;
                    }
                    handleMessage(webSocket, text);
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    Log.i(TAG, "ws closed " + code + " " + reason + " epoch=" + epoch);
                    if (epoch != connectEpoch) {
                        return;
                    }
                    registered = false;
                    consoleLogStreamer.stop();
                    if (WebServerWsClient.this.webSocket == webSocket) {
                        WebServerWsClient.this.webSocket = null;
                    }
                    scheduleReconnect(epoch);
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    Log.w(TAG, "ws failure epoch=" + epoch + ": " + t.getMessage());
                    if (epoch != connectEpoch) {
                        return;
                    }
                    registered = false;
                    consoleLogStreamer.stop();
                    if (WebServerWsClient.this.webSocket == webSocket) {
                        WebServerWsClient.this.webSocket = null;
                    }
                    scheduleReconnect(epoch);
                }
            });
            if (epoch == connectEpoch) {
                webSocket = created;
            } else {
                try {
                    created.close(1000, "stale");
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void scheduleReconnect(int epoch) {
        if (!shouldRun || epoch != connectEpoch) {
            return;
        }
        // 延迟不要占满单线程 EXEC，否则会卡住发消息并在醒来时误关新连接
        new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!shouldRun || epoch != connectEpoch) {
                return;
            }
            if (webSocket != null && registered) {
                return;
            }
            connect();
        }, "ws-device-reconnect").start();
    }

    private void sendRegister(WebSocket ws) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "register");
            msg.put("deviceId", deviceId);
            msg.put("deviceName", Build.MANUFACTURER + " " + Build.MODEL);
            JSONObject info = new JSONObject();
            info.put("manufacturer", Build.MANUFACTURER);
            info.put("model", Build.MODEL);
            info.put("androidVersion", Build.VERSION.RELEASE);
            info.put("clientApp", "devicesdkdemo");
            info.put("appVersion", "cloud-v3-console-log");
            msg.put("deviceInfo", info);
            ws.send(msg.toString());
        } catch (Exception e) {
            Log.w(TAG, "register failed: " + e.getMessage());
        }
    }

    private void handleMessage(WebSocket ws, String text) {
        try {
            JSONObject data = new JSONObject(text);
            String type = data.optString("type", "");
            if ("registered".equals(type)) {
                registered = true;
                Log.i(TAG, "registered as " + deviceId);
                consoleLogStreamer.setSender(this::sendConsoleLogJson);
                // 默认开启：首页可随时点设备看实时日志
                consoleLogStreamer.setEnabled(true);
                return;
            }
            if ("ping".equals(type)) {
                JSONObject pong = new JSONObject();
                pong.put("type", "pong");
                pong.put("deviceId", deviceId);
                ws.send(pong.toString());
                return;
            }
            if ("console_log_control".equals(type)) {
                boolean enable = data.optBoolean("enable", true);
                consoleLogStreamer.setSender(this::sendConsoleLogJson);
                consoleLogStreamer.setEnabled(enable);
                JSONObject ack = new JSONObject();
                ack.put("type", "console_log_control_ack");
                ack.put("deviceId", deviceId);
                ack.put("enable", enable);
                ws.send(ack.toString());
                return;
            }
            if ("ws_shot_capture".equals(type)) {
                // 9526 截图已改由 TestApp（MediaProjection）负责，Demo 不再响应
                Log.i(TAG, "ws_shot_capture ignored (handed to TestApp) requestId="
                        + data.optString("requestId", ""));
                return;
            }
            if ("files".equals(type)) {
                handleFiles(ws, data);
                return;
            }
            if ("execute_test".equals(type)) {
                handleExecuteTest(ws, data);
                return;
            }
            if ("stop_test".equals(type)) {
                handleStopTest(data);
                return;
            }
            if ("delete_file".equals(type)) {
                handleDeleteFile(data);
            }
        } catch (Exception e) {
            Log.w(TAG, "handleMessage error: " + e.getMessage());
        }
    }

    private void handleFiles(WebSocket ws, JSONObject data) {
        JSONArray files = data.optJSONArray("files");
        if (files == null) {
            return;
        }
        Context ctx = appContext;
        if (ctx == null) {
            return;
        }
        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.optJSONObject(i);
            if (f == null) {
                continue;
            }
            String name = f.optString("name", "");
            String content = f.optString("content", "");
            if (name.isEmpty()) {
                continue;
            }
            pendingFiles.put(name, content);
            WebServerCaseRunner.saveTestFile(ctx, name, content);
            Log.i(TAG, "received testcase " + name + " len=" + content.length());
        }
    }

    private void handleExecuteTest(WebSocket ws, JSONObject data) {
        String filename = data.optString("filename", "");
        currentTaskId = data.optString("taskId", "");
        currentCaseId = data.optString("caseId", "");
        if (currentCaseId.isEmpty() && !filename.isEmpty()) {
            int dot = filename.lastIndexOf('.');
            currentCaseId = dot > 0 ? filename.substring(0, dot) : filename;
        }
        currentBaseUrl = data.optString("baseUrl", "");
        if (currentBaseUrl.isEmpty()) {
            SharedPreferences prefs = appContext.getSharedPreferences(
                    ConfigActivity.PREFS_NAME, Context.MODE_PRIVATE);
            currentBaseUrl = prefs.getString(WebServerReporter.KEY_WEBSERVER_URL, "");
        }
        Log.i(TAG, "execute_test file=" + filename + " task=" + currentTaskId
                + " case=" + currentCaseId);
        Context ctx = appContext;
        if (ctx == null || filename.isEmpty()) {
            sendTestCompleted(ws, currentCaseId, false, "无上下文或文件名为空");
            return;
        }
        String content = pendingFiles.get(filename);
        if (content == null || content.isEmpty()) {
            sendTestCompleted(ws, currentCaseId, false, "未收到用例文件");
            return;
        }
        final String caseId = currentCaseId;
        final String taskId = currentTaskId;
        final String baseUrl = currentBaseUrl;
        caseRunner.runCase(ctx, filename, content, taskId, caseId, deviceId, baseUrl,
                new WebServerCaseRunner.Callback() {
                    @Override
                    public void onStepProgress(int stepIndex, int stepTotal, String stepId, String action) {
                        sendStepProgress(stepIndex, stepTotal, stepId, action);
                    }

                    @Override
                    public void onCaseFinished(String finishedCaseId, boolean success, String message) {
                        sendTestCompleted(ws, finishedCaseId, success, message);
                    }
                });
    }

    private void handleStopTest(JSONObject data) {
        String reason = data.optString("reason", "服务器停止");
        Log.i(TAG, "stop_test reason=" + reason);
        caseRunner.requestStop(reason);
        // 等同聊天页点「停止任务」：停 AISF / 算力主机，避免 GUI 继续跑
        QAActivity.stopAisfFromWebServer(appContext, reason);
    }

    private void handleDeleteFile(JSONObject data) {
        Context ctx = appContext;
        if (ctx == null) {
            return;
        }
        String filename = data.optString("filename", "");
        if (!filename.isEmpty()) {
            pendingFiles.remove(filename);
            WebServerCaseRunner.deleteTestFile(ctx, filename);
        }
    }

    public void sendCaseReply(
            String taskId,
            String caseId,
            int round,
            String userMessage,
            String replyText,
            String outputType,
            long latencyMs,
            long firstTokenLatencyMs,
            long sentAtMs,
            boolean taskCompleted,
            String sessionId
    ) {
        WebSocket ws = webSocket;
        if (ws == null || !registered) {
            return;
        }
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "case_reply");
            if (taskId != null && !taskId.isEmpty()) {
                msg.put("taskId", taskId);
            } else if (!currentTaskId.isEmpty()) {
                msg.put("taskId", currentTaskId);
            }
            if (caseId != null && !caseId.isEmpty()) {
                msg.put("caseId", caseId);
                msg.put("testId", caseId);
            } else if (!currentCaseId.isEmpty()) {
                msg.put("caseId", currentCaseId);
                msg.put("testId", currentCaseId);
            }
            msg.put("deviceId", deviceId);
            msg.put("round", round);
            msg.put("userMessage", userMessage != null ? userMessage : "");
            msg.put("replyText", replyText != null ? replyText : "");
            msg.put("outputType", outputType != null ? outputType : "TEXT");
            msg.put("isFinal", true);
            msg.put("taskCompleted", taskCompleted);
            msg.put("sessionId", sessionId != null ? sessionId : "");
            if (latencyMs >= 0) {
                msg.put("latencyMs", latencyMs);
            }
            if (firstTokenLatencyMs >= 0) {
                msg.put("firstTokenLatencyMs", firstTokenLatencyMs);
            }
            if (sentAtMs > 0) {
                msg.put("sentAtMs", sentAtMs);
            }
            ws.send(msg.toString());
            Log.i(TAG, "case_reply ws round=" + round + " textLen="
                    + (replyText != null ? replyText.length() : 0));
        } catch (Exception e) {
            Log.w(TAG, "sendCaseReply failed: " + e.getMessage());
        }
    }

    private void sendStepProgress(int stepIndex, int stepTotal, String stepId, String action) {
        WebSocket ws = webSocket;
        if (ws == null || !registered || currentTaskId.isEmpty() || currentCaseId.isEmpty()) {
            return;
        }
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "step_progress");
            msg.put("taskId", currentTaskId);
            msg.put("caseId", currentCaseId);
            msg.put("testId", currentCaseId);
            msg.put("deviceId", deviceId);
            msg.put("stepIndex", stepIndex);
            msg.put("stepTotal", stepTotal);
            if (stepId != null) {
                msg.put("stepId", stepId);
            }
            if (action != null) {
                msg.put("action", action);
            }
            ws.send(msg.toString());
        } catch (Exception ignored) {
        }
    }

    private void sendTestCompleted(WebSocket ws, String testId, boolean success, String message) {
        if (ws == null) {
            ws = webSocket;
        }
        if (ws == null || !registered) {
            return;
        }
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "test_completed");
            msg.put("testId", testId != null ? testId : currentCaseId);
            msg.put("taskId", currentTaskId);
            msg.put("deviceId", deviceId);
            msg.put("success", success);
            msg.put("message", message != null ? message : "");
            ws.send(msg.toString());
            Log.i(TAG, "test_completed case=" + testId + " success=" + success);
        } catch (Exception e) {
            Log.w(TAG, "test_completed failed: " + e.getMessage());
        }
    }

    private void captureAndUpload(WebSocket ws, String requestId, int maxWidth, int jpegQuality) {
        EXEC.execute(() -> {
            try {
                byte[] jpeg = captureJpeg(maxWidth, jpegQuality);
                if (jpeg == null || jpeg.length == 0) {
                    sendCaptureError(ws, requestId, buildCaptureErrorHint());
                    Log.w(TAG, "capture empty requestId=" + requestId);
                    return;
                }
                String b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP);
                JSONObject msg = new JSONObject();
                msg.put("type", "ws_shot_upload");
                msg.put("requestId", requestId);
                msg.put("deviceId", deviceId);
                msg.put("filename", "ws_shot_" + System.currentTimeMillis() + ".jpg");
                msg.put("data", "data:image/jpeg;base64," + b64);
                boolean sent = ws.send(msg.toString());
                Log.i(TAG, "ws_shot_upload sent=" + sent + " bytes=" + jpeg.length
                        + " requestId=" + requestId);
            } catch (Exception e) {
                Log.w(TAG, "captureAndUpload failed: " + e.getMessage());
            }
        });
    }

    private void sendCaptureError(WebSocket ws, String requestId, String message) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "ws_shot_error");
            msg.put("requestId", requestId);
            msg.put("deviceId", deviceId);
            msg.put("message", message);
            ws.send(msg.toString());
        } catch (Exception e) {
            Log.w(TAG, "ws_shot_error send failed: " + e.getMessage());
        }
    }

    private String buildCaptureErrorHint() {
        if (MediaScreenshotService.isScreenshotReady()) {
            return "全屏截图失败，请确认无障碍/录屏服务正常后重试";
        }
        return "请先在 Demo 主界面开启「录屏(MediaProjection)」权限；"
                + "可选开无障碍以优先截跨 App 画面";
    }

    private byte[] captureJpeg(int maxWidth, int jpegQuality) {
        Bitmap src = captureAisfBitmap();
        boolean recycleSrc = false;
        if (src == null) {
            src = captureActivityBitmap();
            recycleSrc = src != null;
        }
        if (src == null) {
            return null;
        }
        try {
            return encodeJpeg(src, maxWidth, jpegQuality);
        } finally {
            if (recycleSrc && src != null && !src.isRecycled()) {
                src.recycle();
            }
        }
    }

    /** 无障碍 / MediaProjection 全屏截图（可跨 App，Demo 可不在前台）。 */
    private Bitmap captureAisfBitmap() {
        try {
            ScreenshotManagerImpl mgr = ScreenshotManagerImpl.getInstance();
            Bitmap bmp = mgr.takeValidScreenshot();
            if (bmp != null) {
                Log.i(TAG, "AISF takeValidScreenshot ok "
                        + bmp.getWidth() + "x" + bmp.getHeight());
                return bmp;
            }
            bmp = mgr.takeScreenshot();
            if (bmp != null) {
                Log.i(TAG, "AISF takeScreenshot ok "
                        + bmp.getWidth() + "x" + bmp.getHeight());
                return bmp;
            }
        } catch (Throwable t) {
            Log.w(TAG, "AISF screenshot failed: " + t.getMessage());
        }
        return null;
    }

    private byte[] encodeJpeg(Bitmap src, int maxWidth, int jpegQuality) {
        Bitmap scaled = src;
        try {
            int w = src.getWidth();
            if (maxWidth > 0 && w > maxWidth) {
                int h = src.getHeight();
                int nh = Math.max(1, (int) (h * (maxWidth / (float) w)));
                scaled = Bitmap.createScaledBitmap(src, maxWidth, nh, true);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int q = Math.max(40, Math.min(jpegQuality, 95));
            scaled.compress(Bitmap.CompressFormat.JPEG, q, out);
            return out.toByteArray();
        } finally {
            if (scaled != src && scaled != null && !scaled.isRecycled()) {
                scaled.recycle();
            }
        }
    }

    /** Demo 窗口截图（仅 Demo 界面，作为 AISF 不可用时的回退）。 */
    private Bitmap captureActivityBitmap() {
        Activity activity = DemoApp.getForegroundActivity();
        if (activity == null) {
            Log.w(TAG, "no foreground activity for fallback capture");
            return null;
        }
        final Bitmap[] holder = new Bitmap[1];
        final CountDownLatch latch = new CountDownLatch(1);
        activity.runOnUiThread(() -> {
            try {
                View root = activity.getWindow().getDecorView().getRootView();
                int w = root.getWidth();
                int h = root.getHeight();
                if (w <= 0 || h <= 0) {
                    Log.w(TAG, "activity root size invalid: " + w + "x" + h);
                    return;
                }
                Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bmp);
                root.draw(canvas);
                holder[0] = bmp;
                Log.i(TAG, "activity fallback capture ok " + w + "x" + h);
            } catch (Exception e) {
                Log.w(TAG, "activity fallback capture failed: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(6, TimeUnit.SECONDS)) {
                Log.w(TAG, "activity fallback capture timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return holder[0];
    }

    private static String buildDeviceId() {
        StringBuilder sb = new StringBuilder();
        sb.append(Build.BOARD).append(Build.BRAND).append(Build.DEVICE)
                .append(Build.HARDWARE).append(Build.MANUFACTURER)
                .append(Build.MODEL).append(Build.PRODUCT);
        sb.append(safeProp("ro.boot.qemu.avd_name"));
        sb.append(safeProp("ro.serialno"));
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                sb.append(Build.getSerial());
            } else {
                sb.append(Build.SERIAL);
            }
        } catch (SecurityException ignored) {
            sb.append(Build.SERIAL);
        }
        return "device_" + Integer.toHexString(sb.toString().hashCode());
    }

    private static String safeProp(String key) {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Object value = clazz.getMethod("get", String.class, String.class)
                    .invoke(null, key, "");
            return value != null ? String.valueOf(value) : "";
        } catch (Exception ignored) {
            return "";
        }
    }
}
