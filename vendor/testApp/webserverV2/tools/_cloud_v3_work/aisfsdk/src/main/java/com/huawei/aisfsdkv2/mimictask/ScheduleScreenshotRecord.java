/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.mimictask;

import android.graphics.Bitmap;

/**
 * 截图记录
 *
 * @since 2026-04-09
 */
public class ScheduleScreenshotRecord {

    /** 截图位图 */
    public final Bitmap image;

    /** 截图时间 */
    public final long captureTime;

    /**
     * 构造函数
     *
     * @param image       截图位图
     * @param captureTime 截图时间
     */
    public ScheduleScreenshotRecord(Bitmap image, long captureTime) {
        this.image = image;
        this.captureTime = captureTime;
    }

    /**
     * 检查截图是否已回收
     *
     * @return true 已回收
     */
    public boolean isRecycled() {
        return image == null || image.isRecycled();
    }

    /**
     * 回收截图资源
     */
    public void recycle() {
        if (image != null && !image.isRecycled()) {
            image.recycle();
        }
    }
}
