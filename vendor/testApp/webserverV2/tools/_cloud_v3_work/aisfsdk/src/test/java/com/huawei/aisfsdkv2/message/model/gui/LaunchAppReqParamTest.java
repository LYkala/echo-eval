/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * LaunchAppReqParam 测试
 */
public class LaunchAppReqParamTest {

    private LaunchAppReqParam param;

    @Before
    public void setUp() {
        param = new LaunchAppReqParam();
    }

    @Test
    public void testDefaultValues() {
        assertNull(param.getAppName());
        assertEquals(0, param.getLaunchFlag());
        assertEquals(8, param.getScreenShotMaxTime());
    }

    @Test
    public void testSetAndGetAppName() {
        param.setAppName("com.example.app");
        assertEquals("com.example.app", param.getAppName());
    }

    @Test
    public void testSetAndGetLaunchFlag() {
        param.setLaunchFlag(1);
        assertEquals(1, param.getLaunchFlag());
    }

    @Test
    public void testSetAndGetScreenShotMaxTime() {
        param.setScreenShotMaxTime(10);
        assertEquals(10, param.getScreenShotMaxTime());
    }

    @Test
    public void testToString() {
        param.setAppName("com.test");
        String str = param.toString();
        assertNotNull(str);
        assertTrue(str.contains("com.test"));
        assertTrue(str.contains("LaunchAppReqParam"));
    }
}
