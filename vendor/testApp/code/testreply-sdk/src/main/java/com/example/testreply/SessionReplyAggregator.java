package com.example.testreply;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 帮助 DeviceAPP 拼接流式回复，并关联用户提问轮次与时延。
 */
class SessionReplyAggregator {

    private static class SessionState {
        int round;
        String lastUserMessage;
        long sentAtMs;
        /** AISF 本轮首次回复时间（首 Token / 首段内容） */
        long firstTokenAtMs;
        final StringBuilder streamingText = new StringBuilder();
        /** 本轮已切分出的段落（AISF 每段 TEXT 流式结束为一节）。 */
        final List<String> segments = new ArrayList<>();
        /** streamingText 中已归入 segments 的长度。 */
        int segmentOffset;
    }

    private final Map<String, SessionState> sessions = new HashMap<>();

    /**
     * 新用例开始时调用：轮次按用例从 0 复位，下一问即为 round=1。
     * 与 AISF sessionId 是否复用无关。
     */
    synchronized void resetAllRounds() {
        for (SessionState state : sessions.values()) {
            if (state == null) {
                continue;
            }
            state.round = 0;
            state.lastUserMessage = null;
            state.sentAtMs = 0L;
            state.firstTokenAtMs = 0L;
            state.streamingText.setLength(0);
            state.segments.clear();
            state.segmentOffset = 0;
        }
    }

    synchronized int onUserMessageSent(String sessionId, String userMessage) {
        if (sessionId == null) {
            return 0;
        }
        SessionState state = sessions.computeIfAbsent(sessionId, key -> new SessionState());
        state.round += 1;
        state.lastUserMessage = userMessage;
        state.sentAtMs = System.currentTimeMillis();
        state.firstTokenAtMs = 0L;
        state.streamingText.setLength(0);
        state.segments.clear();
        state.segmentOffset = 0;
        return state.round;
    }

    synchronized void appendChunk(String sessionId, String chunk) {
        if (sessionId == null || chunk == null) {
            return;
        }
        SessionState state = sessions.computeIfAbsent(sessionId, key -> new SessionState());
        if (!chunk.trim().isEmpty()) {
            markFirstToken(state);
        }
        state.streamingText.append(chunk);
    }

