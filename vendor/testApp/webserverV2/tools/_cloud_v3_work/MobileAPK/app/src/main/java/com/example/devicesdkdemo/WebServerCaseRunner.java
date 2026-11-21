package com.example.devicesdkdemo;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 在设备端执行 webserver 下发的 JSON 用例（对齐 local_adb_runner/json_step_runner.py）。
 */
public final class WebServerCaseRunner {
    public static final String TAG = "WebServerCaseRunner";

    private static final Set<String> PRE_STEP_IDS = new HashSet<>();
    static {
        PRE_STEP_IDS.add("wait-home");
        PRE_STEP_IDS.add("launch");
        PRE_STEP_IDS.add("click-session-manage");
        PRE_STEP_IDS.add("click-new-session");
        PRE_STEP_IDS.add("wait-new-session");
        // legacy login/QA entry (stripped by server, keep for old payloads)
        PRE_STEP_IDS.add("dismiss-session-dialog");
        PRE_STEP_IDS.add("scroll-to-enter-test");
        PRE_STEP_IDS.add("wait-test-home");
        PRE_STEP_IDS.add("dismiss-need-login");
        PRE_STEP_IDS.add("click-login");
        PRE_STEP_IDS.add("wait-login");
        PRE_STEP_IDS.add("dismiss-after-login");
        PRE_STEP_IDS.add("click-qa");
        PRE_STEP_IDS.add("wait-qa-page");
    }

    public interface Callback {
        void onStepProgress(int stepIndex, int stepTotal, String stepId, String action);

        void onCaseFinished(String caseId, boolean success, String message);
    }

    private volatile boolean stopRequested;
    private volatile Thread runningThread;
    /** 供 ReplyMetrics 等待循环感知 stop_test。 */
    private static volatile WebServerCaseRunner activeRunner;
    /** Demo 停止原因，上报 test_completed.message（如「人工接管」）。 */
    private volatile String stopReason = null;

    public void requestStop() {
        requestStop(null);
    }

    public void requestStop(String reason) {
        if (reason != null && !reason.trim().isEmpty()) {
            // 保留更具体的「人工接管」，避免后续「用户停止」覆盖
            if (stopReason == null || stopReason.isEmpty()
                    || "用户停止".equals(stopReason)) {
                stopReason = reason.trim();
            }
        }
        stopRequested = true;
        Thread t = runningThread;
        if (t != null) {
            t.interrupt();
        }
        // 停止前尽量把已收到的 AISF 文本/GUI 信号上报，避免结果页空回复
        try {
            ReplyMetrics.abortPendingGui(stopReason != null ? stopReason : "stop");
        } catch (Throwable ignored) {
            try {
                ReplyMetrics.flushPendingReplies("stop");
            } catch (Throwable ignored2) {
            }
        }
        // 唤醒 ReplyMetrics.wait*
        ReplyMetrics.wakeWaiters();
    }

    public boolean isRunning() {
        return runningThread != null && runningThread.isAlive();
    }

    public static boolean isStopRequested() {
        WebServerCaseRunner r = activeRunner;
        return r != null && r.stopRequested;
    }

    /** Demo 点「停止任务」时打断正在跑的 webserver 用例。 */
    public static void abortActiveCaseFromDemoStop() {
        abortActiveCaseFromDemoStop(null);
    }

    public static void abortActiveCaseFromDemoStop(String reason) {
        WebServerCaseRunner r = activeRunner;
        if (r != null && r.isRunning()) {
            Log.i(TAG, "abortActiveCaseFromDemoStop reason=" + reason);
            r.requestStop(reason);
        } else {
            try {
                ReplyMetrics.abortPendingGui(
                        reason != null ? reason : "demo_stop");
            } catch (Throwable ignored) {
            }
        }
    }

    public void runCase(
            Context context,
            String filename,
            String content,
            String taskId,
            String caseId,
            String deviceId,
            String baseUrl,
            Callback callback
    ) {
        if (isRunning()) {
            Log.w(TAG, "case runner busy, reject " + filename);
            if (callback != null) {
                callback.onCaseFinished(caseId, false, "上一个用例仍在执行");
            }
            return;
        }
        stopRequested = false;
        stopReason = null;
        activeRunner = this;
        runningThread = new Thread(() -> {
            boolean ok = false;
            String message = "completed";
            try {
                setCaseContext(context, taskId, caseId, deviceId, baseUrl);
                JSONObject tc = new JSONObject(content);
                ok = runSteps(context, tc, callback);
                if (stopRequested) {
                    ok = false;
                    message = (stopReason != null && !stopReason.isEmpty())
                            ? stopReason : "用户停止";
                }
            } catch (Exception e) {
                Log.e(TAG, "runCase failed: " + e.getMessage(), e);
                message = e.getMessage() != null ? e.getMessage() : "执行异常";
                ok = false;
                if (stopRequested) {
                    message = (stopReason != null && !stopReason.isEmpty())
                            ? stopReason : "用户停止";
                }
            } finally {
                try {
                    // 用户停止：跳过最长 180s 的 GUI 等待，尽快收尾上报
                    if (!stopRequested && ReplyMetrics.hasPendingGui()) {
                        Log.i(TAG, "before handoff: wait GUI taskCompleted");
                        ReplyMetrics.waitForGuiTaskCompleted(180_000L);
                    } else if (stopRequested) {
                        Log.i(TAG, "before handoff: skip GUI wait (stopped)");
                    }
                    WsUiHelper.returnAfterCase(context);
                } catch (Throwable t) {
                    Log.w(TAG, "returnAfterCase failed: " + t.getMessage());
                }
                clearCaseContext(context);
                if (activeRunner == this) {
                    activeRunner = null;
                }
                runningThread = null;
                if (callback != null) {
                    callback.onCaseFinished(caseId, ok, message);
                }
            }
        }, "ws-case-runner");
        runningThread.start();
    }

