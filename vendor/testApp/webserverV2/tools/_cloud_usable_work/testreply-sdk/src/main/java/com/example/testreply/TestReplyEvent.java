package com.example.testreply;

import android.content.Intent;

/**
 * 单条问答回复或任务完成事件。
 */
public class TestReplyEvent {

    private final String sessionId;
    private final int round;
    private final String userMessage;
    private final String replyText;
    private final String outputType;
    private final boolean finalReply;
    private final boolean taskCompleted;
    private final long timestampMs;
    private final long sentAtMs;
    private final long latencyMs;
    private final long firstTokenLatencyMs;
    private final String taskId;
    private final String caseId;

    private TestReplyEvent(Builder builder) {
        this.sessionId = builder.sessionId;
        this.round = builder.round;
        this.userMessage = builder.userMessage;
        this.replyText = builder.replyText;
        this.outputType = builder.outputType;
        this.finalReply = builder.finalReply;
        this.taskCompleted = builder.taskCompleted;
        this.timestampMs = builder.timestampMs > 0
                ? builder.timestampMs
                : System.currentTimeMillis();
        this.sentAtMs = builder.sentAtMs;
        this.latencyMs = builder.latencyMs;
        this.firstTokenLatencyMs = builder.firstTokenLatencyMs;
        this.taskId = builder.taskId;
        this.caseId = builder.caseId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getRound() {
        return round;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getReplyText() {
        return replyText;
    }

    public String getOutputType() {
        return outputType;
    }

    public boolean isFinalReply() {
        return finalReply;
    }

    public boolean isTaskCompleted() {
        return taskCompleted;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public long getSentAtMs() {
        return sentAtMs;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public long getFirstTokenLatencyMs() {
        return firstTokenLatencyMs;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getCaseId() {
        return caseId;
    }

    /** 是否可作为 wait-response 的完成信号。 */
    public boolean signalsWaitComplete() {
        if (taskCompleted) {
            return true;
        }
        return finalReply && replyText != null && !replyText.isEmpty();
    }

    public Intent toBroadcastIntent(String targetPackage) {
        Intent intent = new Intent(TestReplyContract.ACTION_REPLY);
        if (targetPackage != null && !targetPackage.isEmpty()) {
            intent.setPackage(targetPackage);
        }
        intent.putExtras(toBundle());
        return intent;
    }

    public android.os.Bundle toBundle() {
        android.os.Bundle b = new android.os.Bundle();
        b.putString(TestReplyContract.EXTRA_SESSION_ID, sessionId);
        b.putInt(TestReplyContract.EXTRA_ROUND, round);
        b.putString(TestReplyContract.EXTRA_USER_MESSAGE, userMessage);
        b.putString(TestReplyContract.EXTRA_REPLY_TEXT, replyText);
        b.putString(TestReplyContract.EXTRA_OUTPUT_TYPE, outputType);
        b.putBoolean(TestReplyContract.EXTRA_IS_FINAL, finalReply);
        b.putBoolean(TestReplyContract.EXTRA_TASK_COMPLETED, taskCompleted);
        b.putLong(TestReplyContract.EXTRA_TIMESTAMP, timestampMs);
        b.putLong(TestReplyContract.EXTRA_SENT_AT_MS, sentAtMs);
        b.putLong(TestReplyContract.EXTRA_LATENCY_MS, latencyMs);
        b.putLong(TestReplyContract.EXTRA_FIRST_TOKEN_LATENCY_MS, firstTokenLatencyMs);
        if (taskId != null) {
            b.putString(TestReplyContract.EXTRA_TASK_ID, taskId);
        }
        if (caseId != null) {
            b.putString(TestReplyContract.EXTRA_CASE_ID, caseId);
        }
        return b;
    }

    public static TestReplyEvent fromBundle(android.os.Bundle extras) {
        if (extras == null) {
            return null;
        }
        return new Builder()
                .sessionId(extras.getString(TestReplyContract.EXTRA_SESSION_ID))
                .round(extras.getInt(TestReplyContract.EXTRA_ROUND, 0))
                .userMessage(extras.getString(TestReplyContract.EXTRA_USER_MESSAGE))
                .replyText(extras.getString(TestReplyContract.EXTRA_REPLY_TEXT))
                .outputType(extras.getString(TestReplyContract.EXTRA_OUTPUT_TYPE))
                .finalReply(extras.getBoolean(TestReplyContract.EXTRA_IS_FINAL, true))
                .taskCompleted(extras.getBoolean(TestReplyContract.EXTRA_TASK_COMPLETED, false))
                .timestampMs(extras.getLong(
                        TestReplyContract.EXTRA_TIMESTAMP, System.currentTimeMillis()))
                .sentAtMs(extras.getLong(TestReplyContract.EXTRA_SENT_AT_MS, 0L))
                .latencyMs(extras.getLong(TestReplyContract.EXTRA_LATENCY_MS, 0L))
                .firstTokenLatencyMs(extras.getLong(
                        TestReplyContract.EXTRA_FIRST_TOKEN_LATENCY_MS, 0L))
                .taskId(extras.getString(TestReplyContract.EXTRA_TASK_ID))
                .caseId(extras.getString(TestReplyContract.EXTRA_CASE_ID))
                .build();
    }

    public static TestReplyEvent fromIntent(Intent intent) {
        if (intent == null) {
            return null;
        }
        return fromBundle(intent.getExtras());
    }

    public static class Builder {
        private String sessionId;
        private int round;
        private String userMessage;
        private String replyText;
        private String outputType = TestReplyContract.OUTPUT_TEXT;
        private boolean finalReply = true;
        private boolean taskCompleted;
        private long timestampMs;
        private long sentAtMs;
        private long latencyMs;
        private long firstTokenLatencyMs;
        private String taskId;
        private String caseId;

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder round(int round) {
            this.round = round;
            return this;
        }

        public Builder userMessage(String userMessage) {
            this.userMessage = userMessage;
            return this;
        }

        public Builder replyText(String replyText) {
            this.replyText = replyText;
            return this;
        }

        public Builder outputType(String outputType) {
            this.outputType = outputType;
            return this;
        }

        public Builder finalReply(boolean finalReply) {
            this.finalReply = finalReply;
            return this;
        }

        public Builder taskCompleted(boolean taskCompleted) {
            this.taskCompleted = taskCompleted;
            return this;
        }

        public Builder timestampMs(long timestampMs) {
            this.timestampMs = timestampMs;
            return this;
        }

        public Builder sentAtMs(long sentAtMs) {
            this.sentAtMs = sentAtMs;
            return this;
        }

        public Builder latencyMs(long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        public Builder firstTokenLatencyMs(long firstTokenLatencyMs) {
            this.firstTokenLatencyMs = firstTokenLatencyMs;
            return this;
        }

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder caseId(String caseId) {
            this.caseId = caseId;
            return this;
        }

        public TestReplyEvent build() {
            return new TestReplyEvent(this);
        }
    }
}
