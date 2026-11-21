/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.memory;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * MemCategoryEnum 测试
 */
public class MemCategoryEnumTest {

    @Test
    public void testEnumCount() {
        assertEquals(6, MemCategoryEnum.values().length);
    }

    @Test
    public void testGetValue() {
        assertEquals("all", MemCategoryEnum.ALL.getValue());
        assertEquals("common", MemCategoryEnum.COMMON.getValue());
        assertEquals("core", MemCategoryEnum.CORE.getValue());
        assertEquals("semantic", MemCategoryEnum.SEMANTIC.getValue());
        assertEquals("episodic", MemCategoryEnum.EPISODIC.getValue());
        assertEquals("userInput", MemCategoryEnum.USER_INPUT.getValue());
    }

    @Test
    public void testFromValueByStringValue() {
        assertEquals(MemCategoryEnum.ALL, MemCategoryEnum.fromValue("all"));
        assertEquals(MemCategoryEnum.COMMON, MemCategoryEnum.fromValue("common"));
        assertEquals(MemCategoryEnum.CORE, MemCategoryEnum.fromValue("core"));
        assertEquals(MemCategoryEnum.SEMANTIC, MemCategoryEnum.fromValue("semantic"));
        assertEquals(MemCategoryEnum.EPISODIC, MemCategoryEnum.fromValue("episodic"));
        assertEquals(MemCategoryEnum.USER_INPUT, MemCategoryEnum.fromValue("userInput"));
    }

    @Test
    public void testFromValueByEnumName() {
        assertEquals(MemCategoryEnum.ALL, MemCategoryEnum.fromValue("ALL"));
        assertEquals(MemCategoryEnum.COMMON, MemCategoryEnum.fromValue("COMMON"));
        assertEquals(MemCategoryEnum.CORE, MemCategoryEnum.fromValue("CORE"));
        assertEquals(MemCategoryEnum.USER_INPUT, MemCategoryEnum.fromValue("USER_INPUT"));
    }

    @Test
    public void testFromValueCaseInsensitive() {
        assertEquals(MemCategoryEnum.ALL, MemCategoryEnum.fromValue("ALL"));
        assertEquals(MemCategoryEnum.ALL, MemCategoryEnum.fromValue("all"));
        assertEquals(MemCategoryEnum.COMMON, MemCategoryEnum.fromValue("Common"));
    }

    @Test
    public void testFromValueNull() {
        // null 返回 ALL
        assertEquals(MemCategoryEnum.ALL, MemCategoryEnum.fromValue(null));
    }

    @Test
    public void testFromValueEmpty() {
        // 空字符串返回 ALL
        assertEquals(MemCategoryEnum.ALL, MemCategoryEnum.fromValue(""));
    }

    @Test
    public void testFromValueUnknown() {
        // 未匹配返回 ALL
        assertEquals(MemCategoryEnum.ALL, MemCategoryEnum.fromValue("unknown"));
        assertEquals(MemCategoryEnum.ALL, MemCategoryEnum.fromValue("xyz"));
    }

    @Test
    public void testFromValueRoundTrip() {
        for (MemCategoryEnum e : MemCategoryEnum.values()) {
            assertNotNull(MemCategoryEnum.fromValue(e.getValue()));
            assertEquals(e, MemCategoryEnum.fromValue(e.getValue()));
        }
    }
}
