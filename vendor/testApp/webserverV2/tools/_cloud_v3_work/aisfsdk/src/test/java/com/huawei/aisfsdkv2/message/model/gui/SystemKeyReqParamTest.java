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
 * SystemKeyReqParam 测试
 */
public class SystemKeyReqParamTest {

    private SystemKeyReqParam param;

    @Before
    public void setUp() {
        param = new SystemKeyReqParam();
    }

    @Test
    public void testDefaultValues() {
        assertFalse(param.isNeedScreenShot());
        assertEquals(2, param.getMaxScreenshotTime());
    }

    @Test
    public void testSetAndGetNeedScreenShot() {
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
        param.setNeedScreenShot(true);
        param.setMaxScreenshotTime(3);
        String str = param.toString();
        assertNotNull(str);
        assertTrue(str.contains("true"));
        assertTrue(str.contains("3"));
    }
}
