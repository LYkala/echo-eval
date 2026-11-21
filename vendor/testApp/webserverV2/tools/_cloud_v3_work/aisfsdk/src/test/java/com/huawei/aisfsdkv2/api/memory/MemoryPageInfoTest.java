/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.memory;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * MemoryPageInfo 测试
 */
public class MemoryPageInfoTest {

    private MemoryPageInfo pageInfo;

    @Before
    public void setUp() {
        pageInfo = new MemoryPageInfo();
    }

    @Test
    public void testDefaultValues() {
        assertEquals(0, pageInfo.getPage());
        assertEquals(0, pageInfo.getLimit());
        assertEquals(0, pageInfo.getTotalSize());
        assertFalse(pageInfo.getHasNext());
    }

    @Test
    public void testSetAndGetPage() {
        pageInfo.setPage(2);
        assertEquals(2, pageInfo.getPage());
    }

    @Test
    public void testSetAndGetLimit() {
        pageInfo.setLimit(20);
        assertEquals(20, pageInfo.getLimit());
    }

    @Test
    public void testSetAndGetTotalSize() {
        pageInfo.setTotalSize(100);
        assertEquals(100, pageInfo.getTotalSize());
    }

    @Test
    public void testSetHasNextTrue() {
        pageInfo.setHasNext(true);
        assertTrue(pageInfo.getHasNext());
    }

    @Test
    public void testSetHasNextFalse() {
        pageInfo.setHasNext(true);
        pageInfo.setHasNext(false);
        assertFalse(pageInfo.getHasNext());
    }

    @Test
    public void testToString() {
        pageInfo.setPage(1);
        pageInfo.setLimit(10);
        pageInfo.setTotalSize(50);
        pageInfo.setHasNext(true);
        String str = pageInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("1"));
        assertTrue(str.contains("10"));
        assertTrue(str.contains("50"));
        assertTrue(str.contains("true"));
    }

    @Test
    public void testToStringFormat() {
        String str = pageInfo.toString();
        assertTrue(str.contains("MemoryPageInfo"));
        assertTrue(str.contains("page"));
        assertTrue(str.contains("limit"));
        assertTrue(str.contains("totalSize"));
        assertTrue(str.contains("hasNext"));
    }
}
