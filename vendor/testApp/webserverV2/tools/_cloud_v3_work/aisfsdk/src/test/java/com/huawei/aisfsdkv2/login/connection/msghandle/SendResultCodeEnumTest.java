/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.login.connection.msghandle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * SendResultCodeEnum 单元测试
 */
public class SendResultCodeEnumTest {

    @Test
    public void testSuccessCode() {
        assertEquals(0, SendResultCodeEnum.SUCCESS.getCode());
    }

    @Test
    public void testNotConnectedCode() {
        assertEquals(-1, SendResultCodeEnum.NOT_CONNECTED.getCode());
    }

    @Test
    public void testFilteredCode() {
        assertEquals(-2, SendResultCodeEnum.FILTERED.getCode());
    }

    @Test
    public void testQueueFullCode() {
        assertEquals(-3, SendResultCodeEnum.QUEUE_FULL.getCode());
    }

    @Test
    public void testSendFailedCode() {
        assertEquals(-4, SendResultCodeEnum.SEND_FAILED.getCode());
    }

    @Test
    public void testDisconnectedCode() {
        assertEquals(-5, SendResultCodeEnum.DISCONNECTED.getCode());
    }

    @Test
    public void testSuccessDescription() {
        assertEquals("success", SendResultCodeEnum.SUCCESS.getDescription());
    }

    @Test
    public void testNotConnectedDescription() {
        assertEquals("not connected", SendResultCodeEnum.NOT_CONNECTED.getDescription());
    }

    @Test
    public void testIsSuccess_onSuccess() {
        assertTrue(SendResultCodeEnum.SUCCESS.isSuccess());
    }

    @Test
    public void testIsSuccess_onFailure() {
        assertFalse(SendResultCodeEnum.NOT_CONNECTED.isSuccess());
        assertFalse(SendResultCodeEnum.FILTERED.isSuccess());
        assertFalse(SendResultCodeEnum.QUEUE_FULL.isSuccess());
        assertFalse(SendResultCodeEnum.SEND_FAILED.isSuccess());
        assertFalse(SendResultCodeEnum.DISCONNECTED.isSuccess());
    }

    @Test
    public void testEnumValuesCount() {
        assertEquals(6, SendResultCodeEnum.values().length);
    }
}
