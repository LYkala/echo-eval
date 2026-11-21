package com.example.testapp.util;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.example.testapp.util.ScreenshotDiagLogger;

/**
 * 截图管理器
 *
 * 【核心功能】
 * 1. 管理截图队列，支持排队上传
 * 2. 通过 HTTP 按 500ms 间隔上传截图，避免 WebSocket 队列拥塞
 * 3. 上传成功后删除本地文件
 * 4. 支持界面变化自动截图
 */
public class ScreenshotManager {

    private static final String TAG = "ScreenshotManager";

    /** 单例实例 */
    private static ScreenshotManager instance;

    /** 应用上下文 */
    private final Context context;

    /** 待上传截图队列（线程安全） */
    private final ConcurrentLinkedQueue<ScreenshotTask> uploadQueue;

    /** 主线程Handler（仅回调 UI） */
    private final Handler handler;

    /** 上传专用线程，避免 HTTP 上传阻塞主线程/GUI 轮询 */
    private HandlerThread uploadThread;
    private Handler uploadHandler;

    /** 是否正在上传 */
    private volatile boolean isUploading = false;

    /** 是否允许继续上传（用户停止后为 false） */
    private volatile boolean uploadsEnabled = true;

    private final Runnable uploadNextRunnable = this::uploadNextScreenshot;

    /** 上传间隔（毫秒） */
    private static final long UPLOAD_INTERVAL_MS = 500;

    /** 当前测试ID */
    private String currentTestId;

    /** 是否启用自动截图（仅在测试期间启用） */
    private volatile boolean autoScreenshotEnabled = false;

    /**
     * 截图任务
     */
    public static class ScreenshotTask {
        public String testId;
        public File file;
        public long timestamp;

