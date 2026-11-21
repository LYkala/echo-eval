package com.example.testapp.util;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 步骤截图专用诊断日志：每步是否尝试、screencap/a11y 成败、是否保存/上传。
 * 测试结束自动导出并上传结果页，用于排查「后面不截图」。
 */
public final class ScreenshotDiagLogger {

    private static final String TAG = "ScreenshotDiag";
    private static final Object LOCK = new Object();
    private static final StringBuilder sessionBuffer = new StringBuilder();
    private static String sessionTestId;
    private static int capturedCount;
    private static int failedCount;
    private static int uploadedCount;
    private static int lastStepIndex;
    private static String lastStepId;
    private static String lastStepAction;
    private static boolean lastStepCrossApp;
    private static int mediaDialogCaptureWarnings;
    private static long configuredTimeoutMs;
    private static long sessionStartUptimeMs;
    private static long finishingPhaseStartUptimeMs;

    private ScreenshotDiagLogger() {
    }

    public static void startSession(String testId) {
        synchronized (LOCK) {
            sessionTestId = testId;
            capturedCount = 0;
            failedCount = 0;
            uploadedCount = 0;
            lastStepIndex = 0;
            lastStepId = "";
            lastStepAction = "";
            lastStepCrossApp = false;
            mediaDialogCaptureWarnings = 0;
            configuredTimeoutMs = 0;
            sessionStartUptimeMs = android.os.SystemClock.uptimeMillis();
            finishingPhaseStartUptimeMs = 0;
            sessionBuffer.setLength(0);
            sessionBuffer.append("=== 截图诊断日志 ===\n");
            sessionBuffer.append("testId: ").append(testId).append('\n');
            sessionBuffer.append("开始: ").append(now()).append("\n");
            sessionBuffer.append("说明: 测试结束后自动上传；结果页「查看日志」优先展示本文件\n");
            sessionBuffer.append("收尾阶段关键字: [finishing] [exit_flow] [result_final] [test_completed]\n\n");
        }
    }

    public static void configuredTimeout(long timeoutMs, long bufferMs, long finalTimeoutMs) {
        synchronized (LOCK) {
            configuredTimeoutMs = finalTimeoutMs;
        }
        info(String.format("[超时配置] json=%dms buffer=%dms final=%dms (%.1fs)",
                timeoutMs, bufferMs, finalTimeoutMs, finalTimeoutMs / 1000.0));
    }

    public static void phase(String phase, String detail) {
        info("[" + phase + "] " + detail);
    }

    /** 全部步骤执行完，进入收尾（result_final / test_completed 尚未完成） */
    public static void beginFinishingPhase(String detail) {
        synchronized (LOCK) {
            finishingPhaseStartUptimeMs = android.os.SystemClock.uptimeMillis();
        }
        phase("finishing", detail);
    }

    /** 退出流程各阶段（回 testApp、最终截图、停服务） */
    public static void exitFlow(String step, String detail) {
        phase("exit_flow", step + " | " + detail);
    }

    /** result_final 截图/上传事件 */
    public static void resultFinalEvent(String event, String detail) {
        phase("result_final", event + " | " + detail);
    }

    /** 通知服务器 test_completed */
    public static void testCompletedNotify(String source, boolean success, String message) {
        long sinceFinishingMs = -1;
        synchronized (LOCK) {
            if (finishingPhaseStartUptimeMs > 0) {
                sinceFinishingMs = android.os.SystemClock.uptimeMillis() - finishingPhaseStartUptimeMs;
            }
        }
        phase("test_completed", String.format(
                "source=%s success=%s sinceFinishingMs=%d msg=%s",
                source != null ? source : "unknown",
                success,
                sinceFinishingMs,
                message != null ? message : ""));
    }

    public static void completionFallbackScheduled(long delayMs) {
        phase("completion_fallback", "scheduled delayMs=" + delayMs);
    }

    public static void stepBegin(int stepIndex, int totalSteps, String action, String stepId,
            boolean crossAppPolling) {
        synchronized (LOCK) {
            lastStepIndex = stepIndex;
            lastStepId = stepId != null ? stepId : "";
            lastStepAction = action != null ? action : "";
            lastStepCrossApp = crossAppPolling;
        }
        info(String.format("[步骤] idx=%d/%d action=%s stepId=%s crossApp=%s",
                stepIndex, totalSteps, action, stepId != null ? stepId : "",
                crossAppPolling));
    }

