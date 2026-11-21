/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * UploadScreenshotRespParam 测试
 */
public class UploadScreenshotRespParamTest {

    private UploadScreenshotRespParam param;

    @Before
    public void setUp() {
        param = new UploadScreenshotRespParam();
    }

    @Test
    public void testDefaultValues() {
        assertEquals(0, param.getResolutionW());
        assertEquals(0, param.getResolutionH());
        assertFalse(param.isReScreenShot());
        assertEquals(0, param.getChangeCnt());
        assertNull(param.getForegroundAppPackageName());
    }

    @Test
    public void testSetAndGetResolutionW() {
        param.setResolutionW(1080);
        assertEquals(1080, param.getResolutionW());
    }

    @Test
    public void testSetAndGetResolutionH() {
        param.setResolutionH(1920);
        assertEquals(1920, param.getResolutionH());
    }

    @Test
    public void testSetAndGetIsReScreenShot() {
        param.setReScreenShot(true);
        assertTrue(param.isReScreenShot());
        param.setReScreenShot(false);
        assertFalse(param.isReScreenShot());
    }

    @Test
    public void testSetAndGetChangeCnt() {
        param.setChangeCnt(42L);
        assertEquals(42L, param.getChangeCnt());
    }

    @Test
    public void testSetAndGetForegroundAppPackageName() {
        param.setForegroundAppPackageName("com.example.app");
        assertEquals("com.example.app", param.getForegroundAppPackageName());
    }
}
