/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.screenshot;

import android.graphics.Bitmap;

/**
 * 截图管理器接口
 *
 * <p>提供统一的截图能力，优先使用无障碍服务截图，失败时回退到虚拟屏截图。</p>
 *
 * @author l00504561
 * @since 2026-04-10
 */
public interface ScreenshotManager {

    /**
     * 启动截图服务
     */
    void startScreenshotService();

    /**
     * 停止截图服务
     */
    void stopScreenshotService();

    /**
     * 截图
     *
     * <p>优先通过无障碍服务截图，若返回null则通过虚拟屏截图。</p>
     *
     * @return 截图位图，失败返回null
     */
    Bitmap takeScreenshot();

    /**
     * 获取有效屏幕截图
     *
     * <p>使用截图功能，自动重试多次，并过滤纯色图片。</p>
     *
     * <p>实现逻辑：</p>
     * <ol>
     *   <li>优先使用无障碍服务截图，外部重试并判断有效性</li>
     *   <li>若无障碍服务截图失败，回退到虚拟屏截图，同样外部重试并判断有效性</li>
     * </ol>
     *
     * @return 有效的截图位图，失败返回null
     */
    Bitmap takeValidScreenshot();

    /**
     * 获取有效屏幕截图及截图时刻前台应用包名
     *
     * <p>在 {@link #takeValidScreenshot()} 基础上，截图成功后立即读取屏幕最上层应用包名，
     * 一并封装在 {@link ScreenshotResult} 中返回，确保包名与截图画面时刻严格对应。</p>
     *
     * @return 截图结果（含位图和前台包名），截图失败返回 null
     */
    ScreenshotResult takeValidScreenshotWithResult();

    /**
     * 获取屏幕分辨率
     *
     * <p>返回屏幕的宽度和高度（像素）。</p>
     *
     * @return int[2] 包含宽度和高度的数组，[0]=width, [1]=height
     */
    int[] getScreenResolution();

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
    Bitmap getLastScreenshot();
}
