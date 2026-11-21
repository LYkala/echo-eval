/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * ExceptionRespParam 测试
 */
public class ExceptionRespParamTest {

    private ExceptionRespParam param;

    @Before
    public void setUp() {
        param = new ExceptionRespParam();
    }

    @Test
    public void testDefaultValues() {
        assertEquals(0, param.getErrorCode());
        assertNull(param.getErrorDesc());
    }

    @Test
    public void testSetAndGetErrorCode() {
        param.setErrorCode(500);
        assertEquals(500, param.getErrorCode());
    }

    @Test
    public void testSetAndGetErrorDesc() {
        param.setErrorDesc("Internal error");
        assertEquals("Internal error", param.getErrorDesc());
    }

    @Test
    public void testSetNullErrorDesc() {
        param.setErrorDesc(null);
        assertNull(param.getErrorDesc());
    }

    @Test
    public void testSetEmptyErrorDesc() {
        param.setErrorDesc("");
        assertEquals("", param.getErrorDesc());
    }

    @Test
    public void testFieldOverwrite() {
        param.setErrorCode(400);
        assertEquals(400, param.getErrorCode());
        param.setErrorCode(404);
        assertEquals(404, param.getErrorCode());
    }

    @Test
    public void testNegativeErrorCode() {
        param.setErrorCode(-1);
        assertEquals(-1, param.getErrorCode());
    }
}
