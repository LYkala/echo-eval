/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.mimictask;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import com.huawei.aisfsdkv2.component.androidfunc.AndroidFuncManager;
import com.huawei.aisfsdkv2.component.androidfunc.screenshot.MediaScreenshotService;
import com.huawei.aisfsdkv2.component.androidfunc.screenshot.ScreenshotManager;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 截图管理器实现类
 *
 * <p>负责管理定时截图任务和图片选择逻辑，用于捕获用户操作前的屏幕状态。</p>
 *
 * @since 2026-04-09
 */
public class ScheduleScreenshotManagerImpl implements ScheduleScreenshotManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("ScheduleScreenshotManagerImpl");

    /** 定时截图间隔（毫秒） */
    private static final long CAPTURE_INTERVAL_MS = 300;

    /** 截图列表最大容量 */
    private static final int MAX_CAPTURE_LIST_SIZE = 100;

    /** 后台截图线程 */
    private HandlerThread captureThread;

    /** 后台截图 Handler */
    private Handler captureHandler;

    /** 定时截图是否运行中 */
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /** 是否正在截图中 */
    private final AtomicBoolean isCapturing = new AtomicBoolean(false);

    /** 定时截图列表 */
    private final List<ScheduleScreenshotRecord> captureList = new ArrayList<>();

    /**
     * 获取单例实例
     *
     * @return ScheduleScreenshotManager 实例
     */
    public static ScheduleScreenshotManager getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 启动定时截图任务
     */
    @Override
    public void start() {
        if (isRunning.get()) {
            LOGGER.info("start: already running");
            return;
        }
        ScreenshotManager screenshotManager = AndroidFuncManager.getInstance().getScreenshotManager();
        if (screenshotManager != null) {
            LOGGER.info("start: registering ServiceReadyCallback");
            MediaScreenshotService.registerServiceReadyCallback(new MediaScreenshotService.ServiceReadyCallback() {
                @Override
                public void onServiceReady(MediaScreenshotService service) {
                    LOGGER.info("Service ready callback received, initializing capture thread");
                    isRunning.set(true);
                    initCaptureThread();
                    scheduleNextCapture();
                    LOGGER.info("start: started via callback");
                }

                @Override
                public void onServiceFailed(String error) {
                    LOGGER.error("Service failed callback received: {}", error);
                }
            });
            
            LOGGER.info("start: starting screenshot service asynchronously");
            screenshotManager.startScreenshotService();
        } else {
            LOGGER.warn("start: ScreenshotManager is null, cannot start service");
        }
    }

    /**
     * 停止定时截图任务
     */
    @Override
    public void stop() {
        if (!isRunning.get()) {
            return;
        }
        isRunning.set(false);
        LOGGER.info("stop: stopped");
        if (captureHandler != null) {
            captureHandler.removeCallbacksAndMessages(null);
        }
        ScreenshotManager screenshotManager = AndroidFuncManager.getInstance().getScreenshotManager();
        if (screenshotManager != null) {
            LOGGER.info("stop: stopping screenshot service");
            screenshotManager.stopScreenshotService();
        }
    }

    /**
     * 查找操作前的最优图片
     *
     * <p>从截图列表中查找 captureTime 小于等于 eventTime 的最新一张图片</p>
     *
     * @param eventTime 事件时间
     * @return 操作前的截图记录，未找到返回 null
     */
    @Override
    public ScheduleScreenshotRecord findPreActionImage(long eventTime) {
        synchronized (captureList) {
            for (int i = captureList.size() - 1; i >= 0; i--) {
                ScheduleScreenshotRecord record = captureList.get(i);
                if (record != null && !record.isRecycled() && record.captureTime <= eventTime) {
                    LOGGER.info("findPreActionImage: found image, captureTime={}", record.captureTime);
                    return record;
                }
            }
        }
        LOGGER.info("findPreActionImage: not found for eventTime={}", eventTime);
        return null;
    }

    /**
     * 获取最新的截图
     *
     * @return 最新的截图记录，列表为空返回 null
     */
    public ScheduleScreenshotRecord getLatestCapture() {
        synchronized (captureList) {
            if (captureList.isEmpty()) {
                return null;
            }
            return captureList.get(captureList.size() - 1);
        }
    }

    /**
     * 清除指定截图记录
     *
     * @param record 截图记录
     */
    public void removeRecord(ScheduleScreenshotRecord record) {
        if (record == null) {
            return;
        }
        synchronized (captureList) {
            captureList.remove(record);
        }
        record.recycle();
        LOGGER.info("removeRecord: removed, list size={}", captureList.size());
    }

    /**
     * 重置管理器状态
     *
     * <p>停止定时任务，清空截图列表，释放资源</p>
     */
    public void reset() {
        stop();
        synchronized (captureList) {
            for (ScheduleScreenshotRecord record : captureList) {
                record.recycle();
            }
            captureList.clear();
        }
        isRunning.set(false);
        isCapturing.set(false);
        LOGGER.info("reset: manager reset");
        releaseCaptureThread();
    }

    /**
     * 定时截图是否运行中
     *
     * @return true 运行中
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * 初始化后台截图线程
     */
    private void initCaptureThread() {
        if (captureThread == null) {
            captureThread = new HandlerThread("ScreenshotCaptureThread");
            captureThread.start();
            captureHandler = new Handler(captureThread.getLooper());
        }
    }

    /**
     * 释放后台截图线程
     */
    private void releaseCaptureThread() {
        if (captureHandler != null) {
            captureHandler.removeCallbacksAndMessages(null);
        }
        if (captureThread != null) {
            captureThread.quitSafely();
            captureThread = null;
            captureHandler = null;
        }
    }

    /**
     * 安排下一次截图
     */
    private void scheduleNextCapture() {
        if (!isRunning.get()) {
            return;
        }
        captureHandler.post(() -> {
            performCapture();
            if (isRunning.get()) {
                captureHandler.postDelayed(this::scheduleNextCapture, CAPTURE_INTERVAL_MS);
            }
        });
    }

    /**
     * 执行截图
     */
    private void performCapture() {
        if (isCapturing.get()) {
            LOGGER.debug("performCapture: previous capture in progress, skip");
            return;
        }
        isCapturing.set(true);
        try {
            com.huawei.aisfsdkv2.component.androidfunc.screenshot.ScreenshotManager screenshotManager = AndroidFuncManager.getInstance().getScreenshotManager();
            if (screenshotManager == null) {
                LOGGER.warn("performCapture: screenshotManager is null");
                return;
            }
            Bitmap screenshot = screenshotManager.takeValidScreenshot();
            if (screenshot != null) {
                long captureTime = SystemClock.uptimeMillis();
                ScheduleScreenshotRecord record = new ScheduleScreenshotRecord(screenshot, captureTime);
                synchronized (captureList) {
                    captureList.add(record);
                    trimCaptureList();
                }
                LOGGER.debug("performCapture: success, size={}x{}, list size={}",
                        screenshot.getWidth(), screenshot.getHeight(), captureList.size());
            } else {
                LOGGER.warn("performCapture: screenshot is null");
            }
        } catch (Exception e) {
            LOGGER.error("performCapture: exception, error={}", e.getMessage());
        } finally {
            isCapturing.set(false);
        }
    }

    /**
     * 修剪截图列表，保持容量限制
     */
    private void trimCaptureList() {
        while (captureList.size() > MAX_CAPTURE_LIST_SIZE) {
            ScheduleScreenshotRecord oldest = captureList.remove(0);
            oldest.recycle();
            LOGGER.debug("trimCaptureList: recycled oldest record");
        }
    }

    /**
     * 单例持有者
     */
    private static class Holder {
        private static final ScheduleScreenshotManagerImpl INSTANCE = new ScheduleScreenshotManagerImpl();
    }
}
