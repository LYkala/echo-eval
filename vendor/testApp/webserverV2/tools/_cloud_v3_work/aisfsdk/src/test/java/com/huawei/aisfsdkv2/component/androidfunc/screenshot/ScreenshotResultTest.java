/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.screenshot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * ScreenshotResult 测试
 */
public class ScreenshotResultTest {

    @Test
    public void testConstructorWithNullBitmap() {
        ScreenshotResult result = new ScreenshotResult(null, "com.test.app");
        assertNull(result.getBitmap());
        assertEquals("com.test.app", result.getForegroundAppPackageName());
    }

    @Test
    public void testConstructorWithNullPackageName() {
        ScreenshotResult result = new ScreenshotResult(null, null);
        assertNull(result.getBitmap());
        assertNull(result.getForegroundAppPackageName());
    }

    @Test
    public void testGetForegroundAppPackageName() {
        ScreenshotResult result = new ScreenshotResult(null, "com.example.launcher");
        assertEquals("com.example.launcher", result.getForegroundAppPackageName());
    }

    @Test
    public void testGetForegroundAppPackageNameEmpty() {
        ScreenshotResult result = new ScreenshotResult(null, "");
        assertEquals("", result.getForegroundAppPackageName());
    }

    @Test
    public void testGetBitmapNull() {
        ScreenshotResult result = new ScreenshotResult(null, "pkg");
        assertNull(result.getBitmap());
    }
}
