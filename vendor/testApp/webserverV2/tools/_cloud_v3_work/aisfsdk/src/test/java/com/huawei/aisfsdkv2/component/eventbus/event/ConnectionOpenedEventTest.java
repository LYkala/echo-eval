/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.eventbus.event;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * ConnectionOpenedEvent 测试
 */
public class ConnectionOpenedEventTest {

    @Test
    public void testEventTypeConstant() {
        assertEquals("CONNECTION_OPENED", ConnectionOpenedEvent.EVENT_TYPE);
    }

    @Test
    public void testGetEventType() {
        ConnectionOpenedEvent event = new ConnectionOpenedEvent();
        assertEquals("CONNECTION_OPENED", event.getEventType());
    }

    @Test
    public void testGetEventTypeMatchesConstant() {
        ConnectionOpenedEvent event = new ConnectionOpenedEvent();
        assertEquals(ConnectionOpenedEvent.EVENT_TYPE, event.getEventType());
    }

    @Test
    public void testInstanceNotNull() {
        ConnectionOpenedEvent event = new ConnectionOpenedEvent();
        assertNotNull(event);
    }

    @Test
    public void testMultipleInstancesSameEventType() {
        ConnectionOpenedEvent event1 = new ConnectionOpenedEvent();
        ConnectionOpenedEvent event2 = new ConnectionOpenedEvent();
        assertEquals(event1.getEventType(), event2.getEventType());
    }
}
