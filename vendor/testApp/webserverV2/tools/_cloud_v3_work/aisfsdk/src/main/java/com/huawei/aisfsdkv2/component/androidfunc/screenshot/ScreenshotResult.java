/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.screenshot;

import android.graphics.Bitmap;

/**
 * 截图结果
 *
 * <p>封装截图位图及截图时刻屏幕最上层应用的包名。</p>
 */
public class ScreenshotResult {
    /** 截图位图 */
    private final Bitmap bitmap;

    /** 截图时刻屏幕最上层应用包名，获取失败为 null */
    private final String foregroundAppPackageName;

    /**
     * 构造方法
     *
     * @param bitmap                   截图位图
     * @param foregroundAppPackageName 截图时刻屏幕最上层应用包名
     */
    public ScreenshotResult(Bitmap bitmap, String foregroundAppPackageName) {
        this.bitmap = bitmap;
        this.foregroundAppPackageName = foregroundAppPackageName;
    }

    /**
     * 获取截图位图
     *
     * @return 截图位图
     */
    public Bitmap getBitmap() {
        return bitmap;
    }

    /**
     * 获取截图时刻屏幕最上层应用包名
     *
     * @return 应用包名，获取失败返回 null
     */
    public String getForegroundAppPackageName() {
        return foregroundAppPackageName;
    }
}
