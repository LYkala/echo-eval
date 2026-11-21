package com.example.devicesdkdemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.huawei.aisfsdkv2.component.androidfunc.accessibility.impl.AccessibilityManagerImpl;

/**
 * 文本输入广播接收器
 * <p>用于接收 ADB 广播命令并输入文本到当前焦点的输入框</p>
 *
 * <p>使用方法:</p>
 * <pre>
 * adb shell "am broadcast -a com.example.devicesdkdemo.ACTION_INPUT_TEXT --es text '帮我打车到虹桥高铁站'"
 * </pre>
 */
public class TextInputReceiver extends BroadcastReceiver {
    private static final String TAG = "TextInputReceiver";
    public static final String ACTION_INPUT_TEXT = "com.example.devicesdkdemo.ACTION_INPUT_TEXT";
    public static final String ACTION_SEND_TEXT = "com.example.devicesdkdemo.ACTION_SEND_TEXT";
    public static final String EXTRA_TEXT = "text";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "onReceive: action=" + action);
        if (action == null) {
            return;
        }
        String text = intent.getStringExtra(EXTRA_TEXT);
        if (text == null || text.isEmpty()) {
            Log.w(TAG, "onReceive: text is empty");
            return;
        }
        Log.i(TAG, "onReceive: text=" + text);
        // 使用无障碍服务输入文本
        AccessibilityManagerImpl acc = AccessibilityManagerImpl.getInstance();
        if (acc != null) {
            // 在主线程执行
            new Handler(Looper.getMainLooper()).post(() -> {
                boolean success = acc.inputText(0, 0, text);
                Log.i(TAG, "inputText result: " + success);
                // 如果是 SEND_TEXT 动作，输入后自动发送
                if (success && ACTION_SEND_TEXT.equals(action)) {
                    // 查找并发送
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        boolean enterSuccess = acc.autoPerformEnter();
                        Log.i(TAG, "autoPerformEnter result: " + enterSuccess);
                    }, 500);
                }
            });
        } else {
            Log.e(TAG, "AccessibilityManagerImpl is null, service not running");
        }
    }
}
