package com.example.testapp.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Base64;

import com.example.testapp.service.MediaCaptureService;
import com.example.testapp.service.TestAccessibilityService;
import com.example.testapp.model.WebServerConfig;
import com.example.testreply.TestReplyEvent;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * WebServer WebSocket客户端工具
 * 使用WebSocket与服务端通信
 */
public class WebServerClient {

    private static final String TAG = "WebServerClient";

    /** HTTP客户端 */
    private final OkHttpClient httpClient;
    /** 短超时 HTTP：截图/心跳等，避免与 WS 共用 readTimeout=0 导致后台挂死 */
    private final OkHttpClient uploadHttpClient;

    /** JSON解析器 */
    private final Gson gson;

    /** WebServer配置 */
    private final WebServerConfig config;

    /** 设备ID */
    private final String deviceId;

    /** WebSocket连接 */
    private WebSocket webSocket;

    /** 连接状态回调 */
    private ConnectionCallback callback;

    /** 单例实例 */
    private static WebServerClient instance;

    /** 是否已注册 */
    private boolean isRegistered = false;

    /** 当前执行的任务 ID */
    private String currentTaskId;

    /** 当前执行的用例 ID */
    private String currentCaseId;

    /** WebSocket 断连时缓存的 case_reply，重连后补发 */
    private final List<TestReplyEvent> pendingCaseReplies = new ArrayList<>();
    private static final int PENDING_CASE_REPLY_LIMIT = 30;

