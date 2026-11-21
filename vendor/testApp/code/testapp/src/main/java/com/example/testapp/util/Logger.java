package com.example.testapp.util;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 增强版日志记录器
 * 支持多级别日志、自动写入文件、导出到下载目录
 */
public class Logger {

    private static final String TAG = "TestApp";
    private static Logger instance;

    private Context context;
    private StringBuilder logBuilder;
    private SimpleDateFormat dateFormat;
    private SimpleDateFormat fileDateFormat;
    private ExecutorService executor;
    private File currentLogFile;
    private String currentSessionId;

    // 日志级别
    public static final int DEBUG = 0;
    public static final int INFO = 1;
    public static final int WARN = 2;
    public static final int ERROR = 3;

    private int minLogLevel = DEBUG;
    private boolean autoWriteToFile = true;

    private Logger(Context context) {
        this.context = context.getApplicationContext();
        this.logBuilder = new StringBuilder();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        this.fileDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        this.executor = Executors.newSingleThreadExecutor();

        // 创建应用日志目录
        ensureLogDir();

        // 记录应用启动日志
        log(INFO, "Logger", "=== TestApp Logger 初始化 ===");
        log(INFO, "Logger", "设备信息: " + getDeviceInfo());
        log(INFO, "Logger", "应用版本: " + getAppVersion());
        log(INFO, "Logger", "日志目录: " + getLogDir().getAbsolutePath());
    }

    public static synchronized Logger getInstance(Context context) {
        if (instance == null && context != null) {
            instance = new Logger(context);
        }
        return instance;
    }

    /**
     * 获取Logger实例（需要在之前初始化过）
     * 如果未初始化，返回null
     */
    public static synchronized Logger getInstance() {
        return instance;
    }

    /**
     * 安全的调试日志 - 如果Logger未初始化，输出到Logcat
     */
    public static void safeDebug(String tag, String message) {
        if (instance != null) {
            instance.debug(tag, message);
        } else {
            Log.d(tag, message);
        }
    }

    /**
     * 安全的信息日志 - 如果Logger未初始化，输出到Logcat
     */
    public static void safeInfo(String tag, String message) {
        if (instance != null) {
            instance.info(tag, message);
        } else {
            Log.i(tag, message);
        }
    }

    /**
     * 安全的警告日志 - 如果Logger未初始化，输出到Logcat
     */
    public static void safeWarn(String tag, String message) {
        if (instance != null) {
            instance.warn(tag, message);
        } else {
            Log.w(tag, message);
        }
    }

    /**
     * 安全的错误日志 - 如果Logger未初始化，输出到Logcat
     */
    public static void safeError(String tag, String message) {
        if (instance != null) {
            instance.error(tag, message);
        } else {
            Log.e(tag, message);
        }
    }

    /**
     * 安全的错误日志（带异常） - 如果Logger未初始化，输出到Logcat
     */
    public static void safeError(String tag, String message, Throwable e) {
        if (instance != null) {
            instance.error(tag, message, e);
        } else {
            Log.e(tag, message, e);
        }
    }

    /**
     * 开始新的日志会话
     */
    public void startSession(String sessionId) {
        this.currentSessionId = sessionId;
        log(INFO, "Session", "=== 开始新会话: " + sessionId + " ===");

        // 创建会话日志文件
        String fileName = sessionId + "_" + fileDateFormat.format(new Date()) + ".log";
        currentLogFile = new File(getLogDir(), fileName);

        // 写入会话头信息
        writeToFile("=== TestApp 日志会话 ===\n");
        writeToFile("会话ID: " + sessionId + "\n");
        writeToFile("开始时间: " + dateFormat.format(new Date()) + "\n");
        writeToFile("设备: " + getDeviceInfo() + "\n");
        writeToFile("应用版本: " + getAppVersion() + "\n");
        writeToFile("========================================\n\n");
    }

    /**
     * 结束当前会话
     */
    public void endSession() {
        log(INFO, "Session", "=== 会话结束: " + currentSessionId + " ===");
        if (currentLogFile != null) {
            writeToFile("\n========================================\n");
            writeToFile("会话结束时间: " + dateFormat.format(new Date()) + "\n");
        }
        currentSessionId = null;
    }

    /**
     * 记录日志 (默认INFO级别)
     */
    public void log(String message) {
        log(INFO, TAG, message);
    }

    /**
     * 记录带标签的日志
     */
    public void log(String tag, String message) {
        log(INFO, tag, message);
    }

    /**
     * 仅写入会话文件/内存，不重复打 Logcat
     */
    public static void appendFileLog(int level, String tag, String message) {
        if (instance != null) {
            instance.appendToSession(level, tag, message);
        }
    }

