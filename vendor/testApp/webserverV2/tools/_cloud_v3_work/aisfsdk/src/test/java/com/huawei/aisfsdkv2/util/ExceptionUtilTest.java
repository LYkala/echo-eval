/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * ExceptionUtil 测试
 */
public class ExceptionUtilTest {

    @Test
    public void testGetStackTrace_Null() {
        String result = ExceptionUtil.getStackTrace(null);
        assertEquals("", result);
    }

    @Test
    public void testGetStackTrace_Exception() {
        Exception e = new RuntimeException("test error");
        String result = ExceptionUtil.getStackTrace(e);
        assertNotNull(result);
        assertTrue(result.contains("RuntimeException"));
        assertTrue(result.contains("test error"));
    }

    @Test
    public void testGetStackTrace_WithCause() {
        Exception cause = new IllegalArgumentException("cause");
        Exception e = new RuntimeException("wrapper", cause);
        String result = ExceptionUtil.getStackTrace(e);
        assertNotNull(result);
        assertTrue(result.contains("RuntimeException"));
        assertTrue(result.contains("IllegalArgumentException"));
    }

    @Test
    public void testGetStackTrace_StackTracePresent() {
        Exception e = new Exception();
        e.fillInStackTrace();
        String result = ExceptionUtil.getStackTrace(e);
        assertNotNull(result);
        assertTrue(result.length() > 0);
    }
}
