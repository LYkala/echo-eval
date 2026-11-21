package com.example.testreply;

/**
 * SDK 初始化配置。
 */
public class TestReplyConfig {

    private final String targetPackage;
    private final String taskId;
    private final String caseId;
    private final String serverUrl;

    private TestReplyConfig(Builder builder) {
        this.targetPackage = builder.targetPackage;
        this.taskId = builder.taskId;
        this.caseId = builder.caseId;
        this.serverUrl = builder.serverUrl;
    }

    public String getTargetPackage() {
        return targetPackage;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getCaseId() {
        return caseId;
    }

    /** 测试 WebServer 根地址，如 http://host:9527；供 Device 侧 HTTP 兜底上报。 */
    public String getServerUrl() {
        return serverUrl;
    }

    public static class Builder {
        private String targetPackage = TestReplyContract.DEFAULT_TARGET_PACKAGE;
        private String taskId;
        private String caseId;
        private String serverUrl;

        public Builder targetPackage(String targetPackage) {
            this.targetPackage = targetPackage;
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

        public Builder serverUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        public TestReplyConfig build() {
            return new TestReplyConfig(this);
        }
    }
}
