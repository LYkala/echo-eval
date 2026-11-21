/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.login;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * LoginStatus 枚举测试
 */
public class LoginStatusTest {

    @Test
    public void testEnumValues() {
        LoginStatus[] values = LoginStatus.values();
        assertEquals(5, values.length);
    }

    @Test
    public void testGetValue() {
        assertEquals("idle", LoginStatus.IDLE.getValue());
        assertEquals("authing", LoginStatus.AUTHING.getValue());
        assertEquals("connecting", LoginStatus.CONNECTING.getValue());
        assertEquals("connected", LoginStatus.CONNECTED.getValue());
        assertEquals("disconnected", LoginStatus.DISCONNECTED.getValue());
    }

    @Test
    public void testFromValueExact() {
        assertEquals(LoginStatus.IDLE, LoginStatus.fromValue("idle"));
        assertEquals(LoginStatus.AUTHING, LoginStatus.fromValue("authing"));
        assertEquals(LoginStatus.CONNECTING, LoginStatus.fromValue("connecting"));
        assertEquals(LoginStatus.CONNECTED, LoginStatus.fromValue("connected"));
        assertEquals(LoginStatus.DISCONNECTED, LoginStatus.fromValue("disconnected"));
    }

    @Test
    public void testFromValueCaseInsensitive() {
        assertEquals(LoginStatus.IDLE, LoginStatus.fromValue("IDLE"));
        assertEquals(LoginStatus.AUTHING, LoginStatus.fromValue("AUTHING"));
        assertEquals(LoginStatus.CONNECTED, LoginStatus.fromValue("Connected"));
        assertEquals(LoginStatus.DISCONNECTED, LoginStatus.fromValue("DISCONNECTED"));
    }

    @Test
    public void testFromValueNull() {
        assertNull(LoginStatus.fromValue(null));
    }

    @Test
    public void testFromValueUnknown() {
        assertNull(LoginStatus.fromValue("unknown"));
        assertNull(LoginStatus.fromValue(""));
        assertNull(LoginStatus.fromValue("login"));
    }

    @Test
    public void testEnumName() {
        assertEquals("IDLE", LoginStatus.IDLE.name());
        assertEquals("CONNECTED", LoginStatus.CONNECTED.name());
        assertEquals("DISCONNECTED", LoginStatus.DISCONNECTED.name());
    }

    @Test
    public void testEnumOrdinal() {
        assertEquals(0, LoginStatus.IDLE.ordinal());
        assertEquals(1, LoginStatus.AUTHING.ordinal());
        assertEquals(2, LoginStatus.CONNECTING.ordinal());
        assertEquals(3, LoginStatus.CONNECTED.ordinal());
        assertEquals(4, LoginStatus.DISCONNECTED.ordinal());
    }

    @Test
    public void testValueOf() {
        assertEquals(LoginStatus.IDLE, LoginStatus.valueOf("IDLE"));
        assertEquals(LoginStatus.CONNECTED, LoginStatus.valueOf("CONNECTED"));
    }

    @Test
    public void testFromValueRoundTrip() {
        for (LoginStatus status : LoginStatus.values()) {
            assertNotNull(LoginStatus.fromValue(status.getValue()));
            assertEquals(status, LoginStatus.fromValue(status.getValue()));
        }
    }
}
