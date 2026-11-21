/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * HashUtil 单元测试
 */
public class HashUtilTest {

    @Test
    public void testSha256_NotNull() {
        String result = HashUtil.sha256("hello");
        assertNotNull(result);
    }

    @Test
    public void testSha256_Length64() {
        String result = HashUtil.sha256("hello");
        assertEquals(64, result.length());
    }

    @Test
    public void testSha256_KnownValue() {
        // SHA256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        String result = HashUtil.sha256("hello");
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", result);
    }

    @Test
    public void testSha256_Empty_ReturnsEmpty() {
        assertEquals("", HashUtil.sha256(""));
    }

    @Test
    public void testSha256_Null_ReturnsEmpty() {
        assertEquals("", HashUtil.sha256(null));
    }

    @Test
    public void testSha256_LowercaseOutput() {
        String result = HashUtil.sha256("test");
        assertEquals(result, result.toLowerCase());
    }

    @Test
    public void testSha256_Deterministic() {
        assertEquals(HashUtil.sha256("abc"), HashUtil.sha256("abc"));
    }

    @Test
    public void testSha256_DifferentInputs_DifferentOutputs() {
        assertFalse(HashUtil.sha256("abc").equals(HashUtil.sha256("ABC")));
    }

    @Test
    public void testSha256_ChineseCharacters() {
        String result = HashUtil.sha256("中文");
        assertNotNull(result);
        assertEquals(64, result.length());
    }

    @Test
    public void testSha256_SpecialCharacters() {
        String result = HashUtil.sha256("!@#$%^&*()");
        assertNotNull(result);
        assertEquals(64, result.length());
    }

    @Test
    public void testSha256_OnlyHexChars() {
        String result = HashUtil.sha256("test");
        assertTrue(result.matches("[0-9a-f]+"));
    }
}
