/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler.impl.gui;

import com.huawei.aisfsdkv2.message.model.GuiMsgInfo;
import com.huawei.aisfsdkv2.message.model.HeaderMsgInfo;
import com.huawei.aisfsdkv2.message.model.gui.AppInstallStatusRespParam;
import com.huawei.aisfsdkv2.message.model.gui.CheckAppInstalledReqParam;
import com.huawei.aisfsdkv2.message.model.gui.GuiPayload;

import org.junit.Test;
import org.junit.Before;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * CheckAppInstalledMsgSubHandler 单元测试
 *
 * <p>测试应用安装状态检查处理器</p>
 *
 * @author l00504561
 * @since 2026-03-31
 */
public class CheckAppInstalledMsgSubHandlerTest {

    private CheckAppInstalledMsgSubHandler handler;

    @Before
    public void setUp() {
        handler = new CheckAppInstalledMsgSubHandler();
    }

    // ==================== 命令类型测试 ====================

    /**
     * 测试获取命令类型
     */
    @Test
    public void testGetCmdType() {
        assertEquals("命令类型应为 CHECK_APP_INSTALLED",
            com.huawei.aisfsdkv2.message.model.GuiCmdType.CHECK_APP_INSTALLED,
            handler.getCmdType());
    }

    // ==================== handle 方法边界测试 ====================

    /**
     * 测试处理器可实例化
     */
    @Test
    public void testHandlerInstantiation() {
        // 验证处理器可以实例化
        CheckAppInstalledMsgSubHandler newHandler = new CheckAppInstalledMsgSubHandler();
        assertNotNull("处理器应成功实例化", newHandler);
        assertEquals("命令类型应正确",
            com.huawei.aisfsdkv2.message.model.GuiCmdType.CHECK_APP_INSTALLED,
            newHandler.getCmdType());
    }

    /**
     * 测试处理 null 消息参数构造
     */
    @Test
    public void testNullParamsHandling() {
        // 仅验证可以构造带有 null 参数的消息
        // 不实际调用 handle，因为需要 Android 环境
        GuiMsgInfo msgInfo = createTestMsgInfo(null);
        assertNotNull("消息应成功构造", msgInfo);
        assertNull("参数应为 null", msgInfo.getPayload().getParams());
    }

    /**
     * 测试处理空包名消息构造
     */
    @Test
    public void testEmptyPackageNameHandling() {
        CheckAppInstalledReqParam params = new CheckAppInstalledReqParam();
        params.setPackageName("");
        
        GuiMsgInfo msgInfo = createTestMsgInfo(params);
        assertNotNull("消息应成功构造", msgInfo);
        CheckAppInstalledReqParam retrieved = (CheckAppInstalledReqParam) msgInfo.getPayload().getParams();
        assertEquals("包名应为空字符串", "", retrieved.getPackageName());
    }

    /**
     * 测试处理 null 包名消息构造
     */
    @Test
    public void testNullPackageNameHandling() {
        CheckAppInstalledReqParam params = new CheckAppInstalledReqParam();
        params.setPackageName(null);
        
        GuiMsgInfo msgInfo = createTestMsgInfo(params);
        assertNotNull("消息应成功构造", msgInfo);
        CheckAppInstalledReqParam retrieved = (CheckAppInstalledReqParam) msgInfo.getPayload().getParams();
        assertNull("包名应为 null", retrieved.getPackageName());
    }

    /**
     * 测试构造器的初始化
     */
    @Test
    public void testConstructor() {
        CheckAppInstalledMsgSubHandler newHandler = new CheckAppInstalledMsgSubHandler();
        assertNotNull("构造器应成功初始化", newHandler);
        assertEquals("命令类型应正确设置",
            com.huawei.aisfsdkv2.message.model.GuiCmdType.CHECK_APP_INSTALLED,
            newHandler.getCmdType());
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试用的 GuiMsgInfo
     */
    private GuiMsgInfo createTestMsgInfo(CheckAppInstalledReqParam params) {
        GuiMsgInfo msgInfo = new GuiMsgInfo();
        
        // 设置 Header
        HeaderMsgInfo header = new HeaderMsgInfo();
        header.setClientSessionId("test-session-id");
        header.setUserName("testuser");
        header.setDeviceId("test-device-id");
        msgInfo.setHeader(header);
        
        // 设置 Payload
        GuiPayload payload = new GuiPayload();
        payload.setCmdType(com.huawei.aisfsdkv2.message.model.GuiCmdType.CHECK_APP_INSTALLED);
        payload.setParams(params);
        msgInfo.setPayload(payload);
        
        return msgInfo;
    }
}
