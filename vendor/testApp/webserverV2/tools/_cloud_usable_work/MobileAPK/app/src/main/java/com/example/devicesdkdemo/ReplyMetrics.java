package com.example.devicesdkdemo;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * AISF 回复与时延指标，供 PC 端 adb logcat -s DemoReplyMetrics:I 解析。
 * <pre>
 * SEND session=... round=1 text=...
 * FIRST_TOKEN session=... round=1 ttft_ms=120 kind=TEXT chunk=...
 * CHUNK session=... round=1 seq=1 elapsed_ms=120 at_ms=... replied_at=... text=...
 * REPLY session=... round=1 ttft_ms=120 latency_ms=4500 reason=stream|task|next_send chunks=3
 * </pre>
 * CHUNK = 每条流式/分片回复（不整合）；REPLY 仅作本轮结束信号（不再带整合全文）。
 */
public final class ReplyMetrics {
    public static final String TAG = "DemoReplyMetrics";
    private static final int MAX_TEXT_LOG = 1500;
    private static final SimpleDateFormat TS =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private static final class State {
        int round;
        String userMessage;
        long sentAtMs;
        long firstTokenAtMs;
        boolean finalized;
        int chunkSeq;
    }

    private static final Map<String, State> STATES = new HashMap<>();

    private ReplyMetrics() {
    }

    public static synchronized void onUserSend(String sessionId, String userMessage) {
        if (sessionId == null) {
            return;
        }
        State prev = STATES.get(sessionId);
        if (prev != null && !prev.finalized && prev.sentAtMs > 0) {
            emitReply(sessionId, prev, "next_send");
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
        }
        // 不整合：每条分片单独打 CHUNK，带相对时延与墙钟时间
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
    }

    /** GUI / DIALOG / 其它首包信号，也记作首 token。 */
    public static synchronized void onFirstSignal(String sessionId, String kind, String hint) {
        if (sessionId == null) {
            return;
        }
        State s = STATES.get(sessionId);
        if (s == null || s.finalized) {
            return;
        }
        markFirst(sessionId, s, kind != null ? kind : "SIGNAL", hint != null ? hint : "");
    }

    public static synchronized void onTaskCompleted(String sessionId) {
        finalize(sessionId, "task");
    }

    public static synchronized void finalizeStream(String sessionId) {
        finalize(sessionId, "stream");
    }

    public static synchronized void finalize(String sessionId, String reason) {
        if (sessionId == null) {
            return;
        }
        State s = STATES.get(sessionId);
        if (s == null || s.finalized || s.sentAtMs <= 0) {
            return;
        }
        emitReply(sessionId, s, reason != null ? reason : "unknown");
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

    private static void emitReply(String sessionId, State s, String reason) {
        if (s.finalized) {
            return;
        }
        s.finalized = true;
        long now = System.currentTimeMillis();
        long latency = now - s.sentAtMs;
        long ttft = s.firstTokenAtMs > 0 ? (s.firstTokenAtMs - s.sentAtMs) : -1L;
        // 不再输出整合 text=；仅结束信号 + 分片数
        Log.i(TAG, "REPLY session=" + shortId(sessionId)
                + " round=" + s.round
                + " ttft_ms=" + ttft
                + " latency_ms=" + latency
                + " reason=" + reason
                + " chunks=" + s.chunkSeq);
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