    private boolean runSteps(Context ctx, JSONObject testcase, Callback callback) throws Exception {
        JSONArray steps = testcase.optJSONArray("steps");
        if (steps == null || steps.length() == 0) {
            return true;
        }
        int total = steps.length();
        int turnIdx = 0;
        String pendingText = "";

        for (int i = 0; i < total; i++) {
            checkStop();
            JSONObject step = steps.getJSONObject(i);
            String action = step.optString("action", "").trim();
            String sid = step.optString("stepId", action);
            if (callback != null) {
                callback.onStepProgress(i + 1, total, sid, action);
            }
            Log.i(TAG, "step " + sid + ": " + action);

            if ("launchApp".equals(action)) {
                WsUiHelper.launchDemo(ctx);
                continue;
            }
            if ("scrollUntilClick".equals(action)) {
                scrollUntilClick(step);
                continue;
            }
            if ("click".equals(action)) {
                // 新会话前置：弹窗在独立 a11y window，且需等弹窗出现
                if ("click-session-manage".equals(sid)) {
                    if (!WsUiHelper.openNewQaSession()) {
                        throw new RuntimeException("打开会话并点击「新增会话」失败");
                    }
                    // 已在 openNewQaSession 内完成新增；跳过下一步 click-new-session
                    sleep(300);
                    continue;
                }
                if ("click-new-session".equals(sid)) {
                    // 上一步 click-session-manage 已在 openNewQaSession 内点完「新增会话」
                    // 此处仅兜底：弹窗若还在再点一次
                    if (WsUiHelper.tapById("android:id/button1", 2)
                            || WsUiHelper.tapByText("+ 新增会话", 2)) {
                        Log.i(TAG, "click-new-session: tapped leftover dialog button");
                        sleep(800);
                    } else {
                        Log.i(TAG, "click-new-session: skip (already created in session-manage)");
                    }
                    continue;
                }
                clickTarget(step, !isSoftClick(sid));
                if (isSoftClick(sid)) {
                    WsUiHelper.dismissDialogs();
                }
                sleep(200);
                continue;
            }
            if ("input".equals(action)) {
                JSONObject params = step.optJSONObject("params");
                pendingText = params != null ? params.optString("text", "").trim() : "";
                inputText(step);
                continue;
            }
            if ("wait".equals(action)) {
                JSONObject params = step.optJSONObject("params");
                long durationMs = params != null ? params.optLong("duration", 0L) : 0L;
                if (isWaitResponse(sid)) {
                    turnIdx += 1;
                    double waitSec = Math.max(0.0, durationMs / 1000.0);
                    String clarify = params != null
                            ? params.optString("clarifyMessage", "").trim() : "";
                    int afterRound = ReplyMetrics.getLastReplyRound();
                    double phase1 = waitSec;
                    if (!clarify.isEmpty()) {
                        phase1 = Math.min(waitSec, 120.0);
                    }
                    Log.i(TAG, "wait-response up to " + waitSec + "s round=" + turnIdx);
                    checkStop();
                    boolean got = ReplyMetrics.waitForReplyAfterRound(
                            afterRound, (long) (phase1 * 1000));
                    checkStop();
                    if (!clarify.isEmpty() && got) {
                        Log.i(TAG, "auto clarify: " + clarify.substring(0, Math.min(60, clarify.length())));
                        WsUiHelper.sendClarify(clarify);
                        afterRound = ReplyMetrics.getLastReplyRound();
                        double remain = Math.max(30.0, waitSec - phase1);
                        checkStop();
                        ReplyMetrics.waitForReplyAfterRound(
                                afterRound, (long) (remain * 1000));
                        checkStop();
                    } else if (!got) {
                        ReplyMetrics.waitForReplyAfterRound(
                                afterRound, (long) (waitSec * 1000));
                        checkStop();
                    }
                    // GUI：必须等 TASK_STATUS 已完成，再结束本轮 wait-response
                    long guiWaitMs = Math.max(30_000L, (long) (waitSec * 1000L));
                    if (ReplyMetrics.hasPendingGui()) {
                        checkStop();
                        Log.i(TAG, "wait-response: waiting GUI taskCompleted up to "
                                + guiWaitMs + "ms");
                        boolean guiDone = ReplyMetrics.waitForGuiTaskCompleted(guiWaitMs);
                        if (!guiDone) {
                            ReplyMetrics.flushPendingReplies("gui_timeout");
                        }
                        checkStop();
                    }
                    pendingText = "";
                } else {
                    sleep((int) durationMs);
                }
                continue;
            }
            if ("screenshot".equals(action)) {
                // WS 路径截图由服务端 case WS shot 负责，端侧跳过
                continue;
            }
            if ("press_back".equals(action)) {
                WsUiHelper.pressBack();
                continue;
            }
            Log.w(TAG, "skip unsupported action=" + action + " step=" + sid);
        }
        return true;
    }

