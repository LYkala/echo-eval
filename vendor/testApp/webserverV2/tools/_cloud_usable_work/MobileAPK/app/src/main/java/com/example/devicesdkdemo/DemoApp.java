package com.example.devicesdkdemo;

import android.app.Application;
import android.util.Log;

import com.example.testreply.TestReplyConfig;
import com.example.testreply.TestReplySdk;
import com.huawei.aisfsdkv2.message.handler.impl.helper.WebServerMirror;

/**
 * v25 DemoApp + TestReply；AISF GUI 截图经 WebServerMirror 镜像到测试 WebServer。
 */
public class DemoApp extends Application {
    private static final String TAG = "DemoApp";

    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.install(this);
        TestReplySdk.init(this, new TestReplyConfig.Builder().build());
        TestReplySdk.registerCaseControlListener(this, (taskId, caseId) -> {
            Log.i(TAG, "[TestReply] case_start taskId=" + taskId + " caseId=" + caseId);
            QAActivity.onAutomationCaseStart(caseId);
            enableAisfScreenshotMirror(taskId, caseId);
        });
        TestReplySdk.registerClarifyListener(this, (text, round) -> {
            Log.i(TAG, "[TestReply] clarify round=" + round);
            QAActivity.sendClarifyMessageFromAutomation(text);
        });
        TestReplySdk.registerForceEndTakeoverListener(this, () -> {
            Log.i(TAG, "[TestReply] force end takeover");
            QAActivity.forceEndTakeoverFromAutomation();
        });
        TestReplySdk.registerGuiScreenCaptureListener(this, (requestId, stepName) -> {
            Log.i(TAG, "[TestReply] gui capture requestId=" + requestId + " step=" + stepName);
            GuiScreenCaptureHandler.handleCaptureRequest(this, requestId, stepName);
        });
    }

    /**
     * TestApp case_start 会带上 webserver URL；启用后 ImageHelper 上传 ACM 的同时镜像到截图列表。
     */
    private static void enableAisfScreenshotMirror(String taskId, String caseId) {
        TestReplyConfig cfg = TestReplySdk.getConfig();
        String serverUrl = cfg != null ? cfg.getServerUrl() : null;
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            Log.w(TAG, "WebServerMirror 未启用：case_start 未带 serverUrl");
            WebServerMirror.configure(false, "", "", taskId, caseId);
            return;
        }
        WebServerMirror.configure(true, serverUrl.trim(), "", taskId, caseId);
        Log.i(TAG, "WebServerMirror 已启用，AISF GUI 截图将镜像到 " + serverUrl
                + " taskId=" + taskId + " caseId=" + caseId);
    }
}