    public static void scheduleCapture(String stepName, long waitMs, long screenshotDelayMs) {
        long fireInMs = waitMs + screenshotDelayMs;
        long fireAtUptime = android.os.SystemClock.uptimeMillis() + fireInMs;
        info(String.format("[调度截图] step=%s wait=%dms shotDelay=%dms 预计+%dms后触发 uptimeAt=%d",
                stepName, waitMs, screenshotDelayMs, fireInMs, fireAtUptime));
    }

    public static void mediaDialogAtCapture(String stepName, String phase, boolean visible,
            boolean dismissed, String detail) {
        if (visible && "after_prep".equals(phase)) {
            synchronized (LOCK) {
                mediaDialogCaptureWarnings++;
            }
        }
        String level = visible && "after_prep".equals(phase) ? "W" : "I";
        String line = String.format("[录屏弹窗] step=%s phase=%s visible=%s dismissed=%s %s",
                stepName, phase, visible, dismissed, detail != null ? detail : "");
        if ("W".equals(level)) {
            warn(line);
        } else {
            info(line);
        }
    }

    public static void capture(String stepName, String method, boolean success, long bytes, String extra) {
        synchronized (LOCK) {
            if (success) {
                capturedCount++;
            } else {
                failedCount++;
            }
        }
        info(String.format("[截图] step=%s method=%s success=%s size=%d %s",
                stepName, method, success, bytes, extra != null ? extra : ""));
    }

    public static void save(String stepName, boolean queued, String fileName, long bytes) {
        info(String.format("[保存] step=%s queued=%s file=%s size=%d",
                stepName, queued, fileName, bytes));
    }

    public static void upload(String fileName, boolean success, String via, String detail) {
        if (success) {
            synchronized (LOCK) {
                uploadedCount++;
            }
        }
        info(String.format("[上传] file=%s success=%s via=%s %s",
                fileName, success, via != null ? via : "", detail != null ? detail : ""));
    }

    public static void summaryAndReset(String testId, String extra) {
        int captured;
        int failed;
        int uploaded;
        synchronized (LOCK) {
            captured = capturedCount;
            failed = failedCount;
            uploaded = uploadedCount;
        }
        summary(testId, captured, failed, uploaded, extra);
    }

    public static void dialog(String action, boolean mediaDismissed, boolean relaunchApp) {
        info(String.format("[弹窗] action=%s mediaDismissed=%s relaunchApp=%s",
                action, mediaDismissed, relaunchApp));
    }

    /** 截图前记录当前屏幕窗口状态（纯诊断，不参与截图逻辑） */
    public static void screenState(String stepName, String phase, String detail) {
        info(String.format("[屏前] step=%s phase=%s %s",
                stepName, phase, detail != null ? detail : ""));
    }

    public static void warn(String message) {
        Log.w(TAG, message);
        appendSession("W", message);
        Logger.appendFileLog(Logger.WARN, TAG, message);
    }

    public static void error(String message, Throwable e) {
        Log.e(TAG, message, e);
        appendSession("E", message + (e != null ? "\n" + Log.getStackTraceString(e) : ""));
        Logger.appendFileLog(Logger.ERROR, TAG, message);
    }

    public static void summary(String testId, int captured, int failed, int uploaded, String extra) {
        long elapsedMs = android.os.SystemClock.uptimeMillis() - sessionStartUptimeMs;
        int stepIdx;
        String stepId;
        String stepAction;
        boolean crossApp;
        int mediaWarnings;
        long timeoutMs;
        synchronized (LOCK) {
            stepIdx = lastStepIndex;
            stepId = lastStepId;
            stepAction = lastStepAction;
            crossApp = lastStepCrossApp;
            mediaWarnings = mediaDialogCaptureWarnings;
            timeoutMs = configuredTimeoutMs;
        }
        String analysis = buildAnalysis(testId, captured, failed, elapsedMs, timeoutMs,
                stepIdx, stepId, stepAction, crossApp, mediaWarnings, extra);

        String line = "======== 截图诊断摘要 ========\n"
                + "testId=" + testId
                + " captured=" + captured
                + " failed=" + failed
                + " uploaded=" + uploaded
                + " elapsedMs=" + elapsedMs
                + " lastStep=" + stepIdx
                + " (" + stepId + ")";
        Log.i(TAG, line);
        if (extra != null && !extra.isEmpty()) {
            Log.i(TAG, extra);
        }
        appendSession("I", line);
        if (extra != null && !extra.isEmpty()) {
            for (String part : extra.split("\n")) {
                appendSession("I", part);
            }
        }
        for (String part : analysis.split("\n")) {
            appendSession(part.startsWith("【") ? "W" : "I", part);
        }
        appendSession("I", "结果页可点「查看日志」下载 screenshot_diag_*.log");
        appendSession("I", "================================");
    }

