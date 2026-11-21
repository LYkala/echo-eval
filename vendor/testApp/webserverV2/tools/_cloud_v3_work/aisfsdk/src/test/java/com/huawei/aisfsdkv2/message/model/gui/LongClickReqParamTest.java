/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * LongClickReqParam 测试
 */
public class LongClickReqParamTest {

    private LongClickReqParam param;

    @Before
    public void setUp() {
        param = new LongClickReqParam();
    }

    @Test
    public void testDefaultValues() {
        assertEquals(-1, param.getStartX());
        assertEquals(-1, param.getStartY());
        assertEquals(1000, param.getDuration());
        assertTrue(param.isNeedScreenShot());
        assertEquals(2, param.getMaxScreenshotTime());
    }

    @Test
    public void testParameterizedConstructor() {
        LongClickReqParam p = new LongClickReqParam(100, 200, 2000);
        assertEquals(100, p.getStartX());
        assertEquals(200, p.getStartY());
        assertEquals(2000, p.getDuration());
    }

    @Test
    public void testSetAndGetAllFields() {
        param.setStartX(50);
        param.setStartY(60);
        param.setDuration(1500);
        param.setNeedScreenShot(false);
        param.setMaxScreenshotTime(3);
        assertEquals(50, param.getStartX());
        assertEquals(60, param.getStartY());
        assertEquals(1500, param.getDuration());
        assertEquals(false, param.isNeedScreenShot());
        assertEquals(3, param.getMaxScreenshotTime());
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
