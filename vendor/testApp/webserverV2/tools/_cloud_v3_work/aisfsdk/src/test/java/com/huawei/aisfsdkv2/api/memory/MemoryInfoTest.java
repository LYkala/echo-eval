/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.memory;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * MemoryInfo 测试
 */
public class MemoryInfoTest {

    private MemoryInfo memoryInfo;

    @Before
    public void setUp() {
        memoryInfo = new MemoryInfo();
    }

    @Test
    public void testDefaultConstructor() {
        assertNull(memoryInfo.getId());
        assertNull(memoryInfo.getContent());
        assertNull(memoryInfo.getDetail());
        assertNull(memoryInfo.getDate());
        assertNull(memoryInfo.getMemType());
    }

    @Test
    public void testSetAndGetId() {
        memoryInfo.setId("mem-001");
        assertEquals("mem-001", memoryInfo.getId());
    }

    @Test
    public void testSetAndGetContent() {
        memoryInfo.setContent("User likes coffee");
        assertEquals("User likes coffee", memoryInfo.getContent());
    }

    @Test
    public void testSetAndGetDetail() {
        memoryInfo.setDetail("Detailed description");
        assertEquals("Detailed description", memoryInfo.getDetail());
    }

    @Test
    public void testSetAndGetDate() {
        memoryInfo.setDate("2026-04-13");
        assertEquals("2026-04-13", memoryInfo.getDate());
    }

    @Test
    public void testSetAndGetMemType() {
        memoryInfo.setMemType("core");
        assertEquals("core", memoryInfo.getMemType());
    }

    @Test
    public void testToString() {
        memoryInfo.setId("id-1");
        memoryInfo.setContent("content text");
        memoryInfo.setDetail("detail text");
        memoryInfo.setDate("2026-01-01");
        memoryInfo.setMemType("common");
        String str = memoryInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("id-1"));
        assertTrue(str.contains("content text"));
        assertTrue(str.contains("detail text"));
        assertTrue(str.contains("2026-01-01"));
        assertTrue(str.contains("common"));
    }

    @Test
    public void testToStringWithNulls() {
        String str = memoryInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("MemoryInfo"));
    }

    @Test
    public void testSetNullValues() {
        memoryInfo.setId(null);
        memoryInfo.setContent(null);
        memoryInfo.setDetail(null);
        memoryInfo.setDate(null);
        memoryInfo.setMemType(null);
        assertNull(memoryInfo.getId());
        assertNull(memoryInfo.getContent());
        assertNull(memoryInfo.getDetail());
        assertNull(memoryInfo.getDate());
        assertNull(memoryInfo.getMemType());
    }
}