    private void appendToSession(int level, String tag, String message) {
        if (level < minLogLevel) {
            return;
        }
        String timestamp = dateFormat.format(new Date());
        String levelStr = getLevelString(level);
        String logLine = String.format("[%s] [%s] [%s] %s", timestamp, levelStr, tag, message);
        logBuilder.append(logLine).append("\n");
        if (autoWriteToFile) {
            writeToFile(logLine + "\n");
        }
        if (level >= INFO) {
            LiveLogStreamer.append(logLine);
        }
    }

    /**
     * 记录指定级别的日志
     */
    public void log(int level, String tag, String message) {
        if (level < minLogLevel) {
            return;
        }

        String timestamp = dateFormat.format(new Date());
        String levelStr = getLevelString(level);
        String logLine = String.format("[%s] [%s] [%s] %s", timestamp, levelStr, tag, message);

        // 输出到Logcat
        switch (level) {
            case DEBUG:
                Log.d(TAG, logLine);
                break;
            case INFO:
                Log.i(TAG, logLine);
                break;
            case WARN:
                Log.w(TAG, logLine);
                break;
            case ERROR:
                Log.e(TAG, logLine);
                break;
        }

        // 保存到内存
        logBuilder.append(logLine).append("\n");

        // 自动写入文件
        if (autoWriteToFile) {
            writeToFile(logLine + "\n");
        }

        // 实时推送到 WebServer（INFO 及以上，避免 DEBUG 刷屏）
        if (level >= INFO) {
            LiveLogStreamer.append(logLine);
        }
    }

    /**
     * 记录调试日志
     */
    public void debug(String tag, String message) {
        log(DEBUG, tag, message);
    }

    /**
     * 记录信息日志
     */
    public void info(String tag, String message) {
        log(INFO, tag, message);
    }

    /**
     * 记录警告日志
     */
    public void warn(String tag, String message) {
        log(WARN, tag, message);
    }

    /**
     * 记录错误日志
     */
    public void error(String tag, String message) {
        log(ERROR, tag, message);
    }

    /**
     * 记录错误日志
     */
    public void error(String message, Throwable e) {
        String stackTrace = getStackTraceString(e);
        log(ERROR, TAG, message + (stackTrace.isEmpty() ? "" : "\n" + stackTrace));
    }

    /**
     * 记录错误日志（带标签）
     */
    public void error(String tag, String message, Throwable e) {
        String stackTrace = getStackTraceString(e);
        log(ERROR, tag, message + "\n" + stackTrace);
    }

    /**
     * 记录方法调用（用于调试）
     */
    public void method(String tag, String methodName) {
        debug(tag, "-> " + methodName + "()");
    }

    /**
     * 记录方法调用（带参数）
     */
    public void method(String tag, String methodName, String params) {
        debug(tag, "-> " + methodName + "(" + params + ")");
    }

    /**
     * 记录无障碍事件
     */
    public void accessibilityEvent(String eventType, String description) {
        debug("Accessibility", eventType + ": " + description);
    }

    /**
     * 记录节点查找
     */
    public void nodeSearch(String strategy, String value, boolean found) {
        debug("NodeSearch", String.format("strategy=%s, value=%s, found=%b", strategy, value, found));
    }

    /**
     * 记录手势操作
     */
    public void gesture(String gestureType, String params, boolean success) {
        info("Gesture", String.format("%s(%s) -> %s", gestureType, params, success ? "成功" : "失败"));
    }

    /**
     * 获取日志内容
     */
    public String getLogContent() {
        return logBuilder.toString();
    }

    /**
     * 清空日志
     */
    public void clear() {
        logBuilder = new StringBuilder();
        log(INFO, TAG, "日志已清空");
    }

    /**
     * 获取日志目录
     */
    public File getLogDir() {
        return new File(context.getExternalFilesDir(null), "logs");
    }

