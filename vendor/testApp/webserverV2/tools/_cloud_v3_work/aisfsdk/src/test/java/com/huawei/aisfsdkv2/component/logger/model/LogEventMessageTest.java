/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logger.model;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * LogEventMessage单元测试
 */
public class LogEventMessageTest {
    @Test
    public void testConstructorWithEventNameAndDetail() {
        LogEventMessage message = new LogEventMessage("TestEvent", "Test detail");
        assertEquals("SDK", message.getServiceName());
        assertEquals("TestEvent", message.getEventName());
        assertEquals("Test detail", message.getDetail());
        assertEquals("success", message.getStatus());
    }

    @Test
    public void testSetServiceName() {
        LogEventMessage message = new LogEventMessage("Event", "Detail");
        message.setServiceName("CustomService");
        assertEquals("CustomService", message.getServiceName());
    }

    @Test
    public void testSetEventName() {
        LogEventMessage message = new LogEventMessage("Event", "Detail");
        message.setEventName("NewEvent");
        assertEquals("NewEvent", message.getEventName());
    }

    @Test
    public void testSetDetail() {
        LogEventMessage message = new LogEventMessage("Event", "Detail");
        message.setDetail("New detail");
        assertEquals("New detail", message.getDetail());
    }

    @Test
    public void testSetStatus() {
        LogEventMessage message = new LogEventMessage("Event", "Detail");
        message.setStatus("failed");
        assertEquals("failed", message.getStatus());
    }

    @Test
    public void testNullEventName() {
        LogEventMessage message = new LogEventMessage(null, "Detail");
        assertNull(message.getEventName());
    }

    @Test
    public void testNullDetail() {
        LogEventMessage message = new LogEventMessage("Event", null);
        assertNull(message.getDetail());
    }

    @Test
    public void testSetNullServiceName() {
        LogEventMessage message = new LogEventMessage("Event", "Detail");
        message.setServiceName(null);
        assertNull(message.getServiceName());
    }

    @Test
    public void testSetNullStatus() {
        LogEventMessage message = new LogEventMessage("Event", "Detail");
        message.setStatus(null);
        assertNull(message.getStatus());
    }

    @Test
    public void testEmptyStrings() {
        LogEventMessage message = new LogEventMessage("", "");
        assertEquals("", message.getEventName());
        assertEquals("", message.getDetail());
    }

    @Test
    public void testChineseCharacters() {
        LogEventMessage message = new LogEventMessage("登录事件", "用户登录成功");
        assertEquals("登录事件", message.getEventName());
        assertEquals("用户登录成功", message.getDetail());
    }

    @Test
    public void testSpecialCharacters() {
        LogEventMessage message = new LogEventMessage("event-name_123", "detail with spaces and symbols: !@#$%");
        assertEquals("event-name_123", message.getEventName());
        assertEquals("detail with spaces and symbols: !@#$%", message.getDetail());
    }

    @Test
    public void testLongDetail() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("a");
        }
        String longDetail = sb.toString();
        LogEventMessage message = new LogEventMessage("Event", longDetail);
        assertEquals(1000, message.getDetail().length());
    }

    @Test
    public void testAllSettersAndGetters() {
        LogEventMessage message = new LogEventMessage("Event", "Detail");
        message.setServiceName("ServiceA");
        message.setEventName("EventB");
        message.setDetail("DetailC");
        message.setStatus("StatusD");
        assertEquals("ServiceA", message.getServiceName());
        assertEquals("EventB", message.getEventName());
        assertEquals("DetailC", message.getDetail());
        assertEquals("StatusD", message.getStatus());
    }
}
