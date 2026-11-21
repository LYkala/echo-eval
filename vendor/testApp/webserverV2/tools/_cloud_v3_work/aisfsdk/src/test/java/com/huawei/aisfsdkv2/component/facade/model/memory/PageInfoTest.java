/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.memory;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * PageInfo 测试
 */
public class PageInfoTest {

    @Test
    public void testDefaultConstructor() {
        PageInfo page = new PageInfo();
        assertEquals(0, page.getPage());
        assertEquals(0, page.getLimit());
        assertEquals(0, page.getTotalSize());
        assertFalse(page.getHasNext());
    }

    @Test
    public void testParameterizedConstructor() {
        PageInfo page = new PageInfo(2, 20, 100, true);
        assertEquals(2, page.getPage());
        assertEquals(20, page.getLimit());
        assertEquals(100, page.getTotalSize());
        assertTrue(page.getHasNext());
    }

    @Test
    public void testSetAndGetPage() {
        PageInfo page = new PageInfo();
        page.setPage(3);
        assertEquals(3, page.getPage());
    }

    @Test
    public void testSetAndGetLimit() {
        PageInfo page = new PageInfo();
        page.setLimit(50);
        assertEquals(50, page.getLimit());
    }

    @Test
    public void testSetAndGetTotalSize() {
        PageInfo page = new PageInfo();
        page.setTotalSize(200);
        assertEquals(200, page.getTotalSize());
    }

    @Test
    public void testSetHasNextTrue() {
        PageInfo page = new PageInfo();
        page.setHasNext(true);
        assertTrue(page.getHasNext());
    }

    @Test
    public void testSetHasNextFalse() {
        PageInfo page = new PageInfo(1, 10, 5, true);
        page.setHasNext(false);
        assertFalse(page.getHasNext());
    }

    @Test
    public void testFirstPageNoNext() {
        PageInfo page = new PageInfo(1, 10, 5, false);
        assertEquals(1, page.getPage());
        assertFalse(page.getHasNext());
    }
}
