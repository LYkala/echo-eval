/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.memory;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * MemoryException 测试
 */
public class MemoryExceptionTest {

    @Test
    public void testConstructorAndGetCode() {
        MemoryException ex = new MemoryException(404, "Not Found");
        assertEquals(404, ex.getCode());
        assertEquals("Not Found", ex.getMessage());
    }

    @Test
    public void testCodeZero() {
        MemoryException ex = new MemoryException(0, "Success");
        assertEquals(0, ex.getCode());
    }

    @Test
    public void testNegativeCode() {
        MemoryException ex = new MemoryException(-1, "Unknown error");
        assertEquals(-1, ex.getCode());
    }

    @Test
    public void testIsException() {
        MemoryException ex = new MemoryException(500, "Server error");
        assertNotNull(ex);
        assertEquals(500, ex.getCode());
    }

    @Test
    public void testThrownAndCaught() {
        try {
            throw new MemoryException(403, "Forbidden");
        } catch (MemoryException e) {
            assertEquals(403, e.getCode());
            assertEquals("Forbidden", e.getMessage());
        }
    }
}
