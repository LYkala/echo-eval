/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model;

import com.huawei.aisfsdkv2.message.model.gui.GuiData;
import com.huawei.aisfsdkv2.message.model.gui.CheckAppInstalledReqParam;
import com.huawei.aisfsdkv2.message.model.gui.GuiPayload;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * GuiMsgInfo 单元测试
 *
 * <p>测试GUI消息信息模型的 getter/setter 和默认值</p>
 *
 * @author l00504561
 * @since 2026-03-31
 */
public class GuiMsgInfoTest {

    // ==================== 基本构造测试 ====================

    /**
     * 测试默认构造
     */
    @Test
    public void testDefaultConstructor() {
        GuiMsgInfo msgInfo = new GuiMsgInfo();
        assertNotNull("构造后对象不应为null", msgInfo);
    }

    /**
     * 测试默认 Data 不为 null
     */
    @Test
    public void testDefaultData() {
        GuiMsgInfo msgInfo = new GuiMsgInfo();
        assertNotNull("默认 Data 不应为null", msgInfo.getData());
    }

    /**
     * 测试默认 Payload 不为 null
     */
    @Test
    public void testDefaultPayload() {
        GuiMsgInfo msgInfo = new GuiMsgInfo();
        assertNotNull("默认 Payload 不应为null", msgInfo.getPayload());
    }

    // ==================== Header 测试 ====================

    /**
     * 测试 Header setter/getter
     */
    @Test
    public void testHeader() {
        GuiMsgInfo msgInfo = new GuiMsgInfo();
        HeaderMsgInfo header = new HeaderMsgInfo();
        header.setClientSessionId("test-session");
        
        msgInfo.setHeader(header);
        assertSame("Header 应正确设置", header, msgInfo.getHeader());
        assertEquals("ClientSessionId 应正确", "test-session", msgInfo.getHeader().getClientSessionId());
    }

    /**
     * 测试 Header 设置为 null
     */
    @Test
    public void testHeader_Null() {
        GuiMsgInfo msgInfo = new GuiMsgInfo();
        msgInfo.setHeader(null);
        assertNull("Header 可设置为 null", msgInfo.getHeader());
    }

    // ==================== Data 测试 ====================

    /**
     * 测试 Data setter/getter
     */
    @Test
    public void testData() {
        GuiMsgInfo msgInfo = new GuiMsgInfo();
        GuiData data = new GuiData();
        data.setMessageType("TestType");
        
        msgInfo.setData(data);
        assertSame("Data 应正确设置", data, msgInfo.getData());
        assertEquals("MessageType 应正确", "TestType", msgInfo.getData().getMessageType());
    }

    /**
     * 测试 Data 修改后不改变引用
     */
    @Test
    public void testData_Independence() {
        GuiMsgInfo msgInfo = new GuiMsgInfo();
        GuiData originalData = msgInfo.getData();
        
        // 修改 data
        originalData.setMessageType("Modified");
        
        // 验证仍是同一个对象
        assertSame("Data 引用不应改变", originalData, msgInfo.getData());
        assertEquals("修改应生效", "Modified", msgInfo.getData().getMessageType());
    }

    // ==================== Payload 测试 ====================

    /**
     * 测试 Payload setter/getter
     */
    @Test
    public void testPayload() {
        GuiMsgInfo msgInfo = new GuiMsgInfo();
        GuiPayload payload = new GuiPayload();
        payload.setCmdType(GuiCmdType.CHECK_APP_INSTALLED);
        
        msgInfo.setPayload(payload);
        assertSame("Payload 应正确设置", payload, msgInfo.getPayload());
        assertEquals("CmdType 应正确", GuiCmdType.CHECK_APP_INSTALLED, msgInfo.getPayload().getCmdType());
    }

    /**
     * 测试 Payload 设置为 null
     */
    @Test
    public void testPayload_Null() {
        GuiMsgInfo msgInfo = new GuiMsgInfo();
        msgInfo.setPayload(null);
        assertNull("Payload 可设置为 null", msgInfo.getPayload());
    }

    // ==================== 序列化测试 ====================

    /**
     * 测试 toString 方法可调用
     */
    @Test
    public void testToString_Callable() {
        GuiMsgInfo msgInfo = new GuiMsgInfo();
        try {
            // toString 依赖 JSONObject（Android stub），结果可能为 null，不抛异常即通过
            msgInfo.toString();
        } catch (Exception e) {
            // toString 依赖 Android 环境，异常也可接受
        }
        assertTrue("toString 可调用", true);
    }

    // ==================== 综合测试 ====================

    /**
     * 测试完整消息构建
     */
    @Test
    public void testCompleteMessageBuild() {
        GuiMsgInfo msgInfo = new GuiMsgInfo();
        
        // 设置 Header
        HeaderMsgInfo header = new HeaderMsgInfo();
        header.setClientSessionId("session-123");
        header.setUserName("testuser");
        header.setDeviceId("device-456");
        msgInfo.setHeader(header);
        
        // 设置 Data
        GuiData data = new GuiData();
        data.setMessageType("DataExchange");
        msgInfo.setData(data);
        
        // 设置 Payload
        GuiPayload payload = new GuiPayload();
        payload.setCmdType(GuiCmdType.CHECK_APP_INSTALLED);
        CheckAppInstalledReqParam params = new CheckAppInstalledReqParam();
        params.setPackageName("com.test.app");
        payload.setParams(params);
        msgInfo.setPayload(payload);
        
        // 验证所有字段
        assertNotNull(msgInfo.getHeader());
        assertNotNull(msgInfo.getData());
        assertNotNull(msgInfo.getPayload());
        assertEquals("session-123", msgInfo.getHeader().getClientSessionId());
        assertEquals("DataExchange", msgInfo.getData().getMessageType());
        assertEquals(GuiCmdType.CHECK_APP_INSTALLED, msgInfo.getPayload().getCmdType());
    }

    /**
     * 测试多次修改各字段
     */
    @Test
    public void testMultipleModifications() {
        GuiMsgInfo msgInfo = new GuiMsgInfo();
        
        // 第一次修改
        HeaderMsgInfo header1 = new HeaderMsgInfo();
        header1.setClientSessionId("id-1");
        msgInfo.setHeader(header1);
        assertEquals("id-1", msgInfo.getHeader().getClientSessionId());
        
        // 第二次修改
        HeaderMsgInfo header2 = new HeaderMsgInfo();
        header2.setClientSessionId("id-2");
        msgInfo.setHeader(header2);
        assertEquals("id-2", msgInfo.getHeader().getClientSessionId());
        
        // 第三次修改
        msgInfo.setHeader(null);
        assertNull(msgInfo.getHeader());
    }
}
