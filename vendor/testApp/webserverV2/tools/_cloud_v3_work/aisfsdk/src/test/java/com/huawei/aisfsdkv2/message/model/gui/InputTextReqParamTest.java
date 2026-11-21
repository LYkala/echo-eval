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
 * InputTextReqParam 测试
 */
public class InputTextReqParamTest {

    private InputTextReqParam param;

    @Before
    public void setUp() {
        param = new InputTextReqParam();
    }

    @Test
    public void testDefaultValues() {
        assertEquals(0, param.getStartX());
        assertEquals(0, param.getStartY());
        assertEquals("", param.getInputText());
        assertFalse(param.isNeedSearch());
        assertTrue(param.isNeedScreenShot());
        assertEquals(2, param.getMaxScreenshotTime());
    }

    @Test
    public void testConstructorWithText() {
        InputTextReqParam p = new InputTextReqParam("hello world");
        assertEquals("hello world", p.getInputText());
    }

    @Test
    public void testSetAndGetAllFields() {
        param.setStartX(10);
        param.setStartY(20);
        param.setInputText("test input");
        param.setNeedSearch(true);
        param.setNeedScreenShot(false);
        param.setMaxScreenshotTime(5);
        assertEquals(10, param.getStartX());
        assertEquals(20, param.getStartY());
        assertEquals("test input", param.getInputText());
        assertTrue(param.isNeedSearch());
        assertFalse(param.isNeedScreenShot());
        assertEquals(5, param.getMaxScreenshotTime());
    }

    @Test
    public void testToString() {
        param.setInputText("test");
        String str = param.toString();
        assertNotNull(str);
        assertTrue(str.contains("test"));
    }
}
