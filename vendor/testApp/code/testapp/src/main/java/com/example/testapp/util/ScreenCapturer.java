package com.example.testapp.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 截图工具
 */
public class ScreenCapturer {

    private static final String TAG = "ScreenCapturer";

    private Context context;
    private String currentTestId;
    private SimpleDateFormat dateFormat;

    public ScreenCapturer(Context context) {
        this.context = context.getApplicationContext();
        this.dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
    }

    /**
     * 设置当前测试ID
     */
    public void setCurrentTestId(String testId) {
        this.currentTestId = testId;
    }

    /**
     * 截图并保存
     */
    public boolean capture(String name) {
        try {
            // 使用 screencap 命令截图
            String fileName = (name != null ? name : dateFormat.format(new Date())) + ".png";
            File screenshotDir = getScreenshotDir();
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs();
            }

            File file = new File(screenshotDir, fileName);

            // 使用 shell 命令截图
            Process process = Runtime.getRuntime().exec("screencap -p " + file.getAbsolutePath());
            process.waitFor();

            if (file.exists()) {
                Logger.safeDebug(TAG, "截图保存: " + file.getAbsolutePath());
                return true;
            }
            return false;

        } catch (Exception e) {
            Logger.safeError(TAG, "截图失败", e);
            return false;
        }
    }

    /**
     * 获取截图目录
     */
    private File getScreenshotDir() {
        if (currentTestId != null) {
            return new File(context.getExternalFilesDir(null), "screenshots/" + currentTestId);
        }
        return new File(context.getExternalFilesDir(null), "screenshots");
    }

    /**
     * 获取截图文件
     */
    public File getScreenshotFile(String testId, String name) {
        return new File(context.getExternalFilesDir(null), "screenshots/" + testId + "/" + name + ".png");
    }

    /**
     * 获取所有截图目录
     */
    public File[] getScreenshotDirs() {
        File screenshotsRoot = new File(context.getExternalFilesDir(null), "screenshots");
        if (screenshotsRoot.exists() && screenshotsRoot.isDirectory()) {
            return screenshotsRoot.listFiles(File::isDirectory);
        }
        return new File[0];
    }
}