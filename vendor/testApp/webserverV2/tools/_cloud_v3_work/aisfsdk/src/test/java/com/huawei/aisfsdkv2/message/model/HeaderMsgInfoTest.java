/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * HeaderMsgInfo 单元测试
 *
 * <p>测试消息头信息模型的所有字段</p>
 *
 * @author l00504561
 * @since 2026-03-31
 */
public class HeaderMsgInfoTest {

    // ==================== 基本构造测试 ====================

    /**
     * 测试默认构造
     */
    @Test
    public void testDefaultConstructor() {
        HeaderMsgInfo header = new HeaderMsgInfo();
        assertNotNull("构造后对象不应为null", header);
    }

    // ==================== MessageType 测试 ====================

    /**
     * 测试 MessageType setter/getter
     */
    @Test
    public void testMessageType() {
        HeaderMsgInfo header = new HeaderMsgInfo();
        header.setMessageType("DataExchange");
        assertEquals("MessageType 应正确设置", "DataExchange", header.getMessageType());
    }

    /**
     * 测试 MessageType 设置为 null
     */
    @Test
    public void testMessageType_Null() {
        HeaderMsgInfo header = new HeaderMsgInfo();
        header.setMessageType(null);
        assertNull("MessageType 可为 null", header.getMessageType());
    }

    // ==================== Sender 测试 ====================

    /**
     * 测试 Sender setter/getter
     */
    @Test
    public void testSender() {
        HeaderMsgInfo header = new HeaderMsgInfo();
        header.setSender("AISFDEMO");
        assertEquals("Sender 应正确设置", "AISFDEMO", header.getSender());
    }

    // ==================== Receiver 测试 ====================

    /**
     * 测试 Receiver setter/getter
     */
    @Test
    public void testReceiver() {
        HeaderMsgInfo header = new HeaderMsgInfo();
        header.setReceiver("SystemAgent");
        assertEquals("Receiver 应正确设置", "SystemAgent", header.getReceiver());
    }

    // ==================== ClientSessionId 测试 ====================

    /**
     * 测试 ClientSessionId setter/getter
     */
    @Test
    public void testClientSessionId() {
        HeaderMsgInfo header = new HeaderMsgInfo();
        header.setClientSessionId("session-123-abc");
        assertEquals("ClientSessionId 应正确设置", "session-123-abc", header.getClientSessionId());
    }

    // ==================== UserName 测试 ====================

    /**
     * 测试 UserName setter/getter
     */
    @Test
    public void testUserName() {
        HeaderMsgInfo header = new HeaderMsgInfo();
        header.setUserName("testuser");
        assertEquals("UserName 应正确设置", "testuser", header.getUserName());
    }

    // ==================== DeviceId 测试 ====================

    /**
     * 测试 DeviceId setter/getter
     */
    @Test
    public void testDeviceId() {
        HeaderMsgInfo header = new HeaderMsgInfo();
        header.setDeviceId("device-uuid-123");
        assertEquals("DeviceId 应正确设置", "device-uuid-123", header.getDeviceId());
    }

    // ==================== DeviceCategory 测试 ====================

    /**
     * 测试 DeviceCategory setter/getter
     */
    @Test
    public void testDeviceCategory() {
        HeaderMsgInfo header = new HeaderMsgInfo();
        header.setDeviceCategory("1");
        assertEquals("DeviceCategory 应正确设置", "1", header.getDeviceCategory());
    }

    // ==================== UUID 测试 ====================

    /**
     * 测试 UUID setter/getter
     */
    @Test
    public void testUuid() {
        HeaderMsgInfo header = new HeaderMsgInfo();
        header.setUuid("550e8400-e29b-41d4-a716-446655440000");
        assertEquals("UUID 应正确设置", "550e8400-e29b-41d4-a716-446655440000", header.getUuid());
    }

    // ==================== TaskID 测试 ====================

    /**
     * 测试 TaskID setter/getter
     */
    @Test
    public void testTaskId() {
        HeaderMsgInfo header = new HeaderMsgInfo();
        header.setTaskId("task-123-456");
        assertEquals("TaskID 应正确设置", "task-123-456", header.getTaskId());
    }

    // ==================== EventTimeStamp 测试 ====================

