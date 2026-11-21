package com.example.devicesdkdemo;

import android.content.Context;
import android.util.Log;

import com.example.testreply.TestReplyEvent;
import com.example.testreply.TestReplySdk;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AISF 回复与时延指标：logcat DemoReplyMetrics + TestReply 中转给 TestApp（再上报 9526）。
 * <p>
 * GUI 用例：START_GUI 后必须等 TASK_STATUS taskCompleted（状态已完成）
 * 再 handoff / 下一用例，不能仅因 GUI_TASK 信号或文本流结束就拉回 Demo。
 */
public final class ReplyMetrics {
    public static final String TAG = "DemoReplyMetrics";
    private static final int MAX_TEXT_LOG = 1500;
    private static final SimpleDateFormat TS =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private static volatile Context appContext;

    private static final class State {
        int round;
        String userMessage;
        long sentAtMs;
        long firstTokenAtMs;
        boolean finalized;
        boolean draftPublished;
        boolean guiStarted;
        int chunkSeq;
        /** 上次已上报到网页的文本长度，用于 GUI 进度增量推送。 */
        int lastPublishedTextLen;
        long lastProgressPublishAtMs;
        StringBuilder replyBuf = new StringBuilder();
        /** 本轮已收口的回复分段（每段连续 TEXT 流为一节）。 */
        final List<String> segments = new ArrayList<>();
        /** 当前正在累积的 TEXT 段。 */
        final StringBuilder segmentBuf = new StringBuilder();
        String lastKind = "TEXT";
    }

    private static final Map<String, State> STATES = new HashMap<>();
    private static volatile int lastReplyRound = 0;
    /** 已出现 GUI_TASK 且尚未收到 taskCompleted 的轮次；0=无挂起 GUI。 */
    private static volatile int pendingGuiRound = 0;
    private static volatile int lastTaskCompletedRound = 0;

    private ReplyMetrics() {
    }

    public static void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    public static synchronized int getLastReplyRound() {
        return lastReplyRound;
    }

    /** 新用例开始：轮次从 0 再计，避免跨用例 round=2/3/4 导致评判对不上 round=1。 */
    public static synchronized void resetForNewCase() {
        STATES.clear();
        lastReplyRound = 0;
        pendingGuiRound = 0;
        lastTaskCompletedRound = 0;
        Log.i(TAG, "resetForNewCase");
    }

    public static synchronized boolean hasPendingGui() {
        return pendingGuiRound > 0 && pendingGuiRound > lastTaskCompletedRound;
    }

    /** TASK_STATUS 到达前：本轮是否曾启动 GUI，需拉回 QA 聊天页。 */
    public static synchronized boolean shouldReturnToQaAfterTask(String sessionId) {
        if (hasPendingGuiLocked()) {
            return true;
        }
        State s = sessionId != null ? STATES.get(sessionId) : null;
        return s != null && s.guiStarted;
    }

    /** stop_test 时唤醒 wait*，避免卡在 Object.wait。 */
    public static void wakeWaiters() {
        synchronized (ReplyMetrics.class) {
            ReplyMetrics.class.notifyAll();
        }
    }

