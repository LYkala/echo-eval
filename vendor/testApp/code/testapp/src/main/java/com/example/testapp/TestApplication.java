package com.example.testapp;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Environment;

import com.example.testapp.util.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 应用程序类
 * 用于全局异常捕获和日志初始化
 */
public class TestApplication extends Application {

    private static final String TAG = "TestApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        // 初始化Logger（必须最先执行）
        Logger logger = Logger.getInstance(this);
        logger.info(TAG, "=== TestApplication onCreate ===");
        logger.info(TAG, "=== 应用启动 ===");
        logger.info(TAG, "应用版本: " + getAppVersion());
        logger.info(TAG, "设备: " + Build.MANUFACTURER + " " + Build.MODEL);
        logger.info(TAG, "Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");

        // 设置全局异常捕获
        setupCrashHandler();
    }

    private void setupCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                Logger.safeError(TAG, "=== 未捕获的异常 ===", ex);

                // 保存崩溃日志到文件
                saveCrashLog(ex);

                // 保存到Logger
                try {
                    Logger logger = Logger.getInstance(getApplicationContext());
                    logger.error(TAG, "=== 应用崩溃 ===", ex);
                    logger.exportToDownload("crash_" + System.currentTimeMillis());
                } catch (Exception e) {
                    Logger.safeError(TAG, "保存崩溃日志失败", e);
                }

                // 调用系统默认处理
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
            }
        });
    }

    private void saveCrashLog(Throwable ex) {
        try {
            File crashDir = new File(getExternalFilesDir(null), "crash");
            if (!crashDir.exists()) {
                crashDir.mkdirs();
            }

            String fileName = "crash_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".log";
            File crashFile = new File(crashDir, fileName);

            FileOutputStream fos = new FileOutputStream(crashFile);
            StringBuilder sb = new StringBuilder();

            sb.append("=== TestApp 崩溃日志 ===\n");
            sb.append("时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())).append("\n");
            sb.append("设备: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
            sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
            sb.append("应用版本: ").append(getAppVersion()).append("\n");
            sb.append("\n=== 异常信息 ===\n");

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            sb.append(sw.toString());

            fos.write(sb.toString().getBytes("UTF-8"));
            fos.flush();
            fos.close();

            Logger.safeInfo(TAG, "崩溃日志已保存: " + crashFile.getAbsolutePath());

        } catch (Exception e) {
            Logger.safeError(TAG, "保存崩溃日志失败", e);
        }
    }

    private String getAppVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }
}