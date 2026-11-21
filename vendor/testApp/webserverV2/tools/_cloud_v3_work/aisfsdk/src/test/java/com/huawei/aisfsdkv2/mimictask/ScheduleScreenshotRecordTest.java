/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.mimictask;

import android.graphics.Bitmap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * ScheduleScreenshotRecord 单元测试
 * 使用 Robolectric 创建真实的 Bitmap 对象
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ScheduleScreenshotRecordTest {

    @Test
    public void testConstructor_WithBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        long time = System.currentTimeMillis();
        ScheduleScreenshotRecord record = new ScheduleScreenshotRecord(bitmap, time);
        assertNotNull(record.image);
        assertEquals(bitmap, record.image);
        assertEquals(time, record.captureTime);
        bitmap.recycle();
    }

    @Test
    public void testConstructor_NullImage() {
        long time = 12345L;
        ScheduleScreenshotRecord record = new ScheduleScreenshotRecord(null, time);
        assertNull(record.image);
        assertEquals(time, record.captureTime);
    }

    @Test
    public void testIsRecycled_NullImage() {
        ScheduleScreenshotRecord record = new ScheduleScreenshotRecord(null, 0L);
        assertTrue(record.isRecycled());
    }

    @Test
    public void testIsRecycled_WithBitmap_NotRecycled() {
        Bitmap bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        ScheduleScreenshotRecord record = new ScheduleScreenshotRecord(bitmap, 0L);
        assertFalse(record.isRecycled());
        bitmap.recycle();
    }

    @Test
    public void testIsRecycled_AfterRecycle() {
        Bitmap bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        ScheduleScreenshotRecord record = new ScheduleScreenshotRecord(bitmap, 0L);
        assertFalse(record.isRecycled());
        record.recycle();
        assertTrue(record.isRecycled());
    }

    @Test
    public void testRecycle_WithBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.RGB_565);
        ScheduleScreenshotRecord record = new ScheduleScreenshotRecord(bitmap, 0L);
        assertFalse(bitmap.isRecycled());
        record.recycle();
        assertTrue(bitmap.isRecycled());
    }

    @Test
    public void testRecycle_NullImage_NoException() {
        ScheduleScreenshotRecord record = new ScheduleScreenshotRecord(null, 0L);
        record.recycle();
        assertTrue(record.isRecycled());
    }

    @Test
    public void testRecycle_AlreadyRecycled_NoException() {
        Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        bitmap.recycle();
        ScheduleScreenshotRecord record = new ScheduleScreenshotRecord(bitmap, 0L);
        assertTrue(record.isRecycled());
        record.recycle();
        assertTrue(record.isRecycled());
    }

    @Test
    public void testRecycle_MultipleCalls_NoException() {
        Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        ScheduleScreenshotRecord record = new ScheduleScreenshotRecord(bitmap, 0L);
        record.recycle();
        record.recycle();
        record.recycle();
        assertTrue(record.isRecycled());
    }

    @Test
    public void testCaptureTime_Zero() {
        ScheduleScreenshotRecord record = new ScheduleScreenshotRecord(null, 0L);
        assertEquals(0L, record.captureTime);
    }

    @Test
    public void testCaptureTime_LargeValue() {
        long large = Long.MAX_VALUE;
        ScheduleScreenshotRecord record = new ScheduleScreenshotRecord(null, large);
        assertEquals(large, record.captureTime);
    }

    @Test
    public void testCaptureTime_NegativeValue() {
        ScheduleScreenshotRecord record = new ScheduleScreenshotRecord(null, -1L);
        assertEquals(-1L, record.captureTime);
    }

    @Test
    public void testBitmap_DifferentConfigs() {
        Bitmap argb8888 = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        Bitmap rgb565 = Bitmap.createBitmap(10, 10, Bitmap.Config.RGB_565);
        Bitmap alpha8 = Bitmap.createBitmap(10, 10, Bitmap.Config.ALPHA_8);
        ScheduleScreenshotRecord record1 = new ScheduleScreenshotRecord(argb8888, 1L);
        ScheduleScreenshotRecord record2 = new ScheduleScreenshotRecord(rgb565, 2L);
        ScheduleScreenshotRecord record3 = new ScheduleScreenshotRecord(alpha8, 3L);
        assertNotNull(record1.image);
        assertNotNull(record2.image);
        assertNotNull(record3.image);
        assertEquals(Bitmap.Config.ARGB_8888, record1.image.getConfig());
        assertEquals(Bitmap.Config.RGB_565, record2.image.getConfig());
        assertEquals(Bitmap.Config.ALPHA_8, record3.image.getConfig());
        record1.recycle();
        record2.recycle();
        record3.recycle();
    }

    @Test
    public void testBitmap_DifferentSizes() {
        Bitmap small = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        Bitmap medium = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Bitmap large = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        ScheduleScreenshotRecord recordSmall = new ScheduleScreenshotRecord(small, 1L);
        ScheduleScreenshotRecord recordMedium = new ScheduleScreenshotRecord(medium, 2L);
        ScheduleScreenshotRecord recordLarge = new ScheduleScreenshotRecord(large, 3L);
        assertEquals(1, recordSmall.image.getWidth());
        assertEquals(1, recordSmall.image.getHeight());
        assertEquals(100, recordMedium.image.getWidth());
        assertEquals(100, recordMedium.image.getHeight());
        assertEquals(1920, recordLarge.image.getWidth());
        assertEquals(1080, recordLarge.image.getHeight());
        recordSmall.recycle();
        recordMedium.recycle();
        recordLarge.recycle();
    }
}
