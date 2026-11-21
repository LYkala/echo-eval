/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.base;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * ServerAuthModeEnum 测试
 */
public class ServerAuthModeEnumTest {

    @Test
    public void testEnumCount() {
        assertEquals(2, ServerAuthModeEnum.values().length);
    }

    @Test
    public void testApigwTls() {
        assertTrue(ServerAuthModeEnum.APIGW.isTls());
    }

    @Test
    public void testAcmNoTls() {
        assertFalse(ServerAuthModeEnum.ACM.isTls());
    }

    @Test
    public void testValueOf() {
        assertEquals(ServerAuthModeEnum.APIGW, ServerAuthModeEnum.valueOf("APIGW"));
        assertEquals(ServerAuthModeEnum.ACM, ServerAuthModeEnum.valueOf("ACM"));
    }

    @Test
    public void testEnumNames() {
        assertEquals("APIGW", ServerAuthModeEnum.APIGW.name());
        assertEquals("ACM", ServerAuthModeEnum.ACM.name());
    }
}
