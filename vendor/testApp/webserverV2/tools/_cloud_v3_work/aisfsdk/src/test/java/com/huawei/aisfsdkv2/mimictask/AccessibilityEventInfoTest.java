/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.mimictask;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * AccessibilityEventInfo 测试（使用 package-private 构造器）
 */
public class AccessibilityEventInfoTest {

    @Test
    public void testConstructor_BasicFields() {
        AccessibilityEventInfo info = new AccessibilityEventInfo(
                1, 1000L, "com.example", "hello", null, 0, 0, 100, 200);
        assertEquals(1, info.getEventType());
        assertEquals(1000L, info.getEventTime());
        assertEquals("com.example", info.getPackageName());
        assertEquals("hello", info.getText());
        assertNull(info.getSource());
        assertEquals(0, info.getScrollX());
        assertEquals(0, info.getScrollY());
        assertEquals(100, info.getMaxScrollX());
        assertEquals(200, info.getMaxScrollY());
    }

    @Test
    public void testConstructor_NullTextAndPackage() {
        AccessibilityEventInfo info = new AccessibilityEventInfo(
                2, 2000L, null, null, null, 10, 20, 50, 80);
        assertNull(info.getPackageName());
        assertNull(info.getText());
        assertEquals(10, info.getScrollX());
        assertEquals(20, info.getScrollY());
    }

    @Test
    public void testConstructor_ScrollValues() {
        AccessibilityEventInfo info = new AccessibilityEventInfo(
                3, 500L, "pkg", null, null, 5, 10, 100, 300);
        assertEquals(5, info.getScrollX());
        assertEquals(10, info.getScrollY());
        assertEquals(100, info.getMaxScrollX());
        assertEquals(300, info.getMaxScrollY());
    }

    @Test
    public void testConstructor_EventTypes() {
        AccessibilityEventInfo info1 = new AccessibilityEventInfo(
                0x00000001, 0L, null, null, null, 0, 0, 0, 0);
        assertEquals(0x00000001, info1.getEventType());

        AccessibilityEventInfo info2 = new AccessibilityEventInfo(
                0x00000008, 0L, null, null, null, 0, 0, 0, 0);
        assertEquals(0x00000008, info2.getEventType());
    }

    @Test
    public void testConstructor_EventTime() {
        long time = System.currentTimeMillis();
        AccessibilityEventInfo info = new AccessibilityEventInfo(
                1, time, null, null, null, 0, 0, 0, 0);
        assertEquals(time, info.getEventTime());
    }
}
