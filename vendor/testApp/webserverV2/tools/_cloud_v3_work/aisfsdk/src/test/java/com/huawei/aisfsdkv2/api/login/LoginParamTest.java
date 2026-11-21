/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.login;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * LoginParam Builder 模式测试
 */
public class LoginParamTest {

    @Test
    public void testBuildWithUsernameAndPassword() {
        LoginParam param = new LoginParam.Builder()
                .username("user123")
                .password("pass123")
                .build();
        assertEquals("user123", param.getUsername());
        assertEquals("pass123", param.getPassword());
    }

    @Test
    public void testDefaultValues() {
        LoginParam param = new LoginParam.Builder()
                .username("user")
                .password("pass")
                .build();
        assertEquals(30000L, param.getTimeoutMs());
        assertTrue(param.isAutoReconnect());
        assertEquals(3, param.getMaxRetryCount());
        assertNull(param.getCallback());
        assertNull(param.getTicketProvider());
    }

    @Test
    public void testCustomTimeoutMs() {
        LoginParam param = new LoginParam.Builder()
                .username("user")
                .password("pass")
                .timeoutMs(60000L)
                .build();
        assertEquals(60000L, param.getTimeoutMs());
    }

    @Test
    public void testAutoReconnectFalse() {
        LoginParam param = new LoginParam.Builder()
                .username("user")
                .password("pass")
                .autoReconnect(false)
                .build();
        assertFalse(param.isAutoReconnect());
    }

    @Test
    public void testCustomMaxRetryCount() {
        LoginParam param = new LoginParam.Builder()
                .username("user")
                .password("pass")
                .maxRetryCount(5)
                .build();
        assertEquals(5, param.getMaxRetryCount());
    }

    @Test
    public void testWithCallback() {
        LoginCallback callback = new LoginCallback() {
            @Override
            public void onSuccess() {}
            @Override
            public void onFailure(LoginErrorEnum error, String message) {}
            @Override
            public void onConnectStatusChanged(LoginStatus oldStatus, LoginStatus newStatus) {}
        };
        LoginParam param = new LoginParam.Builder()
                .username("user")
                .password("pass")
                .callback(callback)
                .build();
        assertEquals(callback, param.getCallback());
    }

    @Test
    public void testWithTicketProvider() {
        TicketProvider provider = () -> "ticket123";
        LoginParam param = new LoginParam.Builder()
                .username("user")
                .ticketProvider(provider)
                .build();
        assertNotNull(param.getTicketProvider());
        assertNull(param.getPassword());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildWithNullUsername() {
        new LoginParam.Builder()
                .password("pass")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildWithEmptyUsername() {
        new LoginParam.Builder()
                .username("")
                .password("pass")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildWithNullPassword() {
        new LoginParam.Builder()
                .username("user")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildWithEmptyPassword() {
        new LoginParam.Builder()
                .username("user")
                .password("")
                .build();
    }

    @Test
    public void testTicketModeRequiresUsername() {
        // Ticket 模式仍需要 username
        TicketProvider provider = () -> "ticket";
        try {
            new LoginParam.Builder()
                    .ticketProvider(provider)
                    .build();
            throw new AssertionError("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testTicketModeNoPasswordRequired() {
        TicketProvider provider = () -> "ticket";
        LoginParam param = new LoginParam.Builder()
                .username("user")
                .ticketProvider(provider)
                .build();
        assertNotNull(param);
        assertEquals("user", param.getUsername());
        assertNull(param.getPassword());
    }

    @Test
    public void testBuilderChaining() {
        LoginParam param = new LoginParam.Builder()
                .username("testUser")
                .password("testPass")
                .timeoutMs(45000L)
                .autoReconnect(false)
                .maxRetryCount(2)
                .build();
        assertEquals("testUser", param.getUsername());
        assertEquals("testPass", param.getPassword());
        assertEquals(45000L, param.getTimeoutMs());
        assertFalse(param.isAutoReconnect());
        assertEquals(2, param.getMaxRetryCount());
    }
}
