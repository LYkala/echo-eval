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
 * ClickReqParam 测试
 */
public class ClickReqParamTest {

    private ClickReqParam param;

    @Before
    public void setUp() {
        param = new ClickReqParam();
    }

    @Test
    public void testDefaultValues() {
        assertEquals(-1, param.getStartX());
        assertEquals(-1, param.getStartY());
        assertTrue(param.isNeedScreenShot());
        assertEquals(2, param.getMaxScreenshotTime());
    }

    @Test
    public void testConstructorWithCoordinates() {
        ClickReqParam p = new ClickReqParam(100, 200);
        assertEquals(100, p.getStartX());
        assertEquals(200, p.getStartY());
    }

    @Test
    public void testSetAndGetStartX() {
        param.setStartX(500);
        assertEquals(500, param.getStartX());
    }

    @Test
    public void testSetAndGetStartY() {
        param.setStartY(300);
        assertEquals(300, param.getStartY());
    }

    @Test
    public void testSetAndGetNeedScreenShot() {
        param.setNeedScreenShot(false);
        assertFalse(param.isNeedScreenShot());
        param.setNeedScreenShot(true);
        assertTrue(param.isNeedScreenShot());
    }

    @Test
    public void testSetAndGetMaxScreenshotTime() {
        param.setMaxScreenshotTime(5);
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
        assertTrue(str.contains("StartX"));
    }

    @Test
    public void testFieldOverwrite() {
        param.setStartX(100);
        assertEquals(100, param.getStartX());
        param.setStartX(200);
        assertEquals(200, param.getStartX());
    }

    @Test
    public void testZeroCoordinates() {
        param.setStartX(0);
        param.setStartY(0);
        assertEquals(0, param.getStartX());
        assertEquals(0, param.getStartY());
    }
}