    synchronized TestReplyEvent finalizeReply(String sessionId, String outputType) {
        if (sessionId == null) {
            return null;
        }
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            return null;
        }
        // 不清空 streamingText：同一轮多段 TEXT 继续累积，后续 finalize 上报完整回复
        String replyText = state.streamingText.toString().trim();
        if (replyText.isEmpty()) {
            return null;
        }
        if (state.firstTokenAtMs <= 0) {
            markFirstToken(state);
        }
        long completedAt = System.currentTimeMillis();
        long latencyMs = state.sentAtMs > 0 ? completedAt - state.sentAtMs : 0L;
        recordSegment(state);
        return new TestReplyEvent.Builder()
                .sessionId(sessionId)
                .round(state.round > 0 ? state.round : 1)
                .userMessage(state.lastUserMessage)
                .replyText(replyText)
                .outputType(outputType != null ? outputType : TestReplyContract.OUTPUT_TEXT)
                .finalReply(true)
                .sentAtMs(state.sentAtMs)
                .latencyMs(latencyMs)
                .firstTokenLatencyMs(firstTokenLatency(state))
                .timestampMs(completedAt)
                .segments(state.segments)
                .build();
    }

    synchronized TestReplyEvent buildTextReply(String sessionId, String replyText, String outputType) {
        if (sessionId == null || replyText == null || replyText.trim().isEmpty()) {
            return null;
        }
        SessionState state = sessions.get(sessionId);
        if (state != null) {
            markFirstToken(state);
            // 非聚焦模式也会来多段 TEXT：先写入累积缓冲，再按完整内容上报
            state.streamingText.append(replyText);
            replyText = state.streamingText.toString();
            recordSegment(state);
        }
        int round = state != null && state.round > 0 ? state.round : 1;
        String userMessage = state != null ? state.lastUserMessage : null;
        long sentAt = state != null ? state.sentAtMs : 0L;
        long completedAt = System.currentTimeMillis();
        long latencyMs = sentAt > 0 ? completedAt - sentAt : 0L;
        return new TestReplyEvent.Builder()
                .sessionId(sessionId)
                .round(round)
                .userMessage(userMessage)
                .replyText(replyText.trim())
                .outputType(outputType != null ? outputType : TestReplyContract.OUTPUT_TEXT)
                .finalReply(true)
                .sentAtMs(sentAt)
                .latencyMs(latencyMs)
                .firstTokenLatencyMs(firstTokenLatency(state))
                .timestampMs(completedAt)
                .segments(state != null ? state.segments : null)
                .build();
    }

    synchronized TestReplyEvent buildTaskCompletedEvent(
            String sessionId, int statusCode, String outputType) {
        if (sessionId == null) {
            return null;
        }
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            return null;
        }
        long completedAt = System.currentTimeMillis();
        long latencyMs = state.sentAtMs > 0 ? completedAt - state.sentAtMs : 0L;
        return new TestReplyEvent.Builder()
                .sessionId(sessionId)
                .round(state.round > 0 ? state.round : 1)
                .userMessage(state.lastUserMessage)
                .replyText("")
                .outputType(outputType != null ? outputType : TestReplyContract.OUTPUT_TASK_STATUS)
                .finalReply(true)
                .taskCompleted(true)
                .sentAtMs(state.sentAtMs)
                .latencyMs(latencyMs)
                .firstTokenLatencyMs(firstTokenLatency(state))
                .timestampMs(completedAt)
                .build();
    }

    /** AISF 下发澄清弹窗(DIALOG)时通知 TestApp，不结束当前 wait-response。 */
    synchronized TestReplyEvent buildDialogReceivedEvent(String sessionId, String dialogTitle) {
        if (sessionId == null) {
            return null;
        }
        SessionState state = sessions.get(sessionId);
        if (state != null) {
            markFirstToken(state);
        }
        int round = state != null && state.round > 0 ? state.round : 1;
        String userMessage = state != null ? state.lastUserMessage : null;
        long now = System.currentTimeMillis();
        return new TestReplyEvent.Builder()
                .sessionId(sessionId)
                .round(round)
                .userMessage(userMessage)
                .replyText(dialogTitle != null ? dialogTitle : "")
                .outputType(TestReplyContract.OUTPUT_DIALOG)
                .finalReply(false)
                .taskCompleted(false)
                .sentAtMs(state != null ? state.sentAtMs : 0L)
                .latencyMs(0L)
                .firstTokenLatencyMs(firstTokenLatency(state))
                .timestampMs(now)
                .build();
    }

    /** 澄清回复：不增加轮次，清空流式缓冲等待后续 TEXT。 */
    synchronized void sendClarify(String sessionId, String clarifyText) {
        if (sessionId == null || clarifyText == null || clarifyText.isEmpty()) {
            return;
        }
        SessionState state = sessions.computeIfAbsent(sessionId, key -> new SessionState());
        state.streamingText.setLength(0);
        state.segments.clear();
        state.segmentOffset = 0;
    }

    /** 把上次切分之后新增的文本记为一节（AISF 每段 TEXT 流式结束各记一节）。 */
    private static void recordSegment(SessionState state) {
        if (state == null) {
            return;
        }
        int len = state.streamingText.length();
        if (len <= state.segmentOffset) {
            return;
        }
        String segment = state.streamingText.substring(state.segmentOffset, len).trim();
        if (!segment.isEmpty()) {
            state.segments.add(segment);
        }
        state.segmentOffset = len;
    }

    private static void markFirstToken(SessionState state) {
        if (state != null && state.firstTokenAtMs <= 0) {
            state.firstTokenAtMs = System.currentTimeMillis();
        }
    }

    private static long firstTokenLatency(SessionState state) {
        if (state == null || state.firstTokenAtMs <= 0 || state.sentAtMs <= 0) {
            return 0L;
        }
        return Math.max(0L, state.firstTokenAtMs - state.sentAtMs);
    }
}
