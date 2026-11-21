package com.example.devicesdkdemo;

import android.app.Application;
import android.util.Log;

import com.example.testreply.TestReplyConfig;
import com.example.testreply.TestReplySdk;

public class DemoApp extends Application {
    private static final String TAG = "DemoApp";

    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.install(this);
        TestReplySdk.init(this, new TestReplyConfig.Builder().build());
        // 每个自动化用例开始时由 TestApp 通知，轮次按用例从 1 重新计
        TestReplySdk.registerCaseControlListener(this, (taskId, caseId) -> {
            Log.i(TAG, "[TestReply] case_start 重置轮次 taskId=" + taskId
                    + " caseId=" + caseId);
            QAActivity.onAutomationCaseStart(caseId);
        });
        TestReplySdk.registerClarifyListener(this, (text, round) -> {
            Log.i(TAG, "[TestReply] 收到澄清发送请求 round=" + round);
            QAActivity.sendClarifyMessageFromAutomation(text);
        });
        TestReplySdk.registerForceEndTakeoverListener(this, () -> {
            Log.i(TAG, "[TestReply] 收到强制结束 GUI 接管请求");
            QAActivity.forceEndTakeoverFromAutomation();
        });
    }
}
