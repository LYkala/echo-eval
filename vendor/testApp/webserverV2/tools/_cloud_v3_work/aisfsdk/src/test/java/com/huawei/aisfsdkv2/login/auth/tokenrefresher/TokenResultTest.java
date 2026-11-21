/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.login.auth.tokenrefresher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TokenResult 测试
 */
public class TokenResultTest {

    @Test
    public void testSuccess_Fields() {
        TokenResult result = TokenResult.success("my-token", 7200);
        assertTrue(result.isSuccess());
        assertEquals("my-token", result.getToken());
        assertEquals(7200, result.getExpireTime());
        assertNull(result.getMessage());
    }

    @Test
    public void testFailure_Fields() {
        TokenResult result = TokenResult.failure("connection refused");
        assertFalse(result.isSuccess());
        assertNull(result.getToken());
        assertEquals(0, result.getExpireTime());
        assertEquals("connection refused", result.getMessage());
    }

    @Test
    public void testConstructor_AllFields() {
        TokenResult result = new TokenResult(true, "tok", 3600, null);
        assertTrue(result.isSuccess());
        assertEquals("tok", result.getToken());
        assertEquals(3600, result.getExpireTime());
        assertNull(result.getMessage());
    }

    @Test
    public void testFailure_NullMessage() {
        TokenResult result = TokenResult.failure(null);
        assertFalse(result.isSuccess());
        assertNull(result.getMessage());
    }

    @Test
    public void testSuccess_ZeroExpire() {
        TokenResult result = TokenResult.success("tok", 0);
        assertTrue(result.isSuccess());
        assertEquals(0, result.getExpireTime());
    }
}
