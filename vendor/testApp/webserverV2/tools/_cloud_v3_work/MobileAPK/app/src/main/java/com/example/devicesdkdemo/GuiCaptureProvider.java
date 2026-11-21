package com.example.devicesdkdemo;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * ContentProvider：bring_front 唤醒 Demo 并拉起 QAActivity（聊天框，非 Config）。
 * 对齐 TestApp → GuiCaptureProvider 的 Binder 拉回路径（不依赖 ADB）。
 */
public class GuiCaptureProvider extends ContentProvider {

    public static final String AUTHORITY = "com.example.devicesdkdemo.gui_capture";
    public static final String METHOD_BRING_FRONT = "bring_front";
    public static final String METHOD_STOP_AISF = "stop_aisf";
    public static final String EXTRA_OK = "bring_front_ok";
    public static final String EXTRA_ERROR = "bring_front_error";
    public static final String EXTRA_STOP_OK = "stop_aisf_ok";

    private static final String TAG = "GuiCaptureProvider";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        if (METHOD_BRING_FRONT.equals(method)) {
            return bringFront();
        }
        if (METHOD_STOP_AISF.equals(method)) {
            return stopAisf(extras);
        }
        return super.call(method, arg, extras);
    }

    private Bundle stopAisf(@Nullable Bundle extras) {
        Context context = getContext();
        Bundle out = new Bundle();
        if (context == null) {
            out.putBoolean(EXTRA_STOP_OK, false);
            out.putString(EXTRA_ERROR, "no_context");
            return out;
        }
        String reason = extras != null ? extras.getString("reason") : null;
        if (reason == null || reason.isEmpty()) {
            reason = extras != null
                    ? extras.getString(com.example.testreply.TestReplyContract.EXTRA_REASON)
                    : null;
        }
        if (reason == null || reason.isEmpty()) {
            reason = "webserver_stop";
        }
        final String stopReason = reason;
        Log.i(TAG, "stop_aisf via provider reason=" + stopReason);
        try {
            QAActivity.stopAisfFromWebServer(context.getApplicationContext(), stopReason);
            out.putBoolean(EXTRA_STOP_OK, true);
        } catch (Throwable t) {
            Log.w(TAG, "stop_aisf failed: " + t.getMessage());
            out.putBoolean(EXTRA_STOP_OK, false);
            out.putString(EXTRA_ERROR, t.getMessage() != null ? t.getMessage() : "stop_failed");
        }
        return out;
    }

    private Bundle bringFront() {
        Context context = getContext();
        if (context == null) {
            Bundle b = new Bundle();
            b.putBoolean(EXTRA_OK, false);
            b.putString(EXTRA_ERROR, "no_context");
            return b;
        }
        final Context app = context.getApplicationContext();
        Runnable start = () -> {
            try {
                Intent intent = new Intent(app, QAActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                app.startActivity(intent);
                Log.i(TAG, "bring_front started QAActivity");
            } catch (Exception e) {
                Log.e(TAG, "bring_front startActivity failed", e);
            }
        };
        try {
            start.run();
            new Handler(Looper.getMainLooper()).post(start);
            Bundle ok = new Bundle();
            ok.putBoolean(EXTRA_OK, true);
            return ok;
        } catch (Exception e) {
            Bundle b = new Bundle();
            b.putBoolean(EXTRA_OK, false);
            b.putString(EXTRA_ERROR,
                    e.getMessage() != null ? e.getMessage() : "bring_front_failed");
            return b;
        }
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
