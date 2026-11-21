package com.example.devicesdkdemo;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.example.testreply.TestReplyContract;
import com.example.testreply.TestReplySdk;
import com.huawei.aisfsdkv2.component.androidfunc.screenshot.MediaScreenshotService;

import java.io.File;
import java.io.FileOutputStream;

/**
 * TestApp 跨进程请求 DeviceSDK 已有 MediaProjection 授权截屏（第三方 App 如爱奇艺）。
 */
public final class GuiScreenCaptureHandler {

    private static final String TAG = "GuiScreenCapture";

    private GuiScreenCaptureHandler() {
    }

    public static void handleCaptureRequest(Context context, long requestId, String stepName) {
        Context app = context.getApplicationContext();
        new Thread(() -> captureAndReply(app, requestId, stepName), "gui-mp-capture").start();
    }

    private static void captureAndReply(Context context, long requestId, String stepName) {
        Bitmap bitmap = null;
        try {
            bitmap = MediaScreenshotService.takeScreenshot();
            if (bitmap == null) {
                TestReplySdk.publishGuiScreenCaptureResult(
                        context, requestId, null, false, "media_projection_null");
                return;
            }
            File dir = new File(context.getCacheDir(), "testapp_capture");
            if (!dir.exists() && !dir.mkdirs()) {
                TestReplySdk.publishGuiScreenCaptureResult(
                        context, requestId, null, false, "mkdir_failed");
                return;
            }
            File out = new File(dir, "cap_" + requestId + ".png");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)) {
                    TestReplySdk.publishGuiScreenCaptureResult(
                            context, requestId, null, false, "compress_failed");
                    return;
                }
            }
            Uri uri = FileProvider.getUriForFile(
                    context, context.getPackageName() + ".fileprovider", out);
            context.grantUriPermission(
                    TestReplyContract.DEFAULT_TARGET_PACKAGE,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Log.i(TAG, "MediaProjection capture ok step=" + stepName + " requestId=" + requestId
                    + " bytes=" + out.length());
            TestReplySdk.publishGuiScreenCaptureResult(
                    context, requestId, uri.toString(), true, null);
        } catch (Exception e) {
            Log.e(TAG, "MediaProjection capture failed step=" + stepName, e);
            TestReplySdk.publishGuiScreenCaptureResult(
                    context, requestId, null, false, e.getMessage());
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }
}
