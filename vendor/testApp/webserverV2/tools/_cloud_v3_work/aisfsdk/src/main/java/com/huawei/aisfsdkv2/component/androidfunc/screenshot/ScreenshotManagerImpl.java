/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.screenshot;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.impl.AccessibilityManagerImpl;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.util.ImageUtil;
import com.huawei.aisfsdkv2.util.ThreadUtil;

/**
 * 截图管理器实现类
 *
 * <p>提供统一的截图能力，优先使用无障碍服务截图，失败时回退到虚拟屏截图。</p>
 *
 * <h3>截图策略</h3>
 * <ol>
 *   <li>优先使用无障碍服务截图（AccessibilityService）</li>
 *   <li>若无障碍服务截图失败，回退到虚拟屏截图（MediaProjection）</li>
 * </ol>
 *
 * @author l00504561
 * @since 2026-04-10
 */
public class ScreenshotManagerImpl implements ScreenshotManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ScreenshotManagerImpl");

    /** 最大重试次数 */
    private static final int MAX_RETRY_TIMES = 10;

    /** 重试间隔（毫秒） */
    private static final int RETRY_INTERVAL_MS = 100;

    /** 最近一次成功截图的缓存（全局只保存一份） */
    private volatile Bitmap lastScreenshotCache = null;

    /** 截图缓存同步锁 */
    private final Object screenshotLock = new Object();

    private static class Holder {
        private static final ScreenshotManagerImpl INSTANCE = new ScreenshotManagerImpl();
    }

    /**
     * 获取单例实例
     *
     * @return ScreenshotManagerImpl 单例
     */
    public static ScreenshotManagerImpl getInstance() {
        return Holder.INSTANCE;
    }

    private ScreenshotManagerImpl() {
    }

    /**
     * 启动截图服务
     */
    @Override
    public void startScreenshotService() {
        LOGGER.info("startScreenshotService: starting MediaScreenshotService");
        MediaScreenshotService.start();
    }

    /**
     * 停止截图服务
     */
    @Override
    public void stopScreenshotService() {
        LOGGER.info("stopScreenshotService: stopping MediaScreenshotService");
        MediaScreenshotService.stop();
    }

    /**
     * 截图
     *
     * <p>优先通过无障碍服务截图，若返回null则通过虚拟屏截图。</p>
     *
     * <p>截图成功后会自动更新全局缓存，可通过 {@link #getLastScreenshot()} 获取。</p>
     *
     * @return 截图位图，失败返回null
     */
    @Override
    public Bitmap takeScreenshot() {
        Bitmap bitmap = null;
        // 优先使用无障碍服务截图
        AccessibilityManagerImpl accessibilityManager = AccessibilityManagerImpl.getInstance();
        if (accessibilityManager != null) {
            bitmap = accessibilityManager.takeScreenshot();
            if (bitmap != null) {
                LOGGER.info("takeScreenshot by AccessibilityService success");
                updateLastScreenshot(bitmap);
                return bitmap;
            }
            LOGGER.warn("takeScreenshot by AccessibilityService failed, fallback to MediaProjection");
        }
        // 回退到虚拟屏截图
        bitmap = MediaScreenshotService.takeScreenshot();
        if (bitmap != null) {
            LOGGER.info("takeScreenshot by MediaProjection success");
            updateLastScreenshot(bitmap);
        } else {
            LOGGER.error("takeScreenshot by MediaProjection failed");
        }
        return bitmap;
    }

    /**
     * 获取有效屏幕截图
     *
     * <p>使用截图功能，自动重试多次，并过滤纯色图片。</p>
     *
     * <p>实现逻辑：</p>
     * <ol>
     *   <li>优先使用无障碍服务截图，外部重试并判断有效性（非纯色）</li>
     *   <li>若无障碍服务截图失败，回退到虚拟屏截图，同样外部重试并判断有效性</li>
     * </ol>
     *
     * @return 有效的截图位图，失败返回null
     */
    @Override
    public Bitmap takeValidScreenshot() {
        // 优先使用无障碍服务截图
        AccessibilityManagerImpl accessibilityManager = AccessibilityManagerImpl.getInstance();
        if (accessibilityManager != null) {
            Bitmap bitmap = takeValidScreenshotWithRetry(accessibilityManager::takeScreenshot);
            if (bitmap != null) {
                LOGGER.info("takeValidScreenshot by AccessibilityService success");
                return bitmap;
            }
            LOGGER.warn("takeValidScreenshot by AccessibilityService failed, fallback to MediaProjection");
        }
        // 回退到虚拟屏截图
        Bitmap bitmap = takeValidScreenshotWithRetry(MediaScreenshotService::takeScreenshot);
        if (bitmap != null) {
            LOGGER.info("takeValidScreenshot by MediaProjection success");
        } else {
            LOGGER.error("takeValidScreenshot by MediaProjection failed");
        }
        return bitmap;
    }

    /**
     * 获取有效屏幕截图及截图时刻前台应用包名
     *
     * <p>截图成功后立即读取屏幕最上层应用包名，封装为 {@link ScreenshotResult} 返回。</p>
     *
     * @return 截图结果（含位图和前台包名），截图失败返回 null
     */
    @Override
    public ScreenshotResult takeValidScreenshotWithResult() {
        Bitmap bitmap = takeValidScreenshot();
        if (bitmap == null) {
            return null;
        }
        AccessibilityManagerImpl accessibilityManager = AccessibilityManagerImpl.getInstance();
        String packageName = null;
        if (accessibilityManager != null) {
            try {
                packageName = accessibilityManager.getForegroundPackageName();
            } catch (RuntimeException e) {
                LOGGER.error("takeValidScreenshotWithResult: foreground package failed: {}", e.getMessage());
            }
        }
        return new ScreenshotResult(bitmap, packageName);
    }

    /**
     * 带重试的有效截图
     *
     * <p>多次尝试截图，并过滤纯色图片。</p>
     *
     * @param screenshotProvider 截图提供者
     * @return 有效的截图位图，失败返回null
     */
    private Bitmap takeValidScreenshotWithRetry(ScreenshotProvider screenshotProvider) {
        for (int i = 0; i < MAX_RETRY_TIMES; i++) {
            Bitmap bitmap = screenshotProvider.takeScreenshot();
            if (bitmap != null && !ImageUtil.isNullOrPureImage(bitmap)) {
                LOGGER.info("takeValidScreenshotWithRetry: success after {} retries", i);
                return bitmap;
            }
            if (bitmap != null) {
                bitmap.recycle();
            }
            LOGGER.warn("takeValidScreenshotWithRetry: retry {}/{}, image is null or pure", i + 1, MAX_RETRY_TIMES);
            ThreadUtil.sleepNoExcept(RETRY_INTERVAL_MS);
        }
        LOGGER.error("takeValidScreenshotWithRetry: failed after {} retries", MAX_RETRY_TIMES);
        return null;
    }

    /**
     * 截图提供者接口
     */
    private interface ScreenshotProvider {
        /**
         * 截图
         *
         * @return 截图位图，失败返回null
         */
        Bitmap takeScreenshot();
    }

    /**
     * 获取屏幕分辨率
     *
     * <p>通过 WindowManager 获取屏幕的宽度和高度（像素）。</p>
     *
     * @return int[2] 包含宽度和高度的数组，[0]=width, [1]=height
     */
    @Override
    public int[] getScreenResolution() {
        LOGGER.info("getScreenResolution: entering");
        try {
            Context context = AISFSDKClient.getBaseApi().getAppContext();
            if (context == null) {
                LOGGER.error("getScreenResolution: context is null");
                return new int[]{0, 0};
            }
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager == null) {
                LOGGER.error("getScreenResolution: windowManager is null");
                return new int[]{0, 0};
            }
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            int[] resolution = new int[]{metrics.widthPixels, metrics.heightPixels};
            LOGGER.info("getScreenResolution: resolution = [{}, {}]", resolution[0], resolution[1]);
            return resolution;
        } catch (Exception e) {
            LOGGER.error("getScreenResolution: failed to get screen resolution, error: {}", e.getMessage());
            return new int[]{0, 0};
        }
    }

    /**
     * 获取最近一次成功截取的屏幕截图（副本）
     *
     * <p>返回的是全局缓存截图的副本，调用者可以自由使用和回收，不会影响全局缓存。
     * 如果全局缓存不存在或已被回收，则返回 null。</p>
     *
     * <p><b>注意：</b>返回的副本需要调用者在使用完毕后手动调用 {@link Bitmap#recycle()} 回收，
     * 避免内存泄漏。</p>
     *
     * @return 截图副本；如果无缓存、缓存已被回收则返回 null
     */
    @Override
    public Bitmap getLastScreenshot() {
        synchronized (screenshotLock) {
            if (lastScreenshotCache == null) {
                LOGGER.debug("getLastScreenshot: no cached screenshot");
                return null;
            }
            if (lastScreenshotCache.isRecycled()) {
                LOGGER.warn("getLastScreenshot: cached screenshot was recycled");
                lastScreenshotCache = null;
                return null;
            }
            LOGGER.debug("getLastScreenshot: returning copy of cached screenshot [{}x{}]",
                lastScreenshotCache.getWidth(), lastScreenshotCache.getHeight());
            return Bitmap.createBitmap(lastScreenshotCache);
        }
    }

    /**
     * 更新最近一次截图缓存
     *
     * <p>将新截图复制一份保存到全局缓存，同时回收旧缓存。</p>
     *
     * @param newScreenshot 新的截图（不会被修改或回收，只是读取）
     */
    private void updateLastScreenshot(Bitmap newScreenshot) {
        if (newScreenshot == null || newScreenshot.isRecycled()) {
            LOGGER.warn("updateLastScreenshot: new screenshot is null or recycled, skip update");
            return;
        }
        synchronized (screenshotLock) {
            Bitmap oldCache = lastScreenshotCache;
            lastScreenshotCache = Bitmap.createBitmap(newScreenshot);
            LOGGER.info("updateLastScreenshot: cache updated [{}x{}]",
                lastScreenshotCache.getWidth(), lastScreenshotCache.getHeight());
            if (oldCache != null && !oldCache.isRecycled()) {
                oldCache.recycle();
                LOGGER.debug("updateLastScreenshot: old cache recycled");
            }
        }
    }
}
