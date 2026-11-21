/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logtrack;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * LogTrackType 测试
 */
public class LogTrackTypeTest {

    @Test
    public void testEnumValues() {
        LogTrackType[] values = LogTrackType.values();
        assertEquals(2, values.length);
    }

    @Test
    public void testSendType() {
        LogTrackType type = LogTrackType.SEND;
        assertNotNull(type);
        assertEquals("SDK", type.getFrom());
        assertEquals("SERVER", type.getTo());
    }

    @Test
    public void testReceiveType() {
        LogTrackType type = LogTrackType.RECEIVE;
        assertNotNull(type);
        assertEquals("SERVER", type.getFrom());
        assertEquals("SDK", type.getTo());
    }

    @Test
    public void testValueOf() {
        assertEquals(LogTrackType.SEND, LogTrackType.valueOf("SEND"));
        assertEquals(LogTrackType.RECEIVE, LogTrackType.valueOf("RECEIVE"));
    }

    @Test
    public void testOrdinalOrder() {
        assertEquals(0, LogTrackType.SEND.ordinal());
        assertEquals(1, LogTrackType.RECEIVE.ordinal());
    }

    @Test
    public void testSendName() {
        assertEquals("SEND", LogTrackType.SEND.name());
    }

    @Test
    public void testReceiveName() {
        assertEquals("RECEIVE", LogTrackType.RECEIVE.name());
    }
}
