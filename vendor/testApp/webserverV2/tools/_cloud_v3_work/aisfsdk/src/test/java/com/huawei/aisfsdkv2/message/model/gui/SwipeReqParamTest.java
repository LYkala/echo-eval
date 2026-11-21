/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * SwipeReqParam 测试
 */
public class SwipeReqParamTest {

    private SwipeReqParam param;

    @Before
    public void setUp() {
        param = new SwipeReqParam();
    }

    @Test
    public void testDefaultValues() {
        assertEquals(-1, param.getStartX());
        assertEquals(-1, param.getStartY());
        assertEquals(-1, param.getEndX());
        assertEquals(-1, param.getEndY());
        assertEquals(300, param.getDuration());
        assertEquals(0, param.getSleepTime());
        assertTrue(param.isNeedScreenShot());
        assertEquals(2, param.getMaxScreenshotTime());
    }

    @Test
    public void testParameterizedConstructor() {
        SwipeReqParam p = new SwipeReqParam(10, 20, 100, 200, 500);
        assertEquals(10, p.getStartX());
        assertEquals(20, p.getStartY());
        assertEquals(100, p.getEndX());
        assertEquals(200, p.getEndY());
        assertEquals(500, p.getDuration());
    }

    @Test
    public void testSetAndGetAllFields() {
        param.setStartX(50);
        param.setStartY(60);
        param.setEndX(150);
        param.setEndY(160);
        param.setDuration(400);
        param.setSleepTime(100);
        param.setNeedScreenShot(false);
        param.setMaxScreenshotTime(5);
        assertEquals(50, param.getStartX());
        assertEquals(60, param.getStartY());
        assertEquals(150, param.getEndX());
        assertEquals(160, param.getEndY());
        assertEquals(400, param.getDuration());
        assertEquals(100, param.getSleepTime());
        assertFalse(param.isNeedScreenShot());
        assertEquals(5, param.getMaxScreenshotTime());
    }

    @Test
    public void testToString() {
        param.setStartX(10);
        param.setStartY(20);
        String str = param.toString();
        assertNotNull(str);
        assertTrue(str.contains("10"));
        assertTrue(str.contains("20"));
    }
}
