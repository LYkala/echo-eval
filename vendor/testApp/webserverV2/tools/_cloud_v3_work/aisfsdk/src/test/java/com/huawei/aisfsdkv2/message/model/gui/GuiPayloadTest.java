/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import com.huawei.aisfsdkv2.message.model.GuiCmdType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * GuiPayload 单元测试
 *
 * <p>测试 GUI 消息载荷的字段和默认值</p>
 *
 * @author l00504561
 * @since 2026-03-31
 */
public class GuiPayloadTest {

    // ==================== 基本构造测试 ====================

    /**
     * 测试默认构造
     */
    @Test
    public void testDefaultConstructor() {
        GuiPayload payload = new GuiPayload();
        assertNotNull("构造后对象不应为null", payload);
    }

    // ==================== Function 默认值测试 ====================

    /**
     * 测试 Function 默认值
     */
    @Test
    public void testFunction_Default() {
        GuiPayload payload = new GuiPayload();
        assertEquals("Function 默认值应为 GUI", "GUI", payload.getFunction());
    }

    /**
     * 测试 Function setter/getter
     */
    @Test
    public void testFunction() {
        GuiPayload payload = new GuiPayload();
        payload.setFunction("CUSTOM");
        assertEquals("Function 应正确设置", "CUSTOM", payload.getFunction());
    }

    // ==================== CmdType 默认值测试 ====================

    /**
     * 测试 CmdType 默认值
     */
    @Test
    public void testCmdType_Default() {
        GuiPayload payload = new GuiPayload();
        assertEquals("CmdType 默认值应为 UNKNOWN", 
            GuiCmdType.UNKNOWN, payload.getCmdType());
    }

    /**
     * 测试 CmdType setter/getter
     */
    @Test
    public void testCmdType() {
        GuiPayload payload = new GuiPayload();
        payload.setCmdType(GuiCmdType.CHECK_APP_INSTALLED);
        assertEquals("CmdType 应正确设置", 
            GuiCmdType.CHECK_APP_INSTALLED, payload.getCmdType());
    }

    /**
     * 测试所有 CmdType 值
     */
    @Test
    public void testAllCmdTypes() {
        GuiPayload payload = new GuiPayload();
        
        for (GuiCmdType cmdType : GuiCmdType.values()) {
            payload.setCmdType(cmdType);
            assertEquals("CmdType " + cmdType + " 应正确设置", cmdType, payload.getCmdType());
        }
    }

    // ==================== Params 测试 ====================

    /**
     * 测试 Params setter/getter
     */
    @Test
    public void testParams() {
        GuiPayload payload = new GuiPayload();
        CheckAppInstalledReqParam params = new CheckAppInstalledReqParam();
        params.setPackageName("com.test.app");
        
        payload.setParams(params);
        assertEquals("Params 应正确设置", params, payload.getParams());
        
        CheckAppInstalledReqParam retrieved = (CheckAppInstalledReqParam) payload.getParams();
        assertEquals("com.test.app", retrieved.getPackageName());
    }

    /**
     * 测试 Params 设置为 null
     */
    @Test
    public void testParams_Null() {
        GuiPayload payload = new GuiPayload();
        payload.setParams(null);
        assertNull("Params 可为 null", payload.getParams());
    }

    // ==================== 综合测试 ====================

    /**
     * 测试所有字段独立设置
     */
    @Test
    public void testAllFieldsIndependent() {
        GuiPayload payload = new GuiPayload();
        
        payload.setFunction("TEST");
        payload.setCmdType(GuiCmdType.SCREEN_SHOT);
        CheckAppInstalledReqParam params = new CheckAppInstalledReqParam();
        payload.setParams(params);
        
        assertEquals("TEST", payload.getFunction());
        assertEquals(GuiCmdType.SCREEN_SHOT, payload.getCmdType());
        assertEquals(params, payload.getParams());
    }

    /**
     * 测试多次修改字段
     */
    @Test
    public void testFieldOverwrite() {
        GuiPayload payload = new GuiPayload();
        
        payload.setCmdType(GuiCmdType.CHECK_APP_INSTALLED);
        assertEquals(GuiCmdType.CHECK_APP_INSTALLED, payload.getCmdType());
        
        payload.setCmdType(GuiCmdType.SCREEN_SHOT);
        assertEquals(GuiCmdType.SCREEN_SHOT, payload.getCmdType());
        
        payload.setCmdType(GuiCmdType.UNKNOWN);
        assertEquals(GuiCmdType.UNKNOWN, payload.getCmdType());
    }

    /**
     * 测试完整的消息载荷构建
     */
    @Test
    public void testCompletePayloadBuild() {
        GuiPayload payload = new GuiPayload();
        
        // 构建应用安装检查消息
        payload.setFunction("GUI");
        payload.setCmdType(GuiCmdType.CHECK_APP_INSTALLED);
        CheckAppInstalledReqParam params = new CheckAppInstalledReqParam();
        params.setPackageName("com.example.app");
        payload.setParams(params);
        
        assertEquals("GUI", payload.getFunction());
        assertEquals(GuiCmdType.CHECK_APP_INSTALLED, payload.getCmdType());
        assertNotNull(payload.getParams());
    }

    /**
     * 测试 toString 方法
     */
    @Test
    public void testToString() {
        GuiPayload payload = new GuiPayload();
        payload.setCmdType(GuiCmdType.CHECK_APP_INSTALLED);
        
        String str = payload.toString();
        assertNotNull("toString 不应返回 null", str);
        assertTrue("toString 应返回非空字符串", str.length() > 0);
    }

    /**
     * 测试空对象状态（默认值）
     */
    @Test
    public void testEmptyObject() {
        GuiPayload payload = new GuiPayload();
        
        assertEquals("默认 Function 应为 GUI", "GUI", payload.getFunction());
        assertEquals("默认 CmdType 应为 UNKNOWN", GuiCmdType.UNKNOWN, payload.getCmdType());
        assertNull("默认 Params 应为 null", payload.getParams());
    }

    /**
     * 测试 Function 切换
     */
    @Test
    public void testFunctionSwitch() {
        GuiPayload payload = new GuiPayload();
        
        payload.setFunction("GUI");
        assertEquals("GUI", payload.getFunction());
        
        payload.setFunction("SYSTEM");
        assertEquals("SYSTEM", payload.getFunction());
        
        payload.setFunction("");
        assertEquals("", payload.getFunction());
    }
}