    /** 等待 ReplyMetrics 上报新 REPLY（round 大于 afterRound）。 */
    public static boolean waitForReplyAfterRound(int afterRound, long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            if (WebServerCaseRunner.isStopRequested()
                    || Thread.currentThread().isInterrupted()) {
                Log.i(TAG, "waitForReplyAfterRound: stopped");
                return lastReplyRound > afterRound;
            }
            synchronized (ReplyMetrics.class) {
                if (lastReplyRound > afterRound) {
                    return true;
                }
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) {
                    break;
                }
                try {
                    ReplyMetrics.class.wait(Math.min(500L, remain));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return lastReplyRound > afterRound;
                }
            }
        }
        return lastReplyRound > afterRound;
    }

    /**
     * 若本轮已 START_GUI，则阻塞直到 TASK_STATUS 已完成（或超时）。
     * 无挂起 GUI 时立即返回 true。
     */
    public static boolean waitForGuiTaskCompleted(long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            if (WebServerCaseRunner.isStopRequested()
                    || Thread.currentThread().isInterrupted()) {
                Log.i(TAG, "waitForGuiTaskCompleted: stopped");
                synchronized (ReplyMetrics.class) {
                    return !hasPendingGuiLocked();
                }
            }
            synchronized (ReplyMetrics.class) {
                if (!hasPendingGuiLocked()) {
                    Log.i(TAG, "waitForGuiTaskCompleted: settled"
                            + " pendingGui=" + pendingGuiRound
                            + " taskDoneRound=" + lastTaskCompletedRound);
                    return true;
                }
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) {
                    break;
                }
                try {
                    ReplyMetrics.class.wait(Math.min(500L, remain));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return !hasPendingGuiLocked();
                }
            }
        }
        synchronized (ReplyMetrics.class) {
            boolean ok = !hasPendingGuiLocked();
            if (!ok) {
                Log.w(TAG, "waitForGuiTaskCompleted TIMEOUT"
                        + " pendingGui=" + pendingGuiRound
                        + " taskDoneRound=" + lastTaskCompletedRound);
            }
            return ok;
        }
    }

    private static boolean hasPendingGuiLocked() {
        return pendingGuiRound > 0 && pendingGuiRound > lastTaskCompletedRound;
    }

    public static synchronized void onUserSend(String sessionId, String userMessage) {
        if (sessionId == null) {
            return;
        }
        State prev = STATES.get(sessionId);
        if (prev != null && !prev.finalized && prev.sentAtMs > 0) {
            emitReply(sessionId, prev, "next_send", true);
        }
        State s = new State();
        s.round = (prev != null ? prev.round : 0) + 1;
        s.userMessage = userMessage != null ? userMessage : "";
        s.sentAtMs = System.currentTimeMillis();
        STATES.put(sessionId, s);
        Log.i(TAG, "SEND session=" + shortId(sessionId)
                + " round=" + s.round
                + " text=" + sanitize(s.userMessage, 200));
    }

    public static synchronized void onTextChunk(String sessionId, String chunk) {
        if (sessionId == null || chunk == null) {
            return;
        }
        State s = STATES.get(sessionId);
        if (s == null || s.finalized) {
            return;
        }
        if (!chunk.isEmpty()) {
            markFirst(sessionId, s, "TEXT", chunk);
            // GUI 进度句（正在执行/正在总结）在手机上是多条气泡：这里按句切 segment，便于网页分段展示
            String trimmed = chunk.trim();
            boolean progressLine = s.guiStarted && isGuiProgressLine(trimmed);
            if (progressLine && s.segmentBuf.length() > 0) {
                flushSegment(s);
            }
            s.replyBuf.append(chunk);
            s.segmentBuf.append(chunk);
            s.lastKind = "TEXT";
            if (progressLine) {
                flushSegment(s);
            }
        }
        if (chunk.isEmpty()) {
            return;
        }
        s.chunkSeq += 1;
        long now = System.currentTimeMillis();
        long elapsed = s.sentAtMs > 0 ? (now - s.sentAtMs) : -1L;
        String repliedAt;
        synchronized (TS) {
            repliedAt = TS.format(new Date(now));
        }
        Log.i(TAG, "CHUNK session=" + shortId(sessionId)
                + " round=" + s.round
                + " seq=" + s.chunkSeq
                + " elapsed_ms=" + elapsed
                + " at_ms=" + now
                + " replied_at=" + repliedAt
                + " text=" + sanitize(chunk, MAX_TEXT_LOG));
        // GUI 接管中：把进度增量推到网页（不只发一次 draft）
        if (s.guiStarted || hasPendingGuiLocked()) {
            boolean force = isGuiProgressLine(chunk.trim());
            publishGuiProgress(sessionId, s, force ? "gui_progress_line" : "gui_chunk", force);
        }
    }

    public static synchronized void onFirstSignal(String sessionId, String kind, String hint) {
        if (sessionId == null) {
            return;
        }
        State s = STATES.get(sessionId);
        if (s == null || s.finalized) {
            return;
        }
        String k = kind != null ? kind : "SIGNAL";
        // 非 TEXT 信号到达即视为上一段文本流结束，后续 TEXT 另起一段
        flushSegment(s);
        s.lastKind = k;
        if (hint != null && !hint.isEmpty() && s.replyBuf.length() == 0) {
            s.replyBuf.append(hint);
        }
        markFirst(sessionId, s, k, hint != null ? hint : "");
        if (isGuiKind(k)) {
            s.guiStarted = true;
            pendingGuiRound = Math.max(pendingGuiRound, s.round);
            Log.i(TAG, "GUI_STARTED session=" + shortId(sessionId)
                    + " round=" + s.round + " kind=" + k
                    + " pendingGui=" + pendingGuiRound);
            // GUI 一开始就上报已有文本/信号，避免任务卡在优酷等界面时结果页一直「空回复」
            publishDraft(sessionId, s, "gui_start");
        }
    }

    /**
     * 澄清弹窗：立刻通知 TestApp（不 finalize 本轮，仍可继续收 TEXT）。
     */
    public static synchronized void publishDialogSignal(String sessionId, String hint) {
        if (sessionId == null) {
            return;
        }
        State s = STATES.get(sessionId);
        if (s == null || s.sentAtMs <= 0) {
            return;
        }
        String text = (hint != null && !hint.isEmpty())
                ? hint
                : (s.replyBuf.length() > 0 ? s.replyBuf.toString() : "dialog");
        Log.i(TAG, "DIALOG_SIGNAL session=" + shortId(sessionId)
                + " round=" + s.round + " text=" + sanitize(text, 200));
        try {
            Context ctx = appContext;
            if (ctx == null) {
                return;
            }
            long now = System.currentTimeMillis();
            TestReplyEvent event = new TestReplyEvent.Builder()
                    .sessionId(sessionId)
                    .round(s.round)
                    .userMessage(s.userMessage)
                    .replyText(text)
                    .outputType("DIALOG")
                    .finalReply(false)
                    .taskCompleted(false)
                    .timestampMs(now)
                    .sentAtMs(s.sentAtMs)
                    .latencyMs(now - s.sentAtMs)
                    .firstTokenLatencyMs(s.firstTokenAtMs > 0 ? (s.firstTokenAtMs - s.sentAtMs) : -1L)
                    .build();
            TestReplySdk.publishReply(ctx, event);
        } catch (Throwable t) {
            Log.w(TAG, "publishDialogSignal failed: " + t.getMessage());
        }
    }

    public static synchronized void onTaskCompleted(String sessionId) {
        State s = sessionId != null ? STATES.get(sessionId) : null;
        int round = s != null ? s.round : Math.max(pendingGuiRound, lastReplyRound);
        lastTaskCompletedRound = Math.max(lastTaskCompletedRound, round);
        if (pendingGuiRound > 0 && lastTaskCompletedRound >= pendingGuiRound) {
            Log.i(TAG, "GUI_TASK_COMPLETED session=" + shortId(sessionId)
                    + " round=" + round
                    + " pendingGui cleared was=" + pendingGuiRound);
            pendingGuiRound = 0;
        } else {
            Log.i(TAG, "TASK_COMPLETED session=" + shortId(sessionId)
                    + " round=" + round
                    + " pendingGui=" + pendingGuiRound);
        }
        ReplyMetrics.class.notifyAll();
        finalize(sessionId, "task");
        try {
            String json = com.huawei.aisfsdkv2.component.gui.GuiStepLatencyTracker.getInstance()
                    .snapshotJson("task_completed");
            if (json != null) {
                publishGuiStepLatency(round, json);
            }
        } catch (Throwable t) {
            Log.w(TAG, "flushGuiStepLatency: " + t.getMessage());
        }
    }

    /** 操控指令的结果图已上传：通知 TestApp 拍 gui_poll，画面仍是这一步执行完的样子。 */
    public static void onControlStepShotReady(String cmdDesc) {
        Context ctx = appContext;
        if (ctx == null) {
            return;
        }
        Log.i(TAG, "gui_poll after control cmd=" + cmdDesc);
        try {
            TestReplySdk.publishGuiPollAfterControl(ctx, cmdDesc);
        } catch (Throwable t) {
            Log.w(TAG, "gui_poll after control failed: " + t.getMessage());
        }
    }

    /** SDK tracker 回调（STOP_GUI 等）：立刻经 TestApp 上报。 */
    public static void onGuiStepLatencySnapshot(String jsonPayload) {
        if (jsonPayload == null || jsonPayload.isEmpty()) {
            return;
        }
        int round;
        synchronized (ReplyMetrics.class) {
            round = Math.max(pendingGuiRound, lastReplyRound);
        }
        Log.i(TAG, "gui_step_latency tracker callback round=" + round
                + " bytes=" + jsonPayload.length());
        publishGuiStepLatency(round, jsonPayload);
    }

    private static void publishGuiStepLatency(int round, String jsonPayload) {
        try {
            Context ctx = appContext;
            if (ctx != null) {
                TestReplySdk.publishGuiStepLatency(ctx, round, jsonPayload);
            }
        } catch (Throwable t) {
            Log.w(TAG, "publishGuiStepLatency failed: " + t.getMessage());
        }
    }

    public static synchronized void finalizeStream(String sessionId) {
        State s = sessionId != null ? STATES.get(sessionId) : null;
        // 仅当 GUI 仍挂起时用 draft；否则 TEXT 静默收口必须发 final，否则 QA wait-response 会 text_timeout
        if (s != null && (s.guiStarted || isGuiKind(s.lastKind)) && hasPendingGuiLocked()) {
            Log.i(TAG, "finalizeStream draft for GUI pending"
                    + " session=" + shortId(sessionId)
                    + " round=" + s.round
                    + " pendingGui=" + pendingGuiRound);
            publishDraft(sessionId, s, "stream_gui");
            return;
        }
        finalize(sessionId, "stream");
    }

    /**
     * stop/超时：把尚未 finalize 的轮次尽量上报，避免结果页模型回答一直为空。
     */
    public static synchronized void flushPendingReplies(String reason) {
        String why = reason != null ? reason : "flush";
        for (Map.Entry<String, State> e : new HashMap<>(STATES).entrySet()) {
            State s = e.getValue();
            if (s == null || s.finalized || s.sentAtMs <= 0) {
                continue;
            }
            if (s.replyBuf.length() == 0 && s.firstTokenAtMs <= 0 && !s.guiStarted) {
                continue;
            }
            emitReply(e.getKey(), s, why, /*finalizing*/ true);
        }
    }

    /** 停止用例：清挂起 GUI 并尽量把已有回复上报。 */
    public static synchronized void abortPendingGui(String reason) {
        String why = reason != null ? reason : "abort";
        if (pendingGuiRound > 0) {
            Log.i(TAG, "abortPendingGui reason=" + why
                    + " pendingRound=" + pendingGuiRound);
            lastTaskCompletedRound = Math.max(lastTaskCompletedRound, pendingGuiRound);
            pendingGuiRound = 0;
        }
        flushPendingReplies(why);
        ReplyMetrics.class.notifyAll();
    }

    public static synchronized void finalize(String sessionId, String reason) {
        if (sessionId == null) {
            return;
        }
        State s = STATES.get(sessionId);
        if (s == null || s.finalized || s.sentAtMs <= 0) {
            return;
        }
        // 空回复不锁死本轮：NOTIFY 等曾导致 empty finalize → 后续 TEXT 被 onTextChunk 丢弃
        if (s.replyBuf.length() == 0 && s.firstTokenAtMs <= 0 && !s.guiStarted
                && !"task".equals(reason)) {
            Log.w(TAG, "finalize skipped empty (keep open for TEXT)"
                    + " session=" + shortId(sessionId)
                    + " round=" + s.round
                    + " reason=" + reason);
            return;
        }
        // GUI 尚未 TASK_STATUS 完成：中途流式收口只发 draft，禁止 final，
        // 避免 TestApp 误结束 wait-response 并拉回 Device 打断操控。
        if (!"task".equals(reason) && hasPendingGuiLocked()) {
            Log.i(TAG, "finalize deferred as draft while GUI pending"
                    + " session=" + shortId(sessionId)
                    + " round=" + s.round
                    + " reason=" + reason
                    + " pendingGui=" + pendingGuiRound);
            publishDraft(sessionId, s, (reason != null ? reason : "unknown") + "_gui_pending");
            return;
        }
        emitReply(sessionId, s, reason != null ? reason : "unknown", true);
    }

    private static void publishDraft(String sessionId, State s, String reason) {
        publishGuiProgress(sessionId, s, reason, true);
    }

    /**
     * 把当前累计回复推到 TestApp → 网页（非 final）。
     * GUI 过程中可多次调用：内容变长才发；force 用于「正在执行」等关键进度句。
     */
    private static void publishGuiProgress(
            String sessionId, State s, String reason, boolean force) {
        if (s == null || s.finalized || s.sentAtMs <= 0) {
            return;
        }
        if (s.replyBuf.length() == 0 && s.firstTokenAtMs <= 0) {
            return;
        }
        int len = s.replyBuf.length();
        long now = System.currentTimeMillis();
        if (len <= s.lastPublishedTextLen) {
            return;
        }
        if (!force && s.lastPublishedTextLen > 0
                && now - s.lastProgressPublishAtMs < 1200L) {
            return;
        }
        emitReply(sessionId, s, reason != null ? reason : "progress", false);
    }

    private static boolean isGuiProgressLine(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.startsWith("正在执行") || text.startsWith("正在总结");
    }

    /** 收口当前 TEXT 段：一段连续文本流结束（遇到 GUI_TASK/TASK_STATUS/DIALOG 等信号）。 */
    private static void flushSegment(State s) {
        if (s == null || s.segmentBuf.length() == 0) {
            return;
        }
        String seg = s.segmentBuf.toString().trim();
        s.segmentBuf.setLength(0);
        if (!seg.isEmpty()) {
            s.segments.add(seg);
        }
    }

    /** 上报用分段快照：已收口段 + 尚未收口的当前段。 */
    private static List<String> snapshotSegments(State s) {
        List<String> out = new ArrayList<>(s.segments);
        String tail = s.segmentBuf.toString().trim();
        if (!tail.isEmpty()) {
            out.add(tail);
        }
        return out;
    }

    private static boolean isGuiKind(String kind) {
        if (kind == null) {
            return false;
        }
        String k = kind.toUpperCase(Locale.US);
        return k.contains("GUI");
    }

    private static void markFirst(String sessionId, State s, String kind, String chunk) {
        if (s.firstTokenAtMs > 0) {
            return;
        }
        s.firstTokenAtMs = System.currentTimeMillis();
        long ttft = s.firstTokenAtMs - s.sentAtMs;
        Log.i(TAG, "FIRST_TOKEN session=" + shortId(sessionId)
                + " round=" + s.round
                + " ttft_ms=" + ttft
                + " kind=" + kind
                + " chunk=" + sanitize(chunk, 200));
    }

    private static void emitReply(String sessionId, State s, String reason, boolean finalizing) {
        if (s.finalized) {
            return;
        }
        if (finalizing) {
            flushSegment(s);
            s.finalized = true;
        } else {
            s.draftPublished = true;
            s.lastPublishedTextLen = s.replyBuf.length();
            s.lastProgressPublishAtMs = System.currentTimeMillis();
        }
        lastReplyRound = Math.max(lastReplyRound, s.round);
        long now = System.currentTimeMillis();
        long latency = now - s.sentAtMs;
        long ttft = s.firstTokenAtMs > 0 ? (s.firstTokenAtMs - s.sentAtMs) : -1L;
        Log.i(TAG, "REPLY session=" + shortId(sessionId)
                + " round=" + s.round
                + " ttft_ms=" + ttft
                + " latency_ms=" + latency
                + " reason=" + reason
                + " final=" + finalizing
                + " chunks=" + s.chunkSeq
                + " guiStarted=" + s.guiStarted
                + " textLen=" + s.replyBuf.length()
                + " segs=" + snapshotSegments(s).size());
        String text = s.replyBuf.toString();
        if (text.isEmpty() && s.guiStarted) {
            text = "[GUI_TASK] " + (s.lastKind != null ? s.lastKind : "GUI");
        }
        boolean taskDone = "task".equals(reason);
        // 优先经 TestApp 中转（9526 只跟 TestApp WS 对话）
        try {
            Context ctx = appContext;
            if (ctx != null) {
                TestReplyEvent event = new TestReplyEvent.Builder()
                        .sessionId(sessionId)
                        .round(s.round)
                        .userMessage(s.userMessage)
                        .replyText(text)
                        .outputType(s.lastKind != null ? s.lastKind : "TEXT")
                        .finalReply(finalizing)
                        .taskCompleted(taskDone)
                        .timestampMs(now)
                        .sentAtMs(s.sentAtMs)
                        .latencyMs(latency)
                        .firstTokenLatencyMs(ttft)
                        .segments(snapshotSegments(s))
                        .build();
                TestReplySdk.publishReply(ctx, event);
            }
        } catch (Throwable t) {
            Log.w(TAG, "TestReply publish failed: " + t.getMessage());
        }
        // 兼容：若仍开启 Demo 直连，再走 HTTP/WS
        try {
            WebServerReporter.reportFinalReply(
                    sessionId,
                    s.round,
                    s.userMessage,
                    text,
                    s.lastKind != null ? s.lastKind : "TEXT",
                    latency,
                    ttft,
                    s.sentAtMs,
                    taskDone
            );
        } catch (Throwable t) {
            Log.w(TAG, "WebServerReporter failed: " + t.getMessage());
        }
        synchronized (ReplyMetrics.class) {
            ReplyMetrics.class.notifyAll();
        }
    }

    private static String shortId(String sid) {
        if (sid == null) {
            return "null";
        }
        return sid.length() <= 8 ? sid : sid.substring(0, 8);
    }

    private static String sanitize(String raw, int max) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace('\n', ' ').replace('\r', ' ');
        if (s.length() > max) {
            return s.substring(0, max) + "...";
        }
        return s;
    }
}