    private static void clickTarget(JSONObject step, boolean required) {
        JSONObject target = step.optJSONObject("target");
        String strategy = target != null ? target.optString("strategy", "id").toLowerCase() : "id";
        String value = target != null ? target.optString("value", "") : "";
        if (value.isEmpty()) {
            if (required) {
                throw new RuntimeException("步骤缺 target: " + step.optString("stepId"));
            }
            return;
        }
        boolean ok;
        if ("id".equals(strategy)) {
            ok = WsUiHelper.tapById(value, 3);
        } else if ("text".equals(strategy)) {
            ok = WsUiHelper.tapByText(value, 2);
        } else {
            throw new RuntimeException("不支持的 click strategy=" + strategy);
        }
        if (required && !ok) {
            throw new RuntimeException("点击失败 " + strategy + "=" + value);
        }
    }

    private static void scrollUntilClick(JSONObject step) {
        JSONObject target = step.optJSONObject("target");
        String strategy = target != null ? target.optString("strategy", "id").toLowerCase() : "id";
        String value = target != null ? target.optString("value", "") : "";
        JSONObject params = step.optJSONObject("params");
        int maxScrolls = params != null ? params.optInt("maxScrolls", 6) : 6;
        int pauseMs = params != null ? params.optInt("duration", 600) : 600;
        if (!"id".equals(strategy) || value.isEmpty()) {
            throw new RuntimeException("scrollUntilClick 仅支持 id");
        }
        for (int i = 0; i <= maxScrolls; i++) {
            if (WsUiHelper.tapById(value, 1)) {
                return;
            }
            if (i < maxScrolls) {
                WsUiHelper.swipeUp();
                sleep(Math.max(150, pauseMs));
            }
        }
        throw new RuntimeException("scrollUntilClick 未找到 " + value);
    }

    private static void inputText(JSONObject step) {
        JSONObject params = step.optJSONObject("params");
        String text = params != null ? params.optString("text", "").trim() : "";
        if (text.isEmpty()) {
            return;
        }
        JSONObject target = step.optJSONObject("target");
        String strategy = target != null ? target.optString("strategy", "id").toLowerCase() : "id";
        String value = target != null ? target.optString("value", "") : "";
        if ("id".equals(strategy) && !value.isEmpty()) {
            WsUiHelper.inputToTarget(value, text);
        } else {
            WsUiHelper.inputToTarget("", text);
        }
    }

    private static boolean isSoftClick(String sid) {
        return sid != null && sid.startsWith("dismiss-");
    }

    private static boolean isWaitResponse(String sid) {
        return sid != null && sid.startsWith("wait-response");
    }

    private void checkStop() {
        if (stopRequested || Thread.currentThread().isInterrupted()) {
            throw new RuntimeException("用户停止");
        }
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("用户停止");
        }
    }

    public static boolean saveTestFile(Context ctx, String fileName, String content) {
        try {
            File dir = new File(ctx.getFilesDir(), "testflows");
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            File file = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(content.getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            Log.i(TAG, "saved testcase " + file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.w(TAG, "saveTestFile failed: " + e.getMessage());
            return false;
        }
    }

    public static boolean deleteTestFile(Context ctx, String fileName) {
        try {
            File file = new File(new File(ctx.getFilesDir(), "testflows"), fileName);
            if (file.exists()) {
                return file.delete();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void setCaseContext(
            Context ctx, String taskId, String caseId, String deviceId, String baseUrl) {
        SharedPreferences prefs = ctx.getSharedPreferences(
                ConfigActivity.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = prefs.edit();
        if (taskId != null) {
            ed.putString(WebServerReporter.KEY_TASK_ID, taskId);
        }
        if (caseId != null) {
            ed.putString(WebServerReporter.KEY_CASE_ID, caseId);
        }
        if (deviceId != null && !deviceId.isEmpty()) {
            ed.putString(WebServerReporter.KEY_DEVICE_ID, deviceId);
        }
        if (baseUrl != null && !baseUrl.isEmpty()) {
            ed.putString(WebServerReporter.KEY_WEBSERVER_URL, baseUrl);
            ed.putBoolean(WebServerReporter.KEY_WEBSERVER_ENABLED, true);
        }
        ed.apply();
        ReplyMetrics.resetForNewCase();
        Log.i(TAG, "case context task=" + taskId + " case=" + caseId + " device=" + deviceId);
    }

    private static void clearCaseContext(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(
                ConfigActivity.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(WebServerReporter.KEY_TASK_ID)
                .remove(WebServerReporter.KEY_CASE_ID)
                .apply();
    }
}
