package com.example.testapp.service;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.testapp.R;
import com.example.testapp.util.Logger;

/**
 * 悬浮窗服务
 */
public class FloatingWindowService extends Service {

    private static final String TAG = "FloatingWindowService";
    private WindowManager windowManager;
    private View floatingView;
    private boolean isShowing = false;

    private TextView tvTestId;
    private TextView tvStepProgress;
    private ProgressBar progressBar;
    private Button btnStop;
    private Button btnMinimize;

    private android.content.BroadcastReceiver updateReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.testapp.UPDATE_FLOATING_WINDOW".equals(intent.getAction())) {
                int currentStep = intent.getIntExtra("currentStep", 0);
                int total = intent.getIntExtra("totalSteps", 0);
                updateProgress(currentStep, total);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.safeInfo(TAG, "FloatingWindowService onCreate");

        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_window, null);
            initViews();

            // 注册更新接收器
            IntentFilter filter = new IntentFilter("com.example.testapp.UPDATE_FLOATING_WINDOW");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(updateReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(updateReceiver, filter);
            }
        } catch (Exception e) {
            Logger.safeError(TAG, "onCreate失败", e);
        }
    }

    private void initViews() {
        if (floatingView == null) return;

        tvTestId = floatingView.findViewById(R.id.tvTestId);
        tvStepProgress = floatingView.findViewById(R.id.tvStepProgress);
        progressBar = floatingView.findViewById(R.id.progressBar);
        btnStop = floatingView.findViewById(R.id.btnStop);
        btnMinimize = floatingView.findViewById(R.id.btnMinimize);

        if (btnStop != null) {
            btnStop.setOnClickListener(v -> {
                Logger.safeInfo(TAG, "停止按钮被点击");
                sendStopBroadcast();
                hideFloatingWindow();
                stopSelf();
            });
        }

        if (btnMinimize != null) {
            btnMinimize.setOnClickListener(v -> hideFloatingWindow());
        }
    }

    private void sendStopBroadcast() {
        Logger.safeInfo(TAG, "发送停止测试广播");
        try {
            Intent intent = new Intent(TestExecuteService.ACTION_STOP_TEST);
            intent.setPackage(getPackageName());
            sendBroadcast(intent);

            // 同时发送另一个action作为备用
            Intent intent2 = new Intent("com.example.testapp.STOP");
            intent2.setPackage(getPackageName());
            sendBroadcast(intent2);

            Logger.safeInfo(TAG, "停止广播已发送");
        } catch (Exception e) {
            Logger.safeError(TAG, "发送停止广播失败", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.safeDebug(TAG, "onStartCommand");

        if (intent != null) {
            String action = intent.getAction();
            Logger.safeDebug(TAG, "收到action: " + action);

            if ("STOP".equals(action)) {
                hideFloatingWindow();
                stopSelf();
                return START_NOT_STICKY;
            }

            if ("START".equals(action)) {
                String testId = intent.getStringExtra("testId");
                int totalSteps = intent.getIntExtra("totalSteps", 0);

                if (tvTestId != null) {
                    tvTestId.setText("测试: " + testId);
                }
                if (tvStepProgress != null) {
                    tvStepProgress.setText("步骤: 0/" + totalSteps);
                }
                if (progressBar != null) {
                    progressBar.setMax(totalSteps);
                    progressBar.setProgress(0);
                }

                showFloatingWindow();
            }
        }

        return START_NOT_STICKY;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showFloatingWindow() {
        if (isShowing) {
            Logger.safeDebug(TAG, "悬浮窗已显示");
            return;
        }

        // 悬浮窗权限校验已注释
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
        //     Logger.safeError(TAG, "没有悬浮窗权限");
        //     return;
        // }

        try {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                            WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );

            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.x = 50;
            params.y = 200;

            if (windowManager != null) {
                windowManager.addView(floatingView, params);
                isShowing = true;
                Logger.safeInfo(TAG, "悬浮窗显示成功");
            }
        } catch (Exception e) {
            Logger.safeError(TAG, "显示悬浮窗失败", e);
        }
    }

    private void hideFloatingWindow() {
        Logger.safeDebug(TAG, "hideFloatingWindow");
        if (isShowing && floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
                Logger.safeInfo(TAG, "悬浮窗已隐藏");
            } catch (Exception e) {
                Logger.safeError(TAG, "移除悬浮窗失败", e);
            }
            isShowing = false;
        }
    }

    private void updateProgress(int current, int total) {
        if (tvStepProgress != null) {
            tvStepProgress.setText("步骤: " + current + "/" + total);
        }
        if (progressBar != null) {
            progressBar.setProgress(current);
        }
    }

    @Override
    public void onDestroy() {
        Logger.safeInfo(TAG, "FloatingWindowService onDestroy");
        hideFloatingWindow();
        try {
            unregisterReceiver(updateReceiver);
        } catch (Exception e) {
            Logger.safeError(TAG, "unregisterReceiver失败", e);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}