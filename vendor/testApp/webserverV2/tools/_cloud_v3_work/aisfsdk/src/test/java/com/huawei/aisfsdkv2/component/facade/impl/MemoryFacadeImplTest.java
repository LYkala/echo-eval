/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.impl;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * MemoryFacadeImpl 测试 - 仅测试模型类
 * 私有解析方法依赖构造器初始化的 httpClient，无法在单元测试中创建实例
 */
public class MemoryFacadeImplTest {

    // ======= Model class tests =======

    @Test
    public void testMemoryException_CodeAndMessage() {
        com.huawei.aisfsdkv2.component.facade.model.memory.MemoryException ex = 
                new com.huawei.aisfsdkv2.component.facade.model.memory.MemoryException(500, "Server error");
        assertEquals(500, ex.getCode());
        assertEquals("Server error", ex.getMessage());
    }

    @Test
    public void testPageInfo_DefaultConstructor() {
        com.huawei.aisfsdkv2.component.facade.model.memory.PageInfo info = 
                new com.huawei.aisfsdkv2.component.facade.model.memory.PageInfo();
        assertEquals(0, info.getPage());
        assertEquals(0, info.getLimit());
    }

    @Test
    public void testPageInfo_AllFields() {
        com.huawei.aisfsdkv2.component.facade.model.memory.PageInfo info = 
                new com.huawei.aisfsdkv2.component.facade.model.memory.PageInfo(1, 10, 100, true);
        assertEquals(1, info.getPage());
        assertEquals(10, info.getLimit());
        assertEquals(100, info.getTotalSize());
        assertTrue(info.getHasNext());
    }

    @Test
    public void testBaseMemInfo_Fields() {
        com.huawei.aisfsdkv2.component.facade.model.memory.BaseMemInfo info = 
                new com.huawei.aisfsdkv2.component.facade.model.memory.BaseMemInfo();
        info.setId("id1");
        info.setContent("content");
        info.setDetail("detail");
        info.setDate("2024-01-01");
        info.setMemType("general");
        assertEquals("id1", info.getId());
        assertEquals("content", info.getContent());
    }

    @Test
    public void testMemoryListInfo_Fields() {
        com.huawei.aisfsdkv2.component.facade.model.memory.MemoryListInfo info = 
                new com.huawei.aisfsdkv2.component.facade.model.memory.MemoryListInfo();
        assertNotNull(info.getMemInfoList());
    }

    @Test
    public void testPersonalClassifyMemoryResult_Fields() {
        com.huawei.aisfsdkv2.component.facade.model.memory.PersonalClassifyMemoryResult result = 
                new com.huawei.aisfsdkv2.component.facade.model.memory.PersonalClassifyMemoryResult();
        // categoryMap is not initialized in constructor
        result.setCategoryMap(new java.util.HashMap<>());
        assertNotNull(result.getCategoryMap());
    }

    private void assertTrue(boolean condition) {
        if (!condition) throw new AssertionError();
    }
}
