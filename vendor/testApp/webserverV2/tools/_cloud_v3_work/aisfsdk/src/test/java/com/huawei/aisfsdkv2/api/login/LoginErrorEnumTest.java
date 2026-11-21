/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.login;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * LoginErrorEnum 枚举测试
 */
public class LoginErrorEnumTest {

    @Test
    public void testEnumValues() {
        assertEquals(7, LoginErrorEnum.values().length);
    }

    @Test
    public void testGetCode() {
        assertEquals(0, LoginErrorEnum.NO_ERROR.getCode());
        assertEquals(1, LoginErrorEnum.ERR_PARAM.getCode());
        assertEquals(2, LoginErrorEnum.ERR_LOGIN_AUTH_FAILED.getCode());
        assertEquals(3, LoginErrorEnum.LOGIN_FAILURE_NEED_RETRY.getCode());
        assertEquals(4, LoginErrorEnum.LOGIN_FAILURE_NO_RETRY.getCode());
        assertEquals(5, LoginErrorEnum.ERR_ALREADY_LOGGED_IN.getCode());
        assertEquals(6, LoginErrorEnum.ERR_LOGIN_IN_PROGRESS.getCode());
    }

    @Test
    public void testGetDefaultMessage() {
        assertEquals("Success", LoginErrorEnum.NO_ERROR.getDefaultMessage());
        assertEquals("Username and password cannot be empty", LoginErrorEnum.ERR_PARAM.getDefaultMessage());
        assertEquals("Login authentication failed", LoginErrorEnum.ERR_LOGIN_AUTH_FAILED.getDefaultMessage());
        assertEquals("Login failed, need retry", LoginErrorEnum.LOGIN_FAILURE_NEED_RETRY.getDefaultMessage());
        assertEquals("Login failed, no retry needed", LoginErrorEnum.LOGIN_FAILURE_NO_RETRY.getDefaultMessage());
        assertEquals("Already logged in", LoginErrorEnum.ERR_ALREADY_LOGGED_IN.getDefaultMessage());
        assertEquals("Login already in progress", LoginErrorEnum.ERR_LOGIN_IN_PROGRESS.getDefaultMessage());
    }

    @Test
    public void testFromCodeAllValues() {
        assertEquals(LoginErrorEnum.NO_ERROR, LoginErrorEnum.fromCode(0));
        assertEquals(LoginErrorEnum.ERR_PARAM, LoginErrorEnum.fromCode(1));
        assertEquals(LoginErrorEnum.ERR_LOGIN_AUTH_FAILED, LoginErrorEnum.fromCode(2));
        assertEquals(LoginErrorEnum.LOGIN_FAILURE_NEED_RETRY, LoginErrorEnum.fromCode(3));
        assertEquals(LoginErrorEnum.LOGIN_FAILURE_NO_RETRY, LoginErrorEnum.fromCode(4));
        assertEquals(LoginErrorEnum.ERR_ALREADY_LOGGED_IN, LoginErrorEnum.fromCode(5));
        assertEquals(LoginErrorEnum.ERR_LOGIN_IN_PROGRESS, LoginErrorEnum.fromCode(6));
    }

    @Test
    public void testFromCodeNotFound() {
        assertNull(LoginErrorEnum.fromCode(-1));
        assertNull(LoginErrorEnum.fromCode(7));
        assertNull(LoginErrorEnum.fromCode(100));
    }

    @Test
    public void testFromCodeRoundTrip() {
        for (LoginErrorEnum error : LoginErrorEnum.values()) {
            assertNotNull(LoginErrorEnum.fromCode(error.getCode()));
            assertEquals(error, LoginErrorEnum.fromCode(error.getCode()));
        }
    }

    @Test
    public void testCodesAreUnique() {
        LoginErrorEnum[] values = LoginErrorEnum.values();
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                assertNotNull("Code collision: " + values[i].getCode(),
                        values[i].getCode() != values[j].getCode());
            }
        }
    }
}
