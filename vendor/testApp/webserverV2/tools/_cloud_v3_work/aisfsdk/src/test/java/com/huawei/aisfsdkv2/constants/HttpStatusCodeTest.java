/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.constants;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * HttpStatusCode 测试
 */
public class HttpStatusCodeTest {

    @Test
    public void testOkValue() {
        assertEquals(200, HttpStatusCode.OK);
    }

    @Test
    public void testSwitchingProtocolsValue() {
        assertEquals(101, HttpStatusCode.SWITCHING_PROTOCOLS);
    }

    @Test
    public void testUnauthorizedValue() {
        assertEquals(401, HttpStatusCode.UNAUTHORIZED);
    }

    @Test
    public void testProxyAuthRequiredValue() {
        assertEquals(407, HttpStatusCode.PROXY_AUTH_REQUIRED);
    }
}
