/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * ScreenShotReqParam 测试
 */
public class ScreenShotReqParamTest {

    private ScreenShotReqParam param;

    @Before
    public void setUp() {
        param = new ScreenShotReqParam();
    }

    @Test
    public void testDefaultValues() {
        assertEquals(2, param.getMaxScreenshotTime());
    }

    @Test
    public void testConstructorWithMaxTime() {
        ScreenShotReqParam p = new ScreenShotReqParam(5);
        assertEquals(5, p.getMaxScreenshotTime());
    }

    @Test
    public void testSetAndGetMaxScreenshotTime() {
        param.setMaxScreenshotTime(10);
        assertEquals(10, param.getMaxScreenshotTime());
    }
}
