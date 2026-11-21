/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.memory;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * MemoryListResult 测试
 */
public class MemoryListResultTest {

    private MemoryListResult result;

    @Before
    public void setUp() {
        result = new MemoryListResult();
    }

    @Test
    public void testDefaultConstructor() {
        assertNotNull(result.getMemoryList());
        assertTrue(result.getMemoryList().isEmpty());
        assertNotNull(result.getPageInfo());
    }

    @Test
    public void testSetAndGetMemoryList() {
        List<MemoryInfo> list = new ArrayList<>();
        MemoryInfo info = new MemoryInfo();
        info.setId("m-1");
        list.add(info);
        result.setMemoryList(list);
        assertEquals(1, result.getMemoryList().size());
        assertEquals("m-1", result.getMemoryList().get(0).getId());
    }

    @Test
    public void testSetAndGetPageInfo() {
        MemoryPageInfo pageInfo = new MemoryPageInfo();
        pageInfo.setPage(1);
        pageInfo.setLimit(10);
        pageInfo.setTotalSize(50);
        result.setPageInfo(pageInfo);
        assertNotNull(result.getPageInfo());
        assertEquals(1, result.getPageInfo().getPage());
        assertEquals(10, result.getPageInfo().getLimit());
        assertEquals(50, result.getPageInfo().getTotalSize());
    }

    @Test
    public void testSetMultipleMemories() {
        List<MemoryInfo> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            MemoryInfo info = new MemoryInfo();
            info.setId("m-" + i);
            list.add(info);
        }
        result.setMemoryList(list);
        assertEquals(5, result.getMemoryList().size());
    }

    @Test
    public void testToString() {
        String str = result.toString();
        assertNotNull(str);
        assertTrue(str.contains("MemoryListResult"));
        assertTrue(str.contains("memoryList"));
        assertTrue(str.contains("pageInfo"));
    }

    @Test
    public void testSetNullMemoryList() {
        result.setMemoryList(null);
        // 可以设置为 null，不抛异常
        assertNotNull(result);
    }
}
