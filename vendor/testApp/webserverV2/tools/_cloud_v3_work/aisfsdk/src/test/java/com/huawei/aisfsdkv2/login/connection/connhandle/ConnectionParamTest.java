/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.login.connection.connhandle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * ConnectionParam Builder 测试
 */
public class ConnectionParamTest {

    @Test
    public void testBuildWithAllFields() {
        ConnectionParam param = ConnectionParam.builder()
                .url("wss://example.com/ws")
                .identifyId("device-001")
                .token("token-abc123")
                .userName("user1")
                .build();
        assertEquals("wss://example.com/ws", param.getUrl());
        assertEquals("device-001", param.getIdentifyId());
        assertEquals("token-abc123", param.getToken());
        assertEquals("user1", param.getUserName());
        assertNull(param.getCallback());
    }

    @Test
    public void testBuildWithCallback() {
        ConnectionCallback cb = new ConnectionCallback() {
            @Override public void onConnected() {}
            @Override public void onDisconnected(int code, String reason) {}
            @Override public void onAuthFailed() {}
        };
        ConnectionParam param = ConnectionParam.builder()
                .url("wss://host/ws")
                .identifyId("dev-1")
                .token("tok-1")
                .userName("usr-1")
                .callback(cb)
                .build();
        assertEquals(cb, param.getCallback());
    }

    @Test(expected = IllegalStateException.class)
    public void testBuildWithoutUrl() {
        ConnectionParam.builder()
                .identifyId("dev")
                .token("tok")
                .userName("usr")
                .build();
    }

    @Test(expected = IllegalStateException.class)
    public void testBuildWithEmptyUrl() {
        ConnectionParam.builder()
                .url("")
                .identifyId("dev")
                .token("tok")
                .userName("usr")
                .build();
    }

    @Test(expected = IllegalStateException.class)
    public void testBuildWithoutIdentifyId() {
        ConnectionParam.builder()
                .url("wss://host/ws")
                .token("tok")
                .userName("usr")
                .build();
    }

    @Test(expected = IllegalStateException.class)
    public void testBuildWithoutToken() {
        ConnectionParam.builder()
                .url("wss://host/ws")
                .identifyId("dev")
                .userName("usr")
                .build();
    }

    @Test(expected = IllegalStateException.class)
    public void testBuildWithoutUserName() {
        ConnectionParam.builder()
                .url("wss://host/ws")
                .identifyId("dev")
                .token("tok")
                .build();
    }

    @Test
    public void testToStringMasksToken() {
        ConnectionParam param = ConnectionParam.builder()
                .url("wss://host/ws")
                .identifyId("dev-1")
                .token("secret-token")
                .userName("user1")
                .build();
        String str = param.toString();
        assertNotNull(str);
        assertTrue(str.contains("***"));
        assertTrue(str.contains("dev-1"));
        assertTrue(str.contains("user1"));
    }

    @Test
    public void testBuilderReturnsNewInstance() {
        assertNotNull(ConnectionParam.builder());
    }
}
