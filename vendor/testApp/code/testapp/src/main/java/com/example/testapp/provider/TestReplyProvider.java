package com.example.testapp.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.testapp.service.TestExecuteService;
import com.example.testreply.TestReplyContract;
import com.example.testreply.TestReplyEvent;

/**
 * 跨应用投递 TestReply：小米 Greezer 会丢掉打到 cached 进程的广播，
 * ContentResolver.call 不受该限制，仍走 TestExecuteService → WebServer。
 */
public class TestReplyProvider extends ContentProvider {
    private static final String TAG = "TestReplyProvider";
    public static final String METHOD_GUI_STEP_LATENCY = "publish_gui_step_latency";
    public static final String METHOD_GUI_POLL_AFTER_CONTROL = "publish_gui_poll_after_control";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        if (METHOD_GUI_POLL_AFTER_CONTROL.equals(method)) {
            String cmdName = extras != null
                    ? extras.getString(TestReplyContract.EXTRA_GUI_CMD_NAME) : null;
            Log.i(TAG, "gui_poll after control via provider cmd=" + cmdName);
            TestExecuteService.requestGuiPollAfterControl(cmdName);
            Bundle ok = new Bundle();
            ok.putBoolean("ok", true);
            ok.putBoolean("running", TestExecuteService.isRunning());
            return ok;
        }
        if (METHOD_GUI_STEP_LATENCY.equals(method)) {
            String json = extras != null
                    ? extras.getString(TestReplyContract.EXTRA_GUI_STEP_LATENCY_JSON) : null;
            int round = extras != null
                    ? extras.getInt(TestReplyContract.EXTRA_GUI_STEP_LATENCY_ROUND, 0) : 0;
            String taskId = extras != null
                    ? extras.getString(TestReplyContract.EXTRA_TASK_ID) : null;
            String caseId = extras != null
                    ? extras.getString(TestReplyContract.EXTRA_CASE_ID) : null;
            Log.i(TAG, "gui_step_latency via provider round=" + round
                    + " bytes=" + (json != null ? json.length() : 0));
            TestExecuteService.dispatchExternalGuiStepLatency(taskId, caseId, round, json);
            Bundle ok = new Bundle();
            ok.putBoolean("ok", true);
            return ok;
        }
        if (!TestReplyContract.PROVIDER_METHOD_PUBLISH.equals(method)) {
            return null;
        }
        TestReplyEvent event = TestReplyEvent.fromBundle(extras);
        if (event == null) {
            Bundle fail = new Bundle();
            fail.putBoolean("ok", false);
            fail.putString("error", "null_event");
            return fail;
        }
        Log.i(TAG, "publish via provider round=" + event.getRound()
                + " type=" + event.getOutputType()
                + " len=" + (event.getReplyText() != null ? event.getReplyText().length() : 0));
        TestExecuteService.dispatchExternalReply(event);
        Bundle ok = new Bundle();
        ok.putBoolean("ok", true);
        return ok;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        return 0;
    }
}
