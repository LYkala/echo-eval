/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.memory;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * BaseMemInfo 测试
 */
public class BaseMemInfoTest {

    @Test
    public void testDefaultConstructor() {
        BaseMemInfo info = new BaseMemInfo();
        assertNull(info.getId());
        assertNull(info.getContent());
        assertNull(info.getDate());
        assertNull(info.getDetail());
        assertNull(info.getMemType());
    }

    @Test
    public void testParameterizedConstructor() {
        BaseMemInfo info = new BaseMemInfo("id-1", "content text", "2026-04-13");
        assertEquals("id-1", info.getId());
        assertEquals("content text", info.getContent());
        assertEquals("2026-04-13", info.getDate());
    }

    @Test
    public void testSetAndGetId() {
        BaseMemInfo info = new BaseMemInfo();
        info.setId("mem-001");
        assertEquals("mem-001", info.getId());
    }

    @Test
    public void testSetAndGetContent() {
        BaseMemInfo info = new BaseMemInfo();
        info.setContent("user likes reading");
        assertEquals("user likes reading", info.getContent());
    }

    @Test
    public void testSetAndGetDate() {
        BaseMemInfo info = new BaseMemInfo();
        info.setDate("2026-01-15");
        assertEquals("2026-01-15", info.getDate());
    }

    @Test
    public void testSetAndGetDetail() {
        BaseMemInfo info = new BaseMemInfo();
        info.setDetail("some detail");
        assertEquals("some detail", info.getDetail());
    }

    @Test
    public void testSetAndGetMemType() {
        BaseMemInfo info = new BaseMemInfo();
        info.setMemType("core");
        assertEquals("core", info.getMemType());
    }

    @Test
    public void testParameterizedConstructorDetailIsNull() {
        BaseMemInfo info = new BaseMemInfo("id", "content", "2026-01-01");
        assertNull(info.getDetail());
        assertNull(info.getMemType());
    }

    @Test
    public void testSetAllFields() {
        BaseMemInfo info = new BaseMemInfo();
        info.setId("id-x");
        info.setContent("content-x");
        info.setDate("2026-04-01");
        info.setDetail("detail-x");
        info.setMemType("episodic");
        assertEquals("id-x", info.getId());
        assertEquals("content-x", info.getContent());
        assertEquals("2026-04-01", info.getDate());
        assertEquals("detail-x", info.getDetail());
        assertEquals("episodic", info.getMemType());
    }
}
