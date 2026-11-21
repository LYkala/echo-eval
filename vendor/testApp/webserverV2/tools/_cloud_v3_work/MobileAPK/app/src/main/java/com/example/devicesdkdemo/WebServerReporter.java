package com.example.devicesdkdemo;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AISF 回复直连 webserver（POST /api/device/case_reply + 可选 WS）。
 * 任务上下文由 WS execute_test / WebServerCaseRunner 写入 SharedPreferences。
 */
public final class WebServerReporter {
    public static final String TAG = "WebServerReporter";

    public static final String KEY_WEBSERVER_URL = "webserver_url";
    public static final String KEY_WEBSERVER_ENABLED = "webserver_report_enabled";
    public static final String KEY_TASK_ID = "webserver_task_id";
    public static final String KEY_CASE_ID = "webserver_case_id";
    public static final String KEY_DEVICE_ID = "webserver_device_id";

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static volatile Context appContext;

    private WebServerReporter() {
    }

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        Log.i(TAG, "initialized");
    }

    public static void reportFinalReply(
            String sessionId,
            int round,
            String userMessage,
            String replyText,
            String outputType,
            long latencyMs,
            long firstTokenLatencyMs,
            long sentAtMs,
            boolean taskCompleted
    ) {
        Context ctx = appContext;
        if (ctx == null) {
            return;
        }
        SharedPreferences prefs = ctx.getSharedPreferences(
                ConfigActivity.PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_WEBSERVER_ENABLED, false)) {
            return;
        }
        String base = prefs.getString(KEY_WEBSERVER_URL, "");
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
        final String url = base + "/api/device/case_reply";
        String taskId = prefs.getString(KEY_TASK_ID, "");
        String caseId = prefs.getString(KEY_CASE_ID, "");
        String deviceId = prefs.getString(KEY_DEVICE_ID, "");
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = Settings.Secure.getString(
                    ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        }
        if ((taskId == null || taskId.isEmpty())
                && (caseId == null || caseId.isEmpty())) {
            Log.w(TAG, "skip report: no taskId/caseId");
        }
        final String fTask = taskId != null ? taskId : "";
        final String fCase = caseId != null ? caseId : "";
        final String fDevice = deviceId != null ? deviceId : "";
        final String fText = replyText != null ? replyText : "";
        final String fUser = userMessage != null ? userMessage : "";
        final String fType = outputType != null ? outputType : "TEXT";
        final String fSid = sessionId != null ? sessionId : "";

        EXEC.execute(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject body = new JSONObject();
                if (!fTask.isEmpty()) {
                    body.put("taskId", fTask);
                }
                if (!fCase.isEmpty()) {
                    body.put("caseId", fCase);
                }
                body.put("deviceId", fDevice);
                body.put("replyText", fText);
                body.put("userMessage", fUser);
                body.put("outputType", fType);
                body.put("round", round);
                body.put("sessionId", fSid);
                body.put("isFinal", true);
                body.put("taskCompleted", taskCompleted);
                if (latencyMs >= 0) {
                    body.put("latencyMs", latencyMs);
                }
                if (firstTokenLatencyMs >= 0) {
                    body.put("firstTokenLatencyMs", firstTokenLatencyMs);
                }
                if (sentAtMs > 0) {
                    body.put("sentAtMs", sentAtMs);
                }
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
                int code = conn.getResponseCode();
                Log.i(TAG, "case_reply POST code=" + code + " task=" + fTask
                        + " case=" + fCase + " round=" + round
                        + " textLen=" + fText.length());
                WebServerWsClient wsClient = WebServerWsClient.getInstanceIfAny();
                if (wsClient != null) {
                    wsClient.sendCaseReply(
                            fTask, fCase, round, fUser, fText, fType,
                            latencyMs, firstTokenLatencyMs, sentAtMs, taskCompleted, fSid);
                }
            } catch (Exception e) {
                Log.w(TAG, "case_reply POST failed: " + e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }
}
