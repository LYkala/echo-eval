package com.example.testapp.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.testapp.service.TestExecuteService;
import com.example.testreply.TestReplyContract;
import com.example.testreply.TestReplyEvent;

/**
 * 清单导出的 TestReply 接收器：避免仅依赖动态注册时，跨应用广播在部分机型上丢失。
 * 仍由 TestExecuteService 走原 handleDeviceReply → WebServer 上报链路。
 */
public class TestReplyBridgeReceiver extends BroadcastReceiver {
    private static final String TAG = "TestReplyBridge";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        if (TestReplyContract.ACTION_GUI_POLL_AFTER_CONTROL.equals(intent.getAction())) {
            String cmdName = intent.getStringExtra(TestReplyContract.EXTRA_GUI_CMD_NAME);
            Log.i(TAG, "recv gui_poll after control cmd=" + cmdName);
            TestExecuteService.requestGuiPollAfterControl(cmdName);
            return;
        }
        if (TestReplyContract.ACTION_GUI_STEP_LATENCY.equals(intent.getAction())) {
            String json = intent.getStringExtra(TestReplyContract.EXTRA_GUI_STEP_LATENCY_JSON);
            int round = intent.getIntExtra(TestReplyContract.EXTRA_GUI_STEP_LATENCY_ROUND, 0);
            String taskId = intent.getStringExtra(TestReplyContract.EXTRA_TASK_ID);
            String caseId = intent.getStringExtra(TestReplyContract.EXTRA_CASE_ID);
            Log.i(TAG, "recv gui_step_latency round=" + round
                    + " bytes=" + (json != null ? json.length() : 0));
            TestExecuteService.dispatchExternalGuiStepLatency(taskId, caseId, round, json);
            return;
        }
        if (!TestReplyContract.ACTION_REPLY.equals(intent.getAction())) {
            return;
        }
        TestReplyEvent event = TestReplyEvent.fromIntent(intent);
        if (event == null) {
            return;
        }
        Log.i(TAG, "recv reply round=" + event.getRound()
                + " type=" + event.getOutputType()
                + " final=" + event.isFinalReply()
                + " len=" + (event.getReplyText() != null ? event.getReplyText().length() : 0));
        TestExecuteService.dispatchExternalReply(event);
    }
}
