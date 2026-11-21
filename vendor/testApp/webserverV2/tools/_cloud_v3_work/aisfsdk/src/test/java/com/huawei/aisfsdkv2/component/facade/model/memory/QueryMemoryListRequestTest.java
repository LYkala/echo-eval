/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.memory;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * QueryMemoryListRequest 测试
 */
public class QueryMemoryListRequestTest {

    @Test
    public void testDefaultValues() {
        QueryMemoryListRequest req = new QueryMemoryListRequest();
        assertEquals("all", req.getCategory());
        assertEquals("DESC", req.getOrder());
        assertEquals(50, req.getLimit());
        assertEquals(1, req.getPage());
    }

    @Test
    public void testConstructorWithCategory() {
        QueryMemoryListRequest req = new QueryMemoryListRequest("core");
        assertEquals("core", req.getCategory());
        assertEquals("DESC", req.getOrder());
        assertEquals(50, req.getLimit());
        assertEquals(1, req.getPage());
    }

    @Test
    public void testConstructorWithAllParams() {
        QueryMemoryListRequest req = new QueryMemoryListRequest("semantic", 2, 20);
        assertEquals("semantic", req.getCategory());
        assertEquals(2, req.getPage());
        assertEquals(20, req.getLimit());
    }

    @Test
    public void testSetAndGetCategory() {
        QueryMemoryListRequest req = new QueryMemoryListRequest();
        req.setCategory("episodic");
        assertEquals("episodic", req.getCategory());
    }

    @Test
    public void testSetAndGetOrder() {
        QueryMemoryListRequest req = new QueryMemoryListRequest();
        req.setOrder("ASC");
        assertEquals("ASC", req.getOrder());
    }

    @Test
    public void testSetAndGetLimit() {
        QueryMemoryListRequest req = new QueryMemoryListRequest();
        req.setLimit(100);
        assertEquals(100, req.getLimit());
    }

    @Test
    public void testSetAndGetPage() {
        QueryMemoryListRequest req = new QueryMemoryListRequest();
        req.setPage(5);
        assertEquals(5, req.getPage());
    }

    @Test
    public void testToString() {
        QueryMemoryListRequest req = new QueryMemoryListRequest("common", 1, 10);
        String str = req.toString();
        assertNotNull(str);
        assertTrue(str.contains("common"));
        assertTrue(str.contains("1"));
        assertTrue(str.contains("10"));
    }
}
