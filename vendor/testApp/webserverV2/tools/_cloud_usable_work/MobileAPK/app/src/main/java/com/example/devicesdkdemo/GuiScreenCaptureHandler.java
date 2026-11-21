package com.example.devicesdkdemo;

import android.content.Context;
import android.util.Log;

import com.example.testreply.TestReplySdk;

/**
 * MediaProjection 已禁用；收到截图请求时直接回失败，由 TestApp 走无障碍/screencap。
 */
public final class GuiScreenCaptureHandler {

    private static final String TAG = "GuiScreenCapture";

    private GuiScreenCaptureHandler() {
    }

    public static void handleCaptureRequest(Context context, long requestId, String stepName) {
        Log.i(TAG, "media capture disabled step=" + stepName + " requestId=" + requestId);
        if (context == null) {
            return;
        }
        TestReplySdk.publishGuiScreenCaptureResult(
                context.getApplicationContext(), requestId, null, false, "media_disabled");
    }
}
