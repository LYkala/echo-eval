package com.example.testreply;

/**
 * DeviceAPP 与 TestApp 之间的回复广播协议。
 */
public final class TestReplyContract {

    private TestReplyContract() {
    }

    /** DeviceAPP 上报 AI/服务端回复 */
    public static final String ACTION_REPLY =
            "com.example.testreply.ACTION_SESSION_REPLY";

    /** TestApp → DeviceAPP：用例控制（切用例时重置轮次） */
    public static final String ACTION_CASE_CONTROL =
            "com.example.testreply.ACTION_CASE_CONTROL";

    /** TestApp → DeviceAPP：发送澄清消息（收到 DIALOG 后） */
    public static final String ACTION_CLARIFY_SEND =
            "com.example.testreply.ACTION_CLARIFY_SEND";

    /** TestApp → DeviceAPP：强制结束 GUI 接管（退出第三方界面） */
    public static final String ACTION_FORCE_END_TAKEOVER =
            "com.example.testreply.ACTION_FORCE_END_TAKEOVER";

    /** TestApp → DeviceAPP：请求 DeviceSDK MediaProjection 截图（第三方 App 如爱奇艺） */
    public static final String ACTION_GUI_SCREEN_CAPTURE_REQUEST =
            "com.example.testreply.ACTION_GUI_SCREEN_CAPTURE_REQUEST";

    /** DeviceAPP → TestApp：MediaProjection 截图结果 */
    public static final String ACTION_GUI_SCREEN_CAPTURE_RESULT =
            "com.example.testreply.ACTION_GUI_SCREEN_CAPTURE_RESULT";

    /** DeviceAPP → TestApp：一条操控指令已执行完，请拍 gui_poll */
    public static final String ACTION_GUI_POLL_AFTER_CONTROL =
            "com.example.testreply.ACTION_GUI_POLL_AFTER_CONTROL";
    public static final String EXTRA_GUI_CMD_NAME = "gui_cmd_name";

    /** DeviceAPP → TestApp：GUI 单步时延 JSON */
    public static final String ACTION_GUI_STEP_LATENCY =
            "com.example.testreply.ACTION_GUI_STEP_LATENCY";
    public static final String EXTRA_GUI_STEP_LATENCY_JSON = "gui_step_latency_json";
    public static final String EXTRA_GUI_STEP_LATENCY_ROUND = "gui_step_latency_round";

    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_ROUND = "round";
    public static final String EXTRA_USER_MESSAGE = "user_message";
    public static final String EXTRA_REPLY_TEXT = "reply_text";
    public static final String EXTRA_OUTPUT_TYPE = "output_type";
    public static final String EXTRA_IS_FINAL = "is_final";
    public static final String EXTRA_TIMESTAMP = "timestamp_ms";
    public static final String EXTRA_SENT_AT_MS = "sent_at_ms";
    public static final String EXTRA_LATENCY_MS = "latency_ms";
    /** 发送完成 → AISF 首次回复（首 Token）时延 */
    public static final String EXTRA_FIRST_TOKEN_LATENCY_MS = "first_token_latency_ms";
    public static final String EXTRA_TASK_COMPLETED = "task_completed";
    public static final String EXTRA_TASK_ID = "task_id";
    public static final String EXTRA_CASE_ID = "case_id";
    /** 同一轮多段 TEXT 的分段内容（按 AISF 每段流式结束切分）。 */
    public static final String EXTRA_SEGMENTS = "reply_segments";
    public static final String EXTRA_SERVER_URL = "server_url";
    public static final String EXTRA_CONTROL = "control";
    public static final String EXTRA_CLARIFY_TEXT = "clarify_text";
    public static final String EXTRA_REASON = "reason";
    public static final String EXTRA_REQUEST_ID = "request_id";
    public static final String EXTRA_STEP_NAME = "step_name";
    public static final String EXTRA_CAPTURE_URI = "capture_uri";
    public static final String EXTRA_CAPTURE_OK = "capture_ok";
    public static final String EXTRA_CAPTURE_ERROR = "capture_error";

    /** 新用例开始：Device 侧轮次应从 1 重新计 */
    public static final String CONTROL_CASE_START = "case_start";

    public static final String OUTPUT_TEXT = "TEXT";
    public static final String OUTPUT_TASK_STATUS = "TASK_STATUS";
    public static final String OUTPUT_GUI_TASK = "GUI_TASK";
    public static final String OUTPUT_DIALOG = "DIALOG";
    public static final String OUTPUT_ERROR = "ERROR";

    /** TestApp 默认包名，DeviceAPP 广播目标 */
    public static final String DEFAULT_TARGET_PACKAGE = "com.example.testapp";
    /** DeviceSDK Demo 默认包名，用例控制广播目标 */
    public static final String DEFAULT_DEVICE_PACKAGE = "com.example.devicesdkdemo";

    /**
     * TestApp ContentProvider：跨应用投递回复（绕过小米 Greezer 对 cached 广播的拦截）。
     */
    public static final String PROVIDER_AUTHORITY = "com.example.testapp.testreply";
    public static final String PROVIDER_METHOD_PUBLISH = "publish";

    /**
     * Device Demo ContentProvider：TestApp 同步请求 MediaProjection 截图（绕过 Greezer）。
     */
    public static final String DEVICE_CAPTURE_AUTHORITY =
            "com.example.devicesdkdemo.gui_capture";
    public static final String PROVIDER_METHOD_GUI_CAPTURE = "gui_capture";
    /** 唤醒 Demo 进程并自拉 QAActivity 到前台（绕过应用内无 shell 的 am） */
    public static final String PROVIDER_METHOD_BRING_FRONT = "bring_front";
    /** TestApp → Demo：等同点「停止任务」/ webserver stop_test */
    public static final String PROVIDER_METHOD_STOP_AISF = "stop_aisf";
    public static final String EXTRA_BRING_FRONT_OK = "bring_front_ok";
    public static final String EXTRA_BRING_FRONT_ERROR = "bring_front_error";
    public static final String EXTRA_STOP_AISF_OK = "stop_aisf_ok";
}
