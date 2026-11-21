package com.example.devicesdkdemo;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全局崩溃捕获处理器
 * 将崩溃信息写入本地文件，方便通过adb读取
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    private static final String CRASH_FILE = "crash.log";
    private Thread.UncaughtExceptionHandler defaultHandler;
    private Context context;

    private CrashHandler(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    public static void install(Context context) {
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(context));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        // 写入崩溃日志到文件
        try {
            File file = new File(context.getFilesDir(), CRASH_FILE);
            FileWriter writer = new FileWriter(file, true);
            PrintWriter pw = new PrintWriter(writer);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
            pw.println("========== CRASH " + sdf.format(new Date()) + " ==========");
            pw.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
            pw.println("Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
            pw.println("Thread: " + thread.getName());
            ex.printStackTrace(pw);
            pw.println();
            pw.flush();
            pw.close();
        } catch (Exception e) {
            Log.e(TAG, "Failed to write crash log", e);
        }

        // 交给系统默认处理
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, ex);
        }
    }

    /**
     * 读取崩溃日志文件内容
     */
    public static String readCrashLog(Context context) {
        try {
            File file = new File(context.getFilesDir(), CRASH_FILE);
            if (!file.exists()) return null;
            byte[] bytes = new byte[(int) file.length()];
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            fis.read(bytes);
            fis.close();
            return new String(bytes, "UTF-8");
        } catch (Exception e) {
            return "Failed to read crash log: " + e.getMessage();
        }
    }

    /**
     * 清除崩溃日志
     */
    public static void clearCrashLog(Context context) {
        File file = new File(context.getFilesDir(), CRASH_FILE);
        if (file.exists()) file.delete();
    }
}
