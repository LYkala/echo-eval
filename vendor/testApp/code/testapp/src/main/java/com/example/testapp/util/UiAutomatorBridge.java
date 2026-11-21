package com.example.testapp.util;

import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

/**
 * UiAutomator 桥接器
 */
public class UiAutomatorBridge {

    private static final String TAG = "UiAutomatorBridge";

    private Context context;
    private DisplayMetrics displayMetrics;

    public UiAutomatorBridge(Context context) {
        this.context = context.getApplicationContext();
        Logger logger = Logger.getInstance(context);
        logger.info(TAG, "UiAutomatorBridge 初始化");
        initDisplayMetrics();
    }

    private void initDisplayMetrics() {
        Logger logger = Logger.getInstance(context);

        try {
            displayMetrics = new DisplayMetrics();
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

            if (windowManager == null) {
                logger.error(TAG, "WindowManager is null", new Exception("WindowManager null"));
                // 使用默认值
                displayMetrics.widthPixels = 1080;
                displayMetrics.heightPixels = 1920;
                displayMetrics.densityDpi = 480;
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 使用新的API
                android.view.Display display = null;
                try {
                    display = context.getDisplay();
                } catch (Exception e) {
                    logger.warn(TAG, "getDisplay() failed: " + e.getMessage());
                }

                if (display != null) {
                    display.getRealMetrics(displayMetrics);
                } else {
                    // 备选方案
                    windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
                }
            } else {
                // Android 11以下
                windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
            }

            logger.info(TAG, "屏幕尺寸: " + displayMetrics.widthPixels + "x" + displayMetrics.heightPixels);

        } catch (Exception e) {
            logger.error(TAG, "initDisplayMetrics 失败", e);
            // 使用默认值
            displayMetrics = new DisplayMetrics();
            displayMetrics.widthPixels = 1080;
            displayMetrics.heightPixels = 1920;
            displayMetrics.densityDpi = 480;
        }
    }

    /**
     * 获取屏幕宽度
     */
    public int getScreenWidth() {
        return displayMetrics.widthPixels;
    }

    /**
     * 获取屏幕高度
     */
    public int getScreenHeight() {
        return displayMetrics.heightPixels;
    }

    /**
     * 获取屏幕中心点
     */
    public Point getScreenCenter() {
        return new Point(getScreenWidth() / 2, getScreenHeight() / 2);
    }

    /**
     * 点击坐标
     */
    public boolean click(int x, int y) {
        Logger logger = Logger.getInstance(context);
        logger.debug(TAG, "click: (" + x + ", " + y + ")");

        try {
            Process process = Runtime.getRuntime().exec("input tap " + x + " " + y);
            process.waitFor();
            int exitValue = process.exitValue();
            logger.gesture("click", x + "," + y, exitValue == 0);
            return exitValue == 0;
        } catch (Exception e) {
            logger.error(TAG, "click 失败", e);
            return false;
        }
    }

    /**
     * 滑动
     */
    public boolean swipe(int startX, int startY, int endX, int endY, int duration) {
        Logger logger = Logger.getInstance(context);
        logger.debug(TAG, "swipe: (" + startX + "," + startY + ") -> (" + endX + "," + endY + ")");

        try {
            Process process = Runtime.getRuntime().exec(
                    "input swipe " + startX + " " + startY + " " + endX + " " + endY + " " + duration);
            process.waitFor();
            int exitValue = process.exitValue();
            logger.gesture("swipe", startX + "," + startY + "->" + endX + "," + endY, exitValue == 0);
            return exitValue == 0;
        } catch (Exception e) {
            logger.error(TAG, "swipe 失败", e);
            return false;
        }
    }

    /**
     * 按键
     */
    public boolean pressKey(int keyCode) {
        Logger logger = Logger.getInstance(context);
        logger.debug(TAG, "pressKey: " + keyCode);

        try {
            Process process = Runtime.getRuntime().exec("input keyevent " + keyCode);
            process.waitFor();
            int exitValue = process.exitValue();
            return exitValue == 0;
        } catch (Exception e) {
            logger.error(TAG, "pressKey 失败", e);
            return false;
        }
    }

    /**
     * 返回键
     */
    public boolean pressBack() {
        return pressKey(4); // KEYCODE_BACK
    }

    /**
     * Home键
     */
    public boolean pressHome() {
        return pressKey(3); // KEYCODE_HOME
    }

    /**
     * 输入文本
     */
    public boolean inputText(String text) {
        Logger logger = Logger.getInstance(context);
        logger.debug(TAG, "inputText: " + text);

        try {
            Process process = Runtime.getRuntime().exec("input text " + text);
            process.waitFor();
            int exitValue = process.exitValue();
            return exitValue == 0;
        } catch (Exception e) {
            logger.error(TAG, "inputText 失败", e);
            return false;
        }
    }

    /**
     * 获取显示参数
     */
    public DisplayMetrics getDisplayMetrics() {
        return displayMetrics;
    }
}