    private static String buildAnalysis(String testId, int captured, int failed, long elapsedMs,
            long timeoutMs, int lastStepIndex, String lastStepId, String lastStepAction,
            boolean lastStepCrossApp, int mediaWarnings, String extra) {
        StringBuilder sb = new StringBuilder();
        sb.append("-------- 自动诊断 --------\n");
        if (extra != null && extra.contains("测试超时")) {
            sb.append("【根因】测试整体超时 (elapsed=")
                    .append(elapsedMs).append("ms");
            if (timeoutMs > 0) {
                sb.append(", limit=").append(timeoutMs).append("ms");
            }
            sb.append(")。\n");
            if (lastStepId != null && lastStepId.startsWith("wait-screenshot")) {
                sb.append("【根因】停在跨 App 轮询阶段的首个/中间 wait-screenshot 步骤，")
                        .append("录屏弹窗出现后后续截图未跑完。\n");
                sb.append("【建议】增大用例 JSON timeout，或缩短 wait-screenshot 步数/间隔；")
                        .append("确认设备无障碍与 screencap 权限正常。\n");
            }
        }
        if (mediaWarnings > 0) {
            sb.append("【根因】有 ").append(mediaWarnings)
                    .append(" 次截图时录屏/投放弹窗仍未关闭，可能截到弹窗画面。\n");
            sb.append("【建议】检查无障碍是否可点击「取消」；查看 [录屏弹窗] 与 [屏前] 行。\n");
        }
        if (lastStepCrossApp && captured > 0 && extra != null && extra.contains("测试超时")) {
            sb.append("【现象】最后成功截图可能在 step_").append(captured)
                    .append("，跨 App 阶段 step_").append(lastStepIndex)
                    .append(" (").append(lastStepId).append(") 未完成。\n");
        }
        if (captured > 0 && failed > captured * 2) {
            sb.append("【提示】screencap 多次失败、依赖 a11y 截图；若 a11y 超时会导致无图。\n");
        }
        long finishingMs = 0;
        synchronized (LOCK) {
            if (finishingPhaseStartUptimeMs > 0) {
                finishingMs = android.os.SystemClock.uptimeMillis() - finishingPhaseStartUptimeMs;
            }
        }
        if (finishingMs > 15000) {
            sb.append("【提示】收尾阶段已超过 15s，请查看 [exit_flow]/[result_final]/[test_completed] 时间线。\n");
        }
        if (sb.toString().endsWith("-------- 自动诊断 --------\n")) {
            sb.append("未发现明显异常模式；请结合 [步骤]/[调度截图]/[截图]/[finishing] 时间线排查。\n");
        }
        return sb.toString();
    }

    public static File exportSessionFile(Context context, String testId) {
        synchronized (LOCK) {
            if (sessionBuffer.length() == 0) {
                return null;
            }
            sessionBuffer.append("\n结束: ").append(now()).append('\n');
            try {
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        .format(new Date());
                String fileName = "screenshot_diag_" + testId + "_" + ts + ".log";
                File outDir = context.getExternalFilesDir("screenshot_diag_logs");
                if (outDir == null) {
                    outDir = context.getFilesDir();
                }
                if (!outDir.exists()) {
                    outDir.mkdirs();
                }
                File outFile = new File(outDir, fileName);
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(sessionBuffer.toString().getBytes(StandardCharsets.UTF_8));
                }
                Logger fileLogger = Logger.getInstance(context);
                if (fileLogger != null) {
                    File exportDir = fileLogger.getExportDir();
                    if (!exportDir.exists()) {
                        exportDir.mkdirs();
                    }
                    File exportCopy = new File(exportDir, fileName);
                    try (FileOutputStream fos = new FileOutputStream(exportCopy)) {
                        fos.write(sessionBuffer.toString().getBytes(StandardCharsets.UTF_8));
                    }
                }
                sessionBuffer.setLength(0);
                sessionTestId = null;
                return outFile;
            } catch (Exception e) {
                Log.e(TAG, "导出截图诊断日志失败", e);
                return null;
            }
        }
    }

    private static void info(String message) {
        Log.i(TAG, message);
        appendSession("I", message);
        Logger.appendFileLog(Logger.INFO, TAG, message);
    }

    private static void appendSession(String level, String message) {
        synchronized (LOCK) {
            if (sessionTestId == null) {
                return;
            }
            sessionBuffer.append('[').append(now()).append("] [")
                    .append(level).append("] ")
                    .append(message).append('\n');
        }
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                .format(new Date());
    }
}
