/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.memory;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * MemoryListInfo 测试
 */
public class MemoryListInfoTest {

    private MemoryListInfo memoryListInfo;

    @Before
    public void setUp() {
        memoryListInfo = new MemoryListInfo();
    }

    @Test
    public void testDefaultConstructor() {
        assertNotNull(memoryListInfo.getMemInfoList());
        assertTrue(memoryListInfo.getMemInfoList().isEmpty());
        assertNull(memoryListInfo.getPageInfo());
    }

    @Test
    public void testSetAndGetMemInfoList() {
        List<BaseMemInfo> list = new ArrayList<>();
        BaseMemInfo info = new BaseMemInfo("id-1", "content", "2026-01-01");
        list.add(info);
        memoryListInfo.setMemInfoList(list);
        assertEquals(1, memoryListInfo.getMemInfoList().size());
        assertEquals("id-1", memoryListInfo.getMemInfoList().get(0).getId());
    }

    @Test
    public void testSetMemInfoListNull() {
        memoryListInfo.setMemInfoList(null);
        assertNotNull(memoryListInfo.getMemInfoList());
        assertTrue(memoryListInfo.getMemInfoList().isEmpty());
    }

    @Test
    public void testSetAndGetPageInfo() {
        PageInfo pageInfo = new PageInfo(1, 10, 50, true);
        memoryListInfo.setPageInfo(pageInfo);
        assertNotNull(memoryListInfo.getPageInfo());
        assertEquals(1, memoryListInfo.getPageInfo().getPage());
        assertEquals(50, memoryListInfo.getPageInfo().getTotalSize());
    }

    @Test
    public void testMultipleMemInfoItems() {
        List<BaseMemInfo> list = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            list.add(new BaseMemInfo("id-" + i, "content-" + i, "2026-01-0" + (i + 1)));
        }
        memoryListInfo.setMemInfoList(list);
        assertEquals(3, memoryListInfo.getMemInfoList().size());
    }
}
