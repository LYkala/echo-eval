/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.login;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * LoginInfo 测试
 */
public class LoginInfoTest {

    @Test
    public void testConstructorAndGetters() {
        LoginInfo info = new LoginInfo(LoginStatus.CONNECTED, "user123", "device001", "password123");
        assertEquals(LoginStatus.CONNECTED, info.getStatus());
        assertEquals("user123", info.getUsername());
        assertEquals("device001", info.getIdentifyId());
        assertEquals("password123", info.getPassword());
    }

    @Test
    public void testNullUsernameDefaultsToEmpty() {
        LoginInfo info = new LoginInfo(LoginStatus.IDLE, null, "device001", "pass");
        assertEquals("", info.getUsername());
    }

    @Test
    public void testNullIdentifyIdDefaultsToEmpty() {
        LoginInfo info = new LoginInfo(LoginStatus.IDLE, "user", null, "pass");
        assertEquals("", info.getIdentifyId());
    }

    @Test
    public void testNullPassword() {
        LoginInfo info = new LoginInfo(LoginStatus.IDLE, "user", "device", null);
        assertNull(info.getPassword());
    }

    @Test
    public void testAllStatuses() {
        for (LoginStatus status : LoginStatus.values()) {
            LoginInfo info = new LoginInfo(status, "user", "device", "pass");
            assertEquals(status, info.getStatus());
        }
    }

    @Test
    public void testToStringContainsStatus() {
        LoginInfo info = new LoginInfo(LoginStatus.CONNECTED, "user123", "device001", "pass");
        String str = info.toString();
        assertNotNull(str);
        assertTrue(str.contains("CONNECTED"));
        assertTrue(str.contains("user123"));
        assertTrue(str.contains("device001"));
    }

    @Test
    public void testToStringMasksPassword() {
        LoginInfo info = new LoginInfo(LoginStatus.CONNECTED, "user", "device", "actualPassword");
        String str = info.toString();
        assertTrue(str.contains("***"));
    }

    @Test
    public void testToStringWithNullPassword() {
        LoginInfo info = new LoginInfo(LoginStatus.IDLE, "user", "device", null);
        String str = info.toString();
        assertTrue(str.contains("null"));
    }
}