    /** case_reply HTTP 上报专用线程（BroadcastReceiver 在主线程回调，禁止同步网络） */
    private final ExecutorService caseReplyExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "case-reply-http");
        t.setDaemon(true);
        return t;
    });

    /** 9526 ws_shot 截图上传线程 */
    private final ExecutorService wsShotExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ws-shot-capture");
        t.setDaemon(true);
        return t;
    });

    public String getCurrentTaskId() {
        return currentTaskId;
    }

    public String getCurrentCaseId() {
        return currentCaseId;
    }

    /** 截图上传回调 */
    private ScreenshotUploadCallback screenshotCallback;

    /**
     * 截图上传回调接口
     */
    public interface ScreenshotUploadCallback {
        void onUploadSuccess(File file);
        void onUploadFailed(File file, String error);
    }

    /**
     * 连接状态回调接口
     */
    public interface ConnectionCallback {
        void onConnected();
        void onDisconnected();
        void onFilesReceived(List<FileData> files);
        void onDeleteFile(String filename);
        void onRefreshFiles();
        void onExecuteTest(String filename);
        void onStopTest(String taskId, String reason);
        void onError(String message);
    }

    /**
     * 文件数据
     */
    public static class FileData {
        public String name;
        public String content;
    }

    /**
     * 文件信息
     */
    public static class FileInfo {
        public String name;
        public long size;
        public long lastModified;
    }

    /**
     * 获取单例实例
     */
    public static synchronized WebServerClient getInstance(WebServerConfig config) {
        if (config != null && (instance == null || instance.config == null)) {
            instance = new WebServerClient(config);
            return instance;
        }
        return instance;
    }

    /**
     * 获取已存在的实例，不会用 null 配置创建新单例。
     */
    public static synchronized WebServerClient peekInstance() {
        return instance;
    }

    /**
     * 私有构造函数
     */
    private WebServerClient(WebServerConfig config) {
        this.config = config;
        this.gson = new Gson();
        this.deviceId = generateDeviceId();

        // WebSocket 客户端：长连接读超时关闭，另开 ping
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)  // WebSocket需要无限等待
                .writeTimeout(30, TimeUnit.SECONDS)
                .pingInterval(10, TimeUnit.SECONDS)  // 心跳间隔
                .build();
        // 截图 / HTTP 心跳：必须有超时，否则进第三方 App 后请求挂死会堵死上传队列
        this.uploadHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(45, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .build();

        Logger.safeInfo(TAG, "WebServerClient initialized with deviceId: " + deviceId);
    }

    /**
     * 生成设备ID。
     * 克隆 AVD 的 Build.* 往往相同，必须混入 qemu AVD 名 / serial，否则多台会注册成同一个 device_*。
     */
    private String generateDeviceId() {
        StringBuilder uniqueId = new StringBuilder();
        uniqueId.append(Build.BOARD)
                .append(Build.BRAND)
                .append(Build.DEVICE)
                .append(Build.HARDWARE)
                .append(Build.MANUFACTURER)
                .append(Build.MODEL)
                .append(Build.PRODUCT);
        uniqueId.append(safeSystemProperty("ro.boot.qemu.avd_name"));
        uniqueId.append(safeSystemProperty("ro.serialno"));
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                uniqueId.append(Build.getSerial());
            } else {
                uniqueId.append(Build.SERIAL);
            }
        } catch (SecurityException ignored) {
            uniqueId.append(Build.SERIAL);
        }
        return "device_" + Integer.toHexString(uniqueId.toString().hashCode())
                + "__testapp";
    }

    private static String safeSystemProperty(String key) {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Object value = clazz.getMethod("get", String.class, String.class)
                    .invoke(null, key, "");
            return value != null ? String.valueOf(value) : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * 获取设备ID
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * 设置连接回调
     */
    public void setCallback(ConnectionCallback callback) {
        this.callback = callback;
    }

    /**
     * 设置截图上传回调
     */
    public void setScreenshotCallback(ScreenshotUploadCallback callback) {
        this.screenshotCallback = callback;
    }

    /**
     * 连接WebSocket
     */
    public void connect() {
        if (config == null) {
            Logger.safeError(TAG, "Cannot connect: WebServerConfig is null");
            return;
        }
        if (webSocket != null) {
            Logger.safeWarn(TAG, "WebSocket already exists, disconnecting first");
            disconnect();
        }

        String wsUrl = config.getWebSocketUrl();
        Logger.safeInfo(TAG, "Connecting to WebSocket: " + wsUrl);

        Request request = new Request.Builder()
                .url(wsUrl)
                .build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Logger.safeInfo(TAG, "WebSocket connected");
                // 发送注册消息
                sendRegisterMessage();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Logger.safeDebug(TAG, "WebSocket message received: " + text);
                handleMessage(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Logger.safeInfo(TAG, "WebSocket closing: " + code + " - " + reason);
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Logger.safeInfo(TAG, "WebSocket closed: " + code + " - " + reason);
                isRegistered = false;
                WebServerClient.this.webSocket = null;
                if (callback != null) {
                    callback.onDisconnected();
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Logger.safeError(TAG, "WebSocket failure: " + t.getMessage(), t);
                isRegistered = false;
                WebServerClient.this.webSocket = null;
                if (callback != null) {
                    callback.onError(t.getMessage());
                    callback.onDisconnected();
                }
            }
        });
    }

    /**
     * 发送注册消息
     */
    private void sendRegisterMessage() {
        Map<String, Object> registerMsg = new HashMap<>();
        registerMsg.put("type", "register");
        registerMsg.put("deviceId", deviceId);

        String avdName = safeSystemProperty("ro.boot.qemu.avd_name");
        String displayName;
        if (avdName != null && !avdName.trim().isEmpty()) {
            displayName = avdName.trim();
        } else {
            displayName = Build.MANUFACTURER + " " + Build.MODEL;
        }
        registerMsg.put("deviceName", displayName);

        Map<String, Object> deviceInfo = new HashMap<>();
        deviceInfo.put("manufacturer", Build.MANUFACTURER);
        deviceInfo.put("model", Build.MODEL);
        deviceInfo.put("androidVersion", Build.VERSION.RELEASE);
        deviceInfo.put("appVersion", "1.0.17-media-ws");
        deviceInfo.put("clientApp", "testapp");
        if (avdName != null && !avdName.trim().isEmpty()) {
            deviceInfo.put("avdName", avdName.trim());
        }
        String serial = safeSystemProperty("ro.serialno");
        if (serial != null && !serial.isEmpty()) {
            deviceInfo.put("serial", serial);
        }
        registerMsg.put("deviceInfo", deviceInfo);

        String json = gson.toJson(registerMsg);
        Logger.safeInfo(TAG, "Sending register message: " + json);

        if (webSocket != null) {
            webSocket.send(json);
        }
    }

    /**
     * 发送心跳（包含文件列表）
     */
    public void sendHeartbeat(File filesDir) {
        if (webSocket == null || !isRegistered) {
            return;
        }

        Map<String, Object> heartbeatMsg = new HashMap<>();
        heartbeatMsg.put("type", "heartbeat");
        heartbeatMsg.put("deviceId", deviceId);

        // 包含文件列表
        List<FileInfo> fileList = getLocalFileList(filesDir);
        heartbeatMsg.put("files", fileList);

        String json = gson.toJson(heartbeatMsg);
        Logger.safeDebug(TAG, "Sending heartbeat with " + fileList.size() + " files");

        if (webSocket != null) {
            webSocket.send(json);
        }
    }

    /**
     * HTTP 心跳兜底：跨 App GUI 时 WS 常被系统冻结，用短超时 HTTP 刷新 last_seen。
     */
    public boolean sendHttpHeartbeat() {
        if (config == null || deviceId == null) {
            return false;
        }
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("deviceId", deviceId);
        if (currentTaskId != null) {
            bodyMap.put("taskId", currentTaskId);
        }
        RequestBody body = RequestBody.create(
                gson.toJson(bodyMap), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(config.getServerUrl() + "/api/device/heartbeat")
                .post(body)
                .build();
        try (Response response = uploadHttpClient.newCall(request).execute()) {
            boolean ok = response.isSuccessful();
            if (!ok) {
                Logger.safeDebug(TAG, "HTTP heartbeat failed: " + response.code());
            }
            return ok;
        } catch (Exception e) {
            Logger.safeDebug(TAG, "HTTP heartbeat error: " + e.getMessage());
            return false;
        }
    }

    /**
     * 发送文件列表更新
     */
    public void sendFileListUpdate(File filesDir) {
        if (webSocket == null || !isRegistered) {
            return;
        }

        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "file_list");
        msg.put("deviceId", deviceId);
        msg.put("files", getLocalFileList(filesDir));

        String json = gson.toJson(msg);
        Logger.safeInfo(TAG, "Sending file list update");

        if (webSocket != null) {
            webSocket.send(json);
        }
    }

    /**
     * 发送文件删除确认
     */
    public void sendFileDeletedConfirm(String filename, boolean success) {
        if (webSocket == null || !isRegistered) {
            return;
        }

        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "file_deleted");
        msg.put("filename", filename);
        msg.put("success", success);

        String json = gson.toJson(msg);

        if (webSocket != null) {
            webSocket.send(json);
        }
    }

    /**
     * 获取本地文件列表
     */
    public List<FileInfo> getLocalFileList(File filesDir) {
        List<FileInfo> fileList = new ArrayList<>();

        File testflowsDir = new File(filesDir, "testflows");
        if (testflowsDir.exists() && testflowsDir.isDirectory()) {
            File[] files = testflowsDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    FileInfo info = new FileInfo();
                    info.name = file.getName();
                    info.size = file.length();
                    info.lastModified = file.lastModified();
                    fileList.add(info);
                }
            }
        }

        return fileList;
    }

    /**
     * 发送日志
     */
    public void sendLog(String message, String level) {
        if (webSocket == null || !isRegistered) {
            return;
        }

        Map<String, Object> logMsg = new HashMap<>();
        logMsg.put("type", "log");
        logMsg.put("message", message);
        logMsg.put("level", level);
        logMsg.put("taskId", currentTaskId);
        logMsg.put("caseId", currentCaseId);
        logMsg.put("deviceId", deviceId);

        String json = gson.toJson(logMsg);

        if (webSocket != null) {
            webSocket.send(json);
        }
    }

    /**
     * 实时分片推送设备日志到 WebServer。
     *
     * @param text  日志文本（可多行）
     * @param reset true 时服务端清空并重新开始 device_live.log
     */
    public void sendLogChunk(String text, boolean reset) {
        if ((text == null || text.isEmpty()) && !reset) {
            return;
        }

        if (webSocket != null && isRegistered) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "log_chunk");
            msg.put("taskId", currentTaskId);
            msg.put("caseId", currentCaseId);
            msg.put("deviceId", deviceId);
            msg.put("reset", reset);
            msg.put("text", text != null ? text : "");
            webSocket.send(gson.toJson(msg));
            return;
        }

        sendLogChunkHttp(text, reset);
    }

    /**
     * WebSocket 断开时仍追加 device_live.log（GUI 接管期间 WS 常掉线）
     */
    public void sendLogChunkHttp(String text, boolean reset) {
        if (config == null) {
            return;
        }
        if ((text == null || text.isEmpty()) && !reset) {
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("taskId", currentTaskId != null ? currentTaskId : "");
        body.put("caseId", currentCaseId != null ? currentCaseId : "");
        body.put("deviceId", deviceId);
        body.put("reset", reset);
        body.put("text", text != null ? text : "");

        String url = config.getServerUrl() + "/api/device/log_chunk";
        RequestBody requestBody = RequestBody.create(
                gson.toJson(body),
                MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String resp = response.body() != null ? response.body().string() : "";
                Logger.safeWarn(TAG, "HTTP log_chunk failed (" + response.code() + "): " + resp);
            }
        } catch (Exception e) {
            Logger.safeWarn(TAG, "HTTP log_chunk error: " + e.getMessage());
        }
    }

    /**
     * 处理接收到的消息
     */
    private void handleMessage(String text) {
        try {
            Map<String, Object> msg = gson.fromJson(text, Map.class);
            String type = (String) msg.get("type");

            switch (type) {
                case "registered":
                    isRegistered = true;
                    Logger.safeInfo(TAG, "Device registered successfully");
                    flushPendingCaseReplies();
                    // 通知ScreenshotManager连接成功
                    ScreenshotManager.getInstance(null).onWebServerConnected();
                    if (callback != null) {
                        callback.onConnected();
                    }
                    break;

                case "heartbeat_ack":
                    Logger.safeDebug(TAG, "Heartbeat acknowledged");
                    break;

                case "ping":
                    // 服务器发送的ping，保持连接活跃
                    Logger.safeDebug(TAG, "Ping received, sending pong");
                    Map<String, Object> pongMsg = new HashMap<>();
                    pongMsg.put("type", "pong");
                    String pongJson = gson.toJson(pongMsg);
                    if (webSocket != null) {
                        webSocket.send(pongJson);
                    }
                    break;

                case "files":
                    // 接收文件
                    List<Map<String, String>> filesList = (List<Map<String, String>>) msg.get("files");
                    if (filesList != null && callback != null) {
                        List<FileData> files = new ArrayList<>();
                        for (Map<String, String> f : filesList) {
                            FileData fd = new FileData();
                            fd.name = f.get("name");
                            fd.content = f.get("content");
                            files.add(fd);
                        }
                        callback.onFilesReceived(files);
                    }
                    break;

                case "delete_file":
                    // 删除文件命令
                    String filename = (String) msg.get("filename");
                    Logger.safeInfo(TAG, "Received delete_file command for: " + filename);
                    if (callback != null) {
                        callback.onDeleteFile(filename);
                    }
                    break;

                case "refresh_files":
                    // 刷新文件列表命令
                    Logger.safeInfo(TAG, "Received refresh_files command");
                    if (callback != null) {
                        callback.onRefreshFiles();
                    }
                    break;

                case "execute_test":
                    // 执行测试命令
                    String testFilename = (String) msg.get("filename");
                    currentTaskId = (String) msg.get("taskId");
                    currentCaseId = (String) msg.get("caseId");
                    if (currentCaseId == null && testFilename != null) {
                        int dotIndex = testFilename.lastIndexOf('.');
                        currentCaseId = dotIndex > 0 ? testFilename.substring(0, dotIndex) : testFilename;
                    }
                    Logger.safeInfo(TAG, "Received execute_test command for: " + testFilename
                            + ", taskId=" + currentTaskId + ", caseId=" + currentCaseId);
                    if (callback != null) {
                        callback.onExecuteTest(testFilename);
                    }
                    break;

                case "stop_test":
                    String stopTaskId = (String) msg.get("taskId");
                    String stopReason = (String) msg.get("reason");
                    if (stopReason == null || stopReason.isEmpty()) {
                        stopReason = "服务器停止";
                    }
                    Logger.safeInfo(TAG, "Received stop_test command, taskId=" + stopTaskId
                            + ", reason=" + stopReason);
                    if (callback != null) {
                        callback.onStopTest(stopTaskId, stopReason);
                    }
                    break;

                case "ws_shot_capture":
                    handleWsShotCapture(msg);
                    break;

                case "error":
                    String errorMsg = (String) msg.get("message");
                    Logger.safeError(TAG, "Server error: " + errorMsg);
                    if (callback != null) {
                        callback.onError(errorMsg);
                    }
                    break;

                default:
                    Logger.safeWarn(TAG, "Unknown message type: " + type);
            }
        } catch (Exception e) {
            Logger.safeError(TAG, "Error handling message: " + e.getMessage(), e);
        }
    }

    private void handleWsShotCapture(Map<String, Object> msg) {
        final String requestId = msg.get("requestId") != null
                ? String.valueOf(msg.get("requestId")) : "";
        final int maxWidth = readInt(msg.get("maxWidth"), 720);
        final int jpegQuality = readInt(msg.get("jpegQuality"), 70);
        final WebSocket ws = webSocket;
        if (ws == null) {
            Logger.safeWarn(TAG, "ws_shot_capture but websocket null requestId=" + requestId);
            return;
        }
        wsShotExecutor.execute(() -> {
            try {
                byte[] jpeg = captureWsShotJpeg(maxWidth, jpegQuality);
                if (jpeg == null || jpeg.length == 0) {
                    sendWsShotError(ws, requestId,
                            "截图失败：请在 TestApp 主界面开启「录屏权限」，可选开无障碍作回退");
                    return;
                }
                String b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP);
                Map<String, Object> upload = new HashMap<>();
                upload.put("type", "ws_shot_upload");
                upload.put("requestId", requestId);
                upload.put("deviceId", deviceId);
                upload.put("filename", "ws_shot_" + System.currentTimeMillis() + ".jpg");
                upload.put("data", "data:image/jpeg;base64," + b64);
                boolean sent = ws.send(gson.toJson(upload));
                Logger.safeInfo(TAG, "ws_shot_upload sent=" + sent
                        + " bytes=" + jpeg.length + " requestId=" + requestId);
            } catch (Exception e) {
                Logger.safeError(TAG, "ws_shot_capture failed: " + e.getMessage(), e);
                sendWsShotError(ws, requestId, "截图异常: " + e.getMessage());
            }
        });
    }

    private void sendWsShotError(WebSocket ws, String requestId, String message) {
        try {
            Map<String, Object> err = new HashMap<>();
            err.put("type", "ws_shot_error");
            err.put("requestId", requestId);
            err.put("deviceId", deviceId);
            err.put("message", message != null ? message : "");
            ws.send(gson.toJson(err));
        } catch (Exception e) {
            Logger.safeWarn(TAG, "ws_shot_error send failed: " + e.getMessage());
        }
    }

    private byte[] captureWsShotJpeg(int maxWidth, int jpegQuality) {
        Bitmap src = null;
        boolean recycle = false;
        try {
            if (MediaCaptureService.isReady() || MediaCaptureService.ensureRunning(null)) {
                src = MediaCaptureService.takeScreenshot();
                if (src != null) {
                    Logger.safeInfo(TAG, "ws_shot via MediaProjection "
                            + src.getWidth() + "x" + src.getHeight());
                }
            }
            if (src == null) {
                src = captureViaAccessibilityBitmap();
                recycle = src != null;
                if (src != null) {
                    Logger.safeInfo(TAG, "ws_shot via accessibility "
                            + src.getWidth() + "x" + src.getHeight());
                }
            }
            if (src == null) {
                return null;
            }
            return encodeJpeg(src, maxWidth, jpegQuality);
        } finally {
            if (recycle && src != null && !src.isRecycled()) {
                src.recycle();
            }
        }
    }

    private Bitmap captureViaAccessibilityBitmap() {
        if (Build.VERSION.SDK_INT < 30) {
            return null;
        }
        TestAccessibilityService a11y = TestAccessibilityService.getInstance();
        if (a11y == null) {
            return null;
        }
        File tmp = null;
        try {
            tmp = File.createTempFile("ws_shot_a11y_", ".png");
            if (!a11y.takeScreenshot(tmp)) {
                return null;
            }
            if (!tmp.exists() || tmp.length() <= 0) {
                return null;
            }
            return BitmapFactory.decodeFile(tmp.getAbsolutePath());
        } catch (Exception e) {
            Logger.safeWarn(TAG, "a11y screenshot failed: " + e.getMessage());
            return null;
        } finally {
            if (tmp != null && tmp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
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

    private static int readInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (webSocket != null) {
            Logger.safeInfo(TAG, "Disconnecting WebSocket");
            webSocket.close(1000, "Client disconnecting");
            webSocket = null;
            isRegistered = false;
        }
    }

    /**
     * 是否已连接
     */
    public boolean isConnected() {
        return webSocket != null && isRegistered;
    }

    /**
     * 保存文件到应用内部存储
     */
    public boolean saveTestFile(String fileName, String content, File filesDir) {
        try {
            File testflowsDir = new File(filesDir, "testflows");
            if (!testflowsDir.exists()) {
                testflowsDir.mkdirs();
            }

            File file = new File(testflowsDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(content.getBytes("UTF-8"));
                fos.flush();
            }

            Logger.safeInfo(TAG, "File saved: " + file.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Logger.safeError(TAG, "Save file error", e);
            return false;
        }
    }

    /**
     * 删除本地文件
     */
    public boolean deleteLocalFile(String fileName, File filesDir) {
        try {
            File testflowsDir = new File(filesDir, "testflows");
            File file = new File(testflowsDir, fileName);

            if (file.exists()) {
                boolean deleted = file.delete();
                Logger.safeInfo(TAG, "Delete file " + fileName + ": " + (deleted ? "success" : "failed"));
                return deleted;
            }
            return false;
        } catch (Exception e) {
            Logger.safeError(TAG, "Delete file error", e);
            return false;
        }
    }

    /**
     * 测试服务器连接
     */
    public boolean testConnection() {
        try {
            Request request = new Request.Builder()
                    .url(config.getServerUrl() + "/api/device/status")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            Logger.safeError(TAG, "Test connection error", e);
            return false;
        }
    }

    /**
     * 更新配置并重连
     */
    public void updateConfig(WebServerConfig newConfig) {
        Logger.safeInfo(TAG, "Updating config to: " + newConfig.getWebSocketUrl());

        // 断开当前连接
        disconnect();

        // 更新配置
        instance = new WebServerClient(newConfig);
    }

    /**
     * 获取当前配置
     */
    public WebServerConfig getConfig() {
        return config;
    }

    /**
     * 上传截图到服务器
     * @param testId 测试ID
     * @param screenshotFile 截图文件
     * @return 是否上传成功
     */
    public boolean uploadScreenshot(String testId, File screenshotFile) {
        if (webSocket == null || !isRegistered) {
            Logger.safeWarn(TAG, "WebSocket not connected, cannot upload screenshot");
            return false;
        }

        try {
            // 读取文件内容
            FileInputStream fis = new FileInputStream(screenshotFile);
            byte[] bytes = new byte[(int) screenshotFile.length()];
            fis.read(bytes);
            fis.close();

            // 转换为Base64
            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);

            // 发送截图：caseId 必须用拍摄时的 testId，不能用 currentCaseId
            //（队列延迟上传时 currentCaseId 可能已切到下一用例，否则会串目录）
            String caseId = (testId != null && !testId.isEmpty()) ? testId : currentCaseId;
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "screenshot_upload");
            msg.put("testId", testId);
            msg.put("taskId", currentTaskId);
            msg.put("caseId", caseId);
            msg.put("deviceId", deviceId);
            msg.put("filename", screenshotFile.getName());
            msg.put("data", "data:" + getScreenshotMimeType(screenshotFile.getName()) + ";base64," + base64);

            String json = gson.toJson(msg);
            boolean sent = webSocket.send(json);
            if (!sent) {
                Logger.safeWarn(TAG, "WebSocket send queue full, screenshot not sent: " + screenshotFile.getName());
                return false;
            }

            Logger.safeInfo(TAG, "Screenshot uploaded: " + screenshotFile.getName() + " for test " + testId);
            return true;
        } catch (Exception e) {
            Logger.safeError(TAG, "Failed to upload screenshot: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 通过 HTTP 上传截图，不占用 WebSocket 发送队列（用于 result_final 等关键截图）
     */
    public boolean uploadScreenshotHttp(String testId, File screenshotFile) {
        if (config == null || screenshotFile == null || !screenshotFile.exists()) {
            return false;
        }

        // 与 WS 上传一致：按拍摄用例 testId 落盘，避免串到下一用例
        String caseId = (testId != null && !testId.isEmpty()) ? testId : currentCaseId;
        String mimeType = getScreenshotMimeType(screenshotFile.getName());
        RequestBody fileBody = RequestBody.create(screenshotFile, MediaType.parse(mimeType));
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("taskId", currentTaskId != null ? currentTaskId : "")
                .addFormDataPart("caseId", caseId != null ? caseId : "")
                .addFormDataPart("testId", testId != null ? testId : "")
                .addFormDataPart("deviceId", deviceId)
                .addFormDataPart("file", screenshotFile.getName(), fileBody)
                .build();

        String url = config.getServerUrl() + "/api/device/screenshot";
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = uploadHttpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                Logger.safeInfo(TAG, "Screenshot uploaded via HTTP: " + screenshotFile.getName()
                        + " caseId=" + caseId);
                return true;
            }
            String bodyText = response.body() != null ? response.body().string() : "";
            Logger.safeWarn(TAG, "HTTP screenshot upload failed (" + response.code() + "): " + bodyText);
            return false;
        } catch (Exception e) {
            Logger.safeError(TAG, "HTTP screenshot upload error: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 通过 HTTP 上传测试日志到 Web 结果页
     */
    public boolean uploadLogHttp(String testId, File logFile) {
        if (config == null || logFile == null || !logFile.exists()) {
            return false;
        }

        String caseId = (testId != null && !testId.isEmpty()) ? testId : currentCaseId;
        RequestBody fileBody = RequestBody.create(logFile, MediaType.parse("text/plain"));
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("taskId", currentTaskId != null ? currentTaskId : "")
                .addFormDataPart("caseId", caseId)
                .addFormDataPart("testId", testId)
                .addFormDataPart("deviceId", deviceId)
                .addFormDataPart("file", logFile.getName(), fileBody)
                .build();

        String url = config.getServerUrl() + "/api/device/log";
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = uploadHttpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                Logger.safeInfo(TAG, "Log uploaded via HTTP: " + logFile.getName());
                return true;
            }
            String bodyText = response.body() != null ? response.body().string() : "";
            Logger.safeWarn(TAG, "HTTP log upload failed (" + response.code() + "): " + bodyText);
            return false;
        } catch (Exception e) {
            Logger.safeError(TAG, "HTTP log upload error: " + e.getMessage(), e);
            return false;
        }
    }

    private static String getScreenshotMimeType(String filename) {
        if (filename == null) {
            return "image/png";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "image/png";
    }

    /**
     * 上报用例执行步骤进度（供 Web 进度条按 step/total 同步）。
     */
    public void sendStepProgress(int stepIndex, int stepTotal, String stepId, String action) {
        if (webSocket == null || !isRegistered || stepIndex <= 0 || stepTotal <= 0) {
            return;
        }

        Map<String, Object> msg = new HashMap<>();
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

        webSocket.send(gson.toJson(msg));
    }

    /**
     * 发送测试完成通知
     * @param testId 测试ID
     * @param success 是否成功
     * @param message 结果消息
     */
    public void sendTestCompleted(String testId, boolean success, String message) {
        if (webSocket == null || !isRegistered) {
            return;
        }

        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "test_completed");
        msg.put("testId", testId);
        msg.put("taskId", currentTaskId);
        msg.put("deviceId", deviceId);
        msg.put("success", success);
        msg.put("message", message);

        String json = gson.toJson(msg);
        webSocket.send(json);

        Logger.safeInfo(TAG, "Test completed notification sent: " + testId);
    }

    /**
     * 上报 DeviceAPP 转发的 AI 回复或任务完成事件。
     * 必须异步：Device 回复经 BroadcastReceiver 进入主线程，禁止在此同步 HTTP。
     */
    public void sendCaseReply(TestReplyEvent event) {
        if (event == null) {
            return;
        }
        // 完成信号必须上报；GUI 中途 draft / 文本也要落 replies.json 供网页展示，
        // 不能只收 final（否则接管期间的 AISF 消息在页面上看不到）。
        String text = event.getReplyText();
        boolean hasText = text != null && !text.trim().isEmpty();
        if (!event.signalsWaitComplete() && !hasText) {
            return;
        }
        caseReplyExecutor.execute(() -> sendCaseReplySync(event));
    }

    /** 上报 GUI 单步时延（端侧/云侧）到 9526。 */
    public void sendGuiStepLatency(String taskId, String caseId, int round, String jsonPayload) {
        if (jsonPayload == null || jsonPayload.isEmpty()) {
            return;
        }
        caseReplyExecutor.execute(() -> {
            try {
                String tid = (taskId != null && !taskId.isEmpty()) ? taskId : currentTaskId;
                String cid = (caseId != null && !caseId.isEmpty()) ? caseId : currentCaseId;
                if (tid == null || tid.isEmpty() || cid == null || cid.isEmpty()) {
                    Logger.safeWarn(TAG, "gui_step_latency skip: missing task/case");
                    return;
                }
                Map<String, Object> msg = new HashMap<>();
                msg.put("type", "gui_step_latency");
                msg.put("taskId", tid);
                msg.put("caseId", cid);
                msg.put("testId", cid);
                msg.put("deviceId", deviceId);
                msg.put("round", round);
                msg.put("payload", jsonPayload);
                String body = gson.toJson(msg);
                if (webSocket != null && isRegistered) {
                    webSocket.send(body);
                }
                if (config == null) {
                    return;
                }
                String url = config.getServerUrl() + "/api/device/gui_step_latency";
                Request request = new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body))
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    Logger.safeInfo(TAG, "gui_step_latency HTTP " + response.code()
                            + " task=" + tid + " case=" + cid + " round=" + round);
                }
            } catch (Exception e) {
                Logger.safeWarn(TAG, "gui_step_latency failed: " + e.getMessage());
            }
        });
    }

    /**
     * 上报轮次开始（收到 GUI_TASK 时该轮即为 GUI 轮）。
     * 服务端据此先建立截图归轮时间窗，避免长轮进行中截图被归到上一轮。
     */
    public void sendRoundStart(String taskId, String caseId, int round, long sentAtMs, String outputType) {
        if (round <= 0) {
            return;
        }
        final int roundNo = round;
        final long sentMs = sentAtMs > 0 ? sentAtMs : System.currentTimeMillis();
        final String type = (outputType != null && !outputType.isEmpty()) ? outputType : "GUI_TASK";
        caseReplyExecutor.execute(() -> {
            try {
                String tid = (taskId != null && !taskId.isEmpty()) ? taskId : currentTaskId;
                String cid = (caseId != null && !caseId.isEmpty()) ? caseId : currentCaseId;
                if (tid == null || tid.isEmpty() || cid == null || cid.isEmpty()) {
                    Logger.safeWarn(TAG, "round_start skip: missing task/case");
                    return;
                }
                Map<String, Object> msg = new HashMap<>();
                msg.put("type", "round_start");
                msg.put("taskId", tid);
                msg.put("caseId", cid);
                msg.put("testId", cid);
                msg.put("deviceId", deviceId);
                msg.put("round", roundNo);
                msg.put("sentAtMs", sentMs);
                msg.put("outputType", type);
                String body = gson.toJson(msg);
                if (webSocket != null && isRegistered) {
                    webSocket.send(body);
                }
                if (config == null) {
                    return;
                }
                String url = config.getServerUrl() + "/api/device/round_start";
                Request request = new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body))
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    Logger.safeInfo(TAG, "round_start HTTP " + response.code()
                            + " task=" + tid + " case=" + cid + " round=" + roundNo);
                }
            } catch (Exception e) {
                Logger.safeWarn(TAG, "round_start failed: " + e.getMessage());
            }
        });
    }

    private void sendCaseReplySync(TestReplyEvent event) {
        boolean httpOk = postCaseReplyHttp(event);
        if (webSocket != null && isRegistered) {
            dispatchCaseReplyWs(event);
        }
        if (httpOk) {
            return;
        }
        if (webSocket == null || !isRegistered) {
            synchronized (pendingCaseReplies) {
                if (pendingCaseReplies.size() >= PENDING_CASE_REPLY_LIMIT) {
                    pendingCaseReplies.remove(0);
                }
                pendingCaseReplies.add(event);
            }
            Logger.safeWarn(TAG, "WebServer 未连接且 HTTP 上报失败，case_reply 已缓存 round="
                    + event.getRound() + " pending=" + pendingCaseReplies.size());
        }
    }

    private void flushPendingCaseReplies() {
        List<TestReplyEvent> queued;
        synchronized (pendingCaseReplies) {
            if (pendingCaseReplies.isEmpty()) {
                return;
            }
            queued = new ArrayList<>(pendingCaseReplies);
            pendingCaseReplies.clear();
        }
        Logger.safeInfo(TAG, "补发缓存 case_reply: " + queued.size());
        for (TestReplyEvent event : queued) {
            // 已在后台补发路径或主线程重连回调时，统一走异步队列，避免重复入队死锁
            caseReplyExecutor.execute(() -> sendCaseReplySync(event));
        }
    }

    private Map<String, Object> buildCaseReplyPayload(TestReplyEvent event) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "case_reply");
        msg.put("taskId", currentTaskId);
        msg.put("caseId", currentCaseId);
        msg.put("testId", currentCaseId);
        msg.put("deviceId", deviceId);
        msg.put("round", event.getRound());
        msg.put("userMessage", event.getUserMessage() != null ? event.getUserMessage() : "");
        msg.put("replyText", event.getReplyText() != null ? event.getReplyText() : "");
        msg.put("outputType", event.getOutputType() != null ? event.getOutputType() : "TEXT");
        msg.put("isFinal", event.isFinalReply());
        msg.put("taskCompleted", event.isTaskCompleted());
        msg.put("sessionId", event.getSessionId() != null ? event.getSessionId() : "");
        msg.put("sentAtMs", event.getSentAtMs());
        msg.put("latencyMs", event.getLatencyMs());
        msg.put("firstTokenLatencyMs", event.getFirstTokenLatencyMs());
        msg.put("segments", event.getSegments() != null
                ? new ArrayList<>(event.getSegments())
                : new ArrayList<String>());
        msg.put("capturedAt", event.getTimestampMs() / 1000.0);
        return msg;
    }

    private boolean postCaseReplyHttp(TestReplyEvent event) {
        if (config == null || event == null) {
            return false;
        }
        String json = gson.toJson(buildCaseReplyPayload(event));
        MediaType jsonType = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(json, jsonType);
        String url = config.getServerUrl() + "/api/device/case_reply";
        Request request = new Request.Builder().url(url).post(body).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                Logger.safeInfo(TAG, "Case reply uploaded via HTTP: round=" + event.getRound()
                        + ", latencyMs=" + event.getLatencyMs()
                        + ", firstTokenLatencyMs=" + event.getFirstTokenLatencyMs());
                return true;
            }
            String bodyText = response.body() != null ? response.body().string() : "";
            Logger.safeWarn(TAG, "HTTP case_reply failed (" + response.code() + "): " + bodyText);
            return false;
        } catch (Exception e) {
            Logger.safeError(TAG, "HTTP case_reply error: " + e.getMessage(), e);
            return false;
        }
    }

    private void dispatchCaseReplyWs(TestReplyEvent event) {
        if (webSocket == null || !isRegistered || event == null) {
            return;
        }
        webSocket.send(gson.toJson(buildCaseReplyPayload(event)));
        Logger.safeInfo(TAG, "Case reply sent: round=" + event.getRound()
                + ", latencyMs=" + event.getLatencyMs()
                + ", firstTokenLatencyMs=" + event.getFirstTokenLatencyMs()
                + ", taskCompleted=" + event.isTaskCompleted());
    }

    /**
     * 上报 DeviceAPP 转发的 AI 回复（兼容旧调用）。
     */
    public void sendCaseReply(int round, String userMessage, String replyText,
                              String outputType, boolean isFinal, String sessionId) {
        if (replyText == null || replyText.isEmpty()) {
            return;
        }
        TestReplyEvent event = new TestReplyEvent.Builder()
                .round(round)
                .userMessage(userMessage)
                .replyText(replyText)
                .outputType(outputType)
                .finalReply(isFinal)
                .sessionId(sessionId)
                .build();
        sendCaseReply(event);
    }

    /**
     * 上传测试结果的所有截图
     * @param testId 测试ID
     * @param screenshotDir 截图目录
     */
    public void uploadTestScreenshots(String testId, File screenshotDir) {
        if (screenshotDir == null || !screenshotDir.exists()) {
            Logger.safeWarn(TAG, "Screenshot directory does not exist");
            return;
        }

        File[] files = screenshotDir.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".jpg"));

        if (files != null && files.length > 0) {
            Logger.safeInfo(TAG, "Uploading " + files.length + " screenshots for test " + testId);
            for (File file : files) {
                uploadScreenshot(testId, file);
                // 短暂延迟避免发送过快
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}