        public ScreenshotTask(String testId, File file) {
            this.testId = testId;
            this.file = file;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * 截图回调接口
     */
    public interface ScreenshotCallback {
        void onScreenshotSaved(File file);
        void onScreenshotUploaded(File file, boolean success);
    }

    private ScreenshotCallback callback;

    /**
     * 获取单例实例
     */
    public static synchronized ScreenshotManager getInstance(Context context) {
        if (instance == null) {
            instance = new ScreenshotManager(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * 私有构造函数
     */
    private ScreenshotManager(Context context) {
        this.context = context;
        this.uploadQueue = new ConcurrentLinkedQueue<>();
        this.handler = new Handler(Looper.getMainLooper());

        Logger.safeInfo(TAG, "ScreenshotManager initialized");
    }

    /**
     * 设置回调
     */
    public void setCallback(ScreenshotCallback callback) {
        this.callback = callback;
    }

    /**
     * 开始测试，设置当前测试ID
     */
    public void startTest(String testId) {
        this.currentTestId = testId;
        this.autoScreenshotEnabled = true;
        this.uploadsEnabled = true;
        Logger.safeInfo(TAG, "Test started: " + testId);
    }

    /**
     * 结束测试
     */
    public void endTest() {
        this.autoScreenshotEnabled = false;
        Logger.safeInfo(TAG, "Test ended: " + currentTestId);
    }

    /**
     * 用户停止：清空待上传队列并停止上传循环
     */
    public void stopAndClearUploads() {
        uploadsEnabled = false;
        isUploading = false;
        handler.removeCallbacks(uploadNextRunnable);
        if (uploadHandler != null) {
            uploadHandler.removeCallbacks(uploadNextRunnable);
        }
        uploadQueue.clear();
        Logger.safeInfo(TAG, "Upload stopped and queue cleared for test: " + currentTestId);
    }

    /**
     * 是否启用自动截图
     */
    public boolean isAutoScreenshotEnabled() {
        return autoScreenshotEnabled;
    }

    /**
     * 获取截图保存目录
     */
    public File getScreenshotDir(String testId) {
        File dir = new File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "screenshots/" + testId
        );
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * 获取待上传队列大小
     */
    public int getQueueSize() {
        return uploadQueue.size();
    }

    /**
     * 添加截图到上传队列
     */
    public void addToUploadQueue(String testId, File screenshotFile) {
        if (!uploadsEnabled) {
            Logger.safeInfo(TAG, "Uploads disabled, discard screenshot: "
                    + (screenshotFile != null ? screenshotFile.getName() : "null"));
            return;
        }
        if (screenshotFile == null || !screenshotFile.exists()) {
            Logger.safeWarn(TAG, "Screenshot file does not exist, skip adding to queue");
            return;
        }

        ScreenshotTask task = new ScreenshotTask(testId, screenshotFile);
        uploadQueue.offer(task);
        Logger.safeInfo(TAG, "Screenshot added to queue: " + screenshotFile.getName() + ", queue size: " + uploadQueue.size());

        // 尝试开始上传
        startUploadIfNeeded();
    }

    /**
     * 将截图插入队列头部，优先于普通步骤截图上传
     */
    public void addToUploadQueueHead(String testId, File screenshotFile) {
        if (!uploadsEnabled) {
            Logger.safeInfo(TAG, "Uploads disabled, discard queue-head screenshot: "
                    + (screenshotFile != null ? screenshotFile.getName() : "null"));
            return;
        }
        if (screenshotFile == null || !screenshotFile.exists()) {
            Logger.safeWarn(TAG, "Screenshot file does not exist, skip adding to queue head");
            return;
        }

        ScreenshotTask task = new ScreenshotTask(testId, screenshotFile);
        ConcurrentLinkedQueue<ScreenshotTask> reordered = new ConcurrentLinkedQueue<>();
        reordered.offer(task);
        ScreenshotTask pending;
        while ((pending = uploadQueue.poll()) != null) {
            reordered.offer(pending);
        }
        uploadQueue.addAll(reordered);
        Logger.safeInfo(TAG, "Screenshot added to queue head: " + screenshotFile.getName()
                + ", queue size: " + uploadQueue.size());
        startUploadIfNeeded();
    }

    /**
     * 确保上传流程已启动（供 result_final 等关键截图使用）
     */
    public void ensureUploading() {
        startUploadIfNeeded();
    }

    /**
     * 添加截图到队列并立即尝试上传
     */
    public void addAndTryUpload(String testId, File screenshotFile) {
        addToUploadQueue(testId, screenshotFile);
    }

    private void ensureUploadThread() {
        if (uploadThread == null || !uploadThread.isAlive()) {
            uploadThread = new HandlerThread("screenshot-upload");
            uploadThread.start();
            uploadHandler = new Handler(uploadThread.getLooper());
            Logger.safeInfo(TAG, "Upload thread started");
        }
    }

    private void postUploadTask(Runnable runnable) {
        ensureUploadThread();
        uploadHandler.post(runnable);
    }

    private void postDelayedUploadTask(Runnable runnable, long delayMs) {
        ensureUploadThread();
        uploadHandler.postDelayed(runnable, delayMs);
    }

    /**
     * 如果需要则开始上传
     */
    private void startUploadIfNeeded() {
        if (!uploadsEnabled) {
            return;
        }
        if (isUploading) {
            Logger.safeDebug(TAG, "Already uploading, skip");
            return;
        }

        WebServerClient client = WebServerClient.getInstance(null);
        if (client == null || client.getConfig() == null) {
            Logger.safeDebug(TAG, "WebServer config unavailable, defer upload");
            return;
        }

        startUploadProcess();
    }

    /**
     * 开始上传流程
     */
    private void startUploadProcess() {
        if (isUploading) {
            return;
        }

        isUploading = true;
        Logger.safeInfo(TAG, "Starting upload process, queue size: " + uploadQueue.size());
        postUploadTask(uploadNextRunnable);
    }

    /**
     * 上传下一个截图（在 upload 线程执行，不阻塞主线程）
     */
    private void uploadNextScreenshot() {
        if (!uploadsEnabled) {
            isUploading = false;
            Logger.safeInfo(TAG, "Upload process halted (uploads disabled)");
            return;
        }
        // 检查队列是否为空
        ScreenshotTask task = uploadQueue.poll();
        if (task == null) {
            isUploading = false;
            Logger.safeInfo(TAG, "Upload queue empty, stopping upload process");
            return;
        }

        WebServerClient client = WebServerClient.getInstance(null);
        if (client == null || client.getConfig() == null) {
            uploadQueue.offer(task);
            isUploading = false;
            Logger.safeWarn(TAG, "WebServer client unavailable, stopping upload, queue size: " + uploadQueue.size());
            return;
        }

        // 检查文件是否存在
        if (!task.file.exists()) {
            Logger.safeWarn(TAG, "Screenshot file not found: " + task.file.getName());
            postDelayedUploadTask(uploadNextRunnable, UPLOAD_INTERVAL_MS);
            return;
        }

        Logger.safeInfo(TAG, "Uploading screenshot: " + task.file.getName() + " for test: " + task.testId);

        boolean success = client.uploadScreenshotHttp(task.testId, task.file);
        String via = "http";
        if (!success && client.isConnected()) {
            success = client.uploadScreenshot(task.testId, task.file);
            via = "websocket";
        }

        ScreenshotDiagLogger.upload(task.file.getName(), success, via,
                "testId=" + task.testId + " queueLeft=" + uploadQueue.size());

        if (success) {
            // 上传成功，删除本地文件
            boolean deleted = task.file.delete();
            Logger.safeInfo(TAG, "Screenshot uploaded and " + (deleted ? "deleted" : "delete failed") + ": " + task.file.getName());

            if (callback != null) {
                callback.onScreenshotUploaded(task.file, true);
            }
        } else {
            Logger.safeWarn(TAG, "Screenshot upload failed: " + task.file.getName());
            if (callback != null) {
                callback.onScreenshotUploaded(task.file, false);
            }
        }

        postDelayedUploadTask(uploadNextRunnable, UPLOAD_INTERVAL_MS);
    }

    /**
     * WebServer连接后触发上传
     */
    public void onWebServerConnected() {
        Logger.safeInfo(TAG, "WebServer connected, checking upload queue, size: " + uploadQueue.size());
        if (!uploadQueue.isEmpty()) {
            startUploadProcess();
        }
    }

    /**
     * 获取所有待上传的截图文件
     */
    public List<File> getAllPendingScreenshots() {
        List<File> files = new ArrayList<>();
        for (ScreenshotTask task : uploadQueue) {
            if (task.file.exists()) {
                files.add(task.file);
            }
        }
        return files;
    }

    /**
     * 扫描并加载所有未上传的截图
     */
    public void scanAndLoadPendingScreenshots() {
        File picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (picturesDir == null || !picturesDir.exists()) {
            return;
        }

        File screenshotsDir = new File(picturesDir, "screenshots");
        if (!screenshotsDir.exists()) {
            return;
        }

        // 遍历所有测试ID目录
        File[] testDirs = screenshotsDir.listFiles(File::isDirectory);
        if (testDirs == null) {
            return;
        }

        for (File testDir : testDirs) {
            String testId = testDir.getName();
            File[] screenshots = testDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".jpg"));

            if (screenshots != null) {
                for (File screenshot : screenshots) {
                    addToUploadQueue(testId, screenshot);
                }
            }
        }

        Logger.safeInfo(TAG, "Scanned and loaded " + uploadQueue.size() + " pending screenshots");
    }

    /**
     * 阻塞直到上传队列排空或超时（收尾上报前调用，避免过程帧丢失）。
     */
    public boolean awaitQueueDrained(long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(1000L, timeoutMs);
        startUploadIfNeeded();
        int lastSize = uploadQueue.size();
        long lastProgressAt = System.currentTimeMillis();
        while (System.currentTimeMillis() < deadline) {
            if (uploadQueue.isEmpty() && !isUploading) {
                return true;
            }
            int size = uploadQueue.size();
            if (size < lastSize || isUploading) {
                lastSize = size;
                lastProgressAt = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - lastProgressAt > 10000L) {
                Logger.safeWarn(TAG, "awaitQueueDrained: 10s无进展，提前结束 queue=" + size);
                break;
            }
            startUploadIfNeeded();
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        boolean empty = uploadQueue.isEmpty() && !isUploading;
        Logger.safeInfo(TAG, "awaitQueueDrained done empty=" + empty
                + " queueSize=" + uploadQueue.size() + " uploading=" + isUploading);
        return empty;
    }

    /**
     * 把本轮遗留的未上传截图重新入队（只收最近文件，避免把历史残留几十张全塞进队列卡死收尾）。
     */
    public void requeueLocalScreenshots(String testId) {
        requeueLocalScreenshots(testId, 0L);
    }

    public void requeueLocalScreenshots(String testId, long notBeforeEpochMs) {
        if (testId == null || testId.isEmpty()) {
            return;
        }
        File dir = getScreenshotDir(testId);
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".png");
        });
        if (files == null || files.length == 0) {
            return;
        }
        long cutoff = notBeforeEpochMs > 0
                ? notBeforeEpochMs - 5000L
                : System.currentTimeMillis() - 10 * 60 * 1000L;
        int added = 0;
        int skipped = 0;
        for (File f : files) {
            if (f == null || !f.exists() || f.length() <= 0) {
                continue;
            }
            if (f.lastModified() < cutoff) {
                skipped++;
                continue;
            }
            addToUploadQueue(testId, f);
            added++;
        }
        Logger.safeInfo(TAG, "requeueLocalScreenshots testId=" + testId
                + " added=" + added + " skippedOld=" + skipped + " cutoffMs=" + cutoff);
    }

    /** 开跑前清掉同用例目录的历史残留，避免收尾 requeue 拖死上传。 */
    public void clearLocalScreenshots(String testId) {
        if (testId == null || testId.isEmpty()) {
            return;
        }
        File dir = getScreenshotDir(testId);
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        int n = 0;
        for (File f : files) {
            if (f != null && f.isFile() && f.delete()) {
                n++;
            }
        }
        Logger.safeInfo(TAG, "clearLocalScreenshots testId=" + testId + " deleted=" + n);
    }

    /**
     * 清空上传队列
     */
    public void clearQueue() {
        uploadQueue.clear();
        Logger.safeInfo(TAG, "Upload queue cleared");
    }

    /**
     * 获取当前测试ID
     */
    public String getCurrentTestId() {
        return currentTestId;
    }
}