    /**
     * 测试 EventTimeStamp setter/getter
     */
    @Test
    public void testEventTimeStamp() {
        HeaderMsgInfo header = new HeaderMsgInfo();
        header.setEventTimeStamp("2024-03-31T12:30:45Z");
        assertEquals("EventTimeStamp 应正确设置", "2024-03-31T12:30:45Z", header.getEventTimeStamp());
    }

    // ==================== ClientExField 测试 ====================

    /**
     * 测试 ClientExField setter/getter
     */
    @Test
    public void testClientExField() {
        HeaderMsgInfo header = new HeaderMsgInfo();
        header.setClientExField("extra-data");
        assertEquals("ClientExField 应正确设置", "extra-data", header.getClientExField());
    }

    // ==================== DeviceCategoryStr 测试 ====================

    /**
     * 测试 DeviceCategoryStr setter/getter
     */
    @Test
    public void testDeviceCategoryStr() {
        HeaderMsgInfo header = new HeaderMsgInfo();
        header.setDeviceCategoryStr("SmartPhone");
        assertEquals("DeviceCategoryStr 应正确设置", "SmartPhone", header.getDeviceCategoryStr());
    }

    // ==================== DeviceUUID 测试 ====================

    /**
     * 测试 DeviceUUID setter/getter
     */
    @Test
    public void testDeviceUUID() {
        HeaderMsgInfo header = new HeaderMsgInfo();
        header.setDeviceUUID("device-uuid-string");
        assertEquals("DeviceUUID 应正确设置", "device-uuid-string", header.getDeviceUUID());
    }

    // ==================== IdentifyId 测试 ====================

    /**
     * 测试 IdentifyId setter/getter
     */
    @Test
    public void testIdentifyId() {
        HeaderMsgInfo header = new HeaderMsgInfo();
        header.setIdentifyId("identify-123");
        assertEquals("IdentifyId 应正确设置", "identify-123", header.getIdentifyId());
    }

    // ==================== 综合测试 ====================

    /**
     * 测试所有字段独立设置
     */
    @Test
    public void testAllFieldsIndependent() {
        HeaderMsgInfo header = new HeaderMsgInfo();
        
        header.setMessageType("DataExchange");
        header.setSender("AISFDEMO");
        header.setReceiver("SystemAgent");
        header.setClientSessionId("session-001");
        header.setUserName("testuser");
        header.setDeviceId("device-001");
        header.setDeviceCategory("1");
        header.setUuid("uuid-001");
        header.setTaskId("task-001");
        header.setEventTimeStamp("2024-03-31T12:00:00Z");
        header.setClientExField("extra");
        header.setDeviceCategoryStr("Phone");
        header.setDeviceUUID("dev-uuid-001");
        header.setIdentifyId("ident-001");
        
        // 验证所有字段
        assertEquals("DataExchange", header.getMessageType());
        assertEquals("AISFDEMO", header.getSender());
        assertEquals("SystemAgent", header.getReceiver());
        assertEquals("session-001", header.getClientSessionId());
        assertEquals("testuser", header.getUserName());
        assertEquals("device-001", header.getDeviceId());
        assertEquals("1", header.getDeviceCategory());
        assertEquals("uuid-001", header.getUuid());
        assertEquals("task-001", header.getTaskId());
        assertEquals("2024-03-31T12:00:00Z", header.getEventTimeStamp());
        assertEquals("extra", header.getClientExField());
        assertEquals("Phone", header.getDeviceCategoryStr());
        assertEquals("dev-uuid-001", header.getDeviceUUID());
        assertEquals("ident-001", header.getIdentifyId());
    }

    /**
     * 测试多次设置同一字段
     */
    @Test
    public void testFieldOverwrite() {
        HeaderMsgInfo header = new HeaderMsgInfo();
        
        header.setClientSessionId("id-1");
        assertEquals("id-1", header.getClientSessionId());
        
        header.setClientSessionId("id-2");
        assertEquals("id-2", header.getClientSessionId());
        
        header.setClientSessionId(null);
        assertNull(header.getClientSessionId());
    }

    /**
     * 测试 toString 方法
     */
    @Test
    public void testToString() {
        HeaderMsgInfo header = new HeaderMsgInfo();
        header.setClientSessionId("test-id");
        
        String str = header.toString();
        assertNotNull("toString 不应返回 null", str);
        assertTrue("toString 应返回非空字符串", str.length() > 0);
    }
}