    /**
     * 获取下载目录中的日志导出目录
     */
    public File getExportDir() {
        File exportDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "TestApp_Logs");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }
        return exportDir;
    }

    /**
     * 确保日志目录存在
     */
    private void ensureLogDir() {
        File logDir = getLogDir();
        if (!logDir.exists()) {
            boolean created = logDir.mkdirs();
            Log.d(TAG, "创建日志目录: " + logDir.getAbsolutePath() + ", 结果: " + created);
        }
    }

    /**
     * 写入日志到文件（异步）
     */
    private void writeToFile(String content) {
        executor.execute(() -> {
            try {
                File logFile = currentLogFile != null ? currentLogFile : new File(getLogDir(), "app.log");
                FileOutputStream fos = new FileOutputStream(logFile, true);
                fos.write(content.getBytes("UTF-8"));
                fos.flush();
                fos.close();
            } catch (IOException e) {
                Log.e(TAG, "写入日志文件失败: " + e.getMessage());
            }
        });
    }

    /**
     * 保存日志到文件
     */
    public File saveToFile(String testId) {
        try {
            File logDir = getLogDir();
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            String fileName = testId + "_" + fileDateFormat.format(new Date()) + ".log";
            File logFile = new File(logDir, fileName);
            FileOutputStream fos = new FileOutputStream(logFile);
            fos.write(logBuilder.toString().getBytes("UTF-8"));
            fos.flush();
            fos.close();

            log(INFO, TAG, "日志已保存: " + logFile.getAbsolutePath());
            return logFile;

        } catch (Exception e) {
            Log.e(TAG, "保存日志失败", e);
            return null;
        }
    }

    /**
     * 导出日志到下载目录（用户可见）
     */
    public File exportToDownload(String testId) {
        try {
            File exportDir = getExportDir();
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }

            String fileName = testId + "_" + fileDateFormat.format(new Date()) + ".log";
            File exportFile = new File(exportDir, fileName);

            FileOutputStream fos = new FileOutputStream(exportFile);
            fos.write(logBuilder.toString().getBytes("UTF-8"));
            fos.flush();
            fos.close();

            log(INFO, TAG, "日志已导出到: " + exportFile.getAbsolutePath());
            return exportFile;

        } catch (Exception e) {
            Log.e(TAG, "导出日志失败", e);
            return null;
        }
    }

    /**
     * 导出所有日志到下载目录
     */
    public File exportAllLogs() {
        try {
            File exportDir = getExportDir();
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }

            String fileName = "all_logs_" + fileDateFormat.format(new Date()) + ".log";
            File exportFile = new File(exportDir, fileName);

            // 合并所有日志文件
            FileOutputStream fos = new FileOutputStream(exportFile);

            File logDir = getLogDir();
            File[] logFiles = logDir.listFiles((dir, name) -> name.endsWith(".log"));

            if (logFiles != null && logFiles.length > 0) {
                for (File logFile : logFiles) {
                    fos.write(("\n\n===== " + logFile.getName() + " =====\n").getBytes("UTF-8"));
                    java.io.FileInputStream fis = new java.io.FileInputStream(logFile);
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fis.close();
                }
            } else {
                // 如果没有日志文件，写入当前内存中的日志
                fos.write(logBuilder.toString().getBytes("UTF-8"));
            }

            fos.flush();
            fos.close();

            Log.i(TAG, "所有日志已导出到: " + exportFile.getAbsolutePath());
            return exportFile;

        } catch (Exception e) {
            Log.e(TAG, "导出所有日志失败", e);
            return null;
        }
    }

    /**
     * 获取日志文件
     */
    public File getLogFile(String testId) {
        File logDir = getLogDir();
        File[] files = logDir.listFiles((dir, name) -> name.startsWith(testId) && name.endsWith(".log"));
        if (files != null && files.length > 0) {
            return files[0];
        }
        return new File(logDir, testId + ".log");
    }

    /**
     * 获取所有日志文件
     */
    public File[] getAllLogFiles() {
        File logDir = getLogDir();
        return logDir.listFiles((dir, name) -> name.endsWith(".log"));
    }

    /**
     * 清理旧日志（保留最近N天的日志）
     */
    public void cleanOldLogs(int daysToKeep) {
        long cutoffTime = System.currentTimeMillis() - (daysToKeep * 24L * 60 * 60 * 1000);
        File logDir = getLogDir();
        File[] files = logDir.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.lastModified() < cutoffTime) {
                    boolean deleted = file.delete();
                    Log.d(TAG, "删除旧日志: " + file.getName() + ", 结果: " + deleted);
                }
            }
        }
    }

    /**
     * 获取日志级别字符串
     */
    private String getLevelString(int level) {
        switch (level) {
            case DEBUG: return "DEBUG";
            case INFO:  return "INFO";
            case WARN:  return "WARN";
            case ERROR: return "ERROR";
            default: return "UNKNOWN";
        }
    }

    /**
     * 获取堆栈跟踪字符串
     */
    private String getStackTraceString(Throwable e) {
        if (e == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * 获取设备信息
     */
    private String getDeviceInfo() {
        return String.format("%s %s, Android %s, SDK %d",
                android.os.Build.MANUFACTURER,
                android.os.Build.MODEL,
                android.os.Build.VERSION.RELEASE,
                android.os.Build.VERSION.SDK_INT);
    }

    /**
     * 获取应用版本
     */
    private String getAppVersion() {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 设置最小日志级别
     */
    public void setMinLogLevel(int level) {
        this.minLogLevel = level;
    }

    /**
     * 设置是否自动写入文件
     */
    public void setAutoWriteToFile(boolean auto) {
        this.autoWriteToFile = auto;
    }
}