/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.base;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * ServerInfo 单元测试
 *
 * <p>测试服务器信息模型的所有字段和默认值</p>
 *
 * @author l00504561
 * @since 2026-03-31
 */
public class ServerInfoTest {

    // ==================== 基本构造和默认值测试 ====================

    /**
     * 测试默认构造
     */
    @Test
    public void testDefaultConstructor() {
        ServerInfo serverInfo = new ServerInfo();
        assertNotNull("构造后对象不应为null", serverInfo);
    }

    /**
     * 测试默认 serverEnv 为 CUSTOM_ENV
     */
    @Test
    public void testDefaultServerEnv() {
        ServerInfo serverInfo = new ServerInfo();
        assertEquals("默认 serverEnv 应为 CUSTOM_ENV", ServerEnvEnum.CUSTOM_ENV, serverInfo.getServerEnv());
    }

    /**
     * 测试默认 serverAuthMode 为 ACM
     */
    @Test
    public void testDefaultServerAuthMode() {
        ServerInfo serverInfo = new ServerInfo();
        assertEquals("默认 serverAuthMode 应为 ACM", ServerAuthModeEnum.ACM, serverInfo.getServerAuthMode());
    }

    // ==================== IP 测试 ====================

    /**
     * 测试自定义 IP setter/getter（CUSTOM_ENV 下）
     */
    @Test
    public void testCustomServerIp() {
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setCustomServerIp("192.168.1.1");
        assertEquals("IP 应正确设置", "192.168.1.1", serverInfo.getServerIp());
    }

    /**
     * 测试自定义 IP 设置为 null
     */
    @Test
    public void testCustomServerIp_Null() {
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setCustomServerIp(null);
        assertEquals("IP 可为 null", null, serverInfo.getServerIp());
    }

    /**
     * 测试空对象默认 IP 为 null
     */
    @Test
    public void testDefaultServerIp() {
        ServerInfo serverInfo = new ServerInfo();
        assertEquals("默认 IP 应为 null", null, serverInfo.getServerIp());
    }

    // ==================== WebSocketPort 测试 ====================

    /**
     * 测试 WebSocketPort setter/getter
     */
    @Test
    public void testWebsocketPort() {
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setWebsocketPort(8080);
        assertEquals("WebSocketPort 应正确设置", 8080, serverInfo.getWebsocketPort());
    }

    /**
     * 测试 WebSocketPort 设置为 0
     */
    @Test
    public void testWebsocketPort_Zero() {
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setWebsocketPort(0);
        assertEquals("WebSocketPort 可为 0", 0, serverInfo.getWebsocketPort());
    }

    /**
     * 测试默认 WebSocketPort 为 0
     */
    @Test
    public void testDefaultWebsocketPort() {
        ServerInfo serverInfo = new ServerInfo();
        assertEquals("默认 WebSocketPort 应为 0", 0, serverInfo.getWebsocketPort());
    }

    // ==================== RestfulPort 测试 ====================

    /**
     * 测试 RestfulPort setter/getter
     */
    @Test
    public void testRestfulPort() {
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setRestfulPort(8443);
        assertEquals("RestfulPort 应正确设置", 8443, serverInfo.getRestfulPort());
    }

    /**
     * 测试默认 RestfulPort 为 0
     */
    @Test
    public void testDefaultRestfulPort() {
        ServerInfo serverInfo = new ServerInfo();
        assertEquals("默认 RestfulPort 应为 0", 0, serverInfo.getRestfulPort());
    }

    // ==================== C2cPort 测试 ====================

    /**
     * 测试 C2cPort setter/getter
     */
    @Test
    public void testC2cPort() {
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setC2cPort(9090);
        assertEquals("C2cPort 应正确设置", 9090, serverInfo.getC2cPort());
    }

    /**
     * 测试默认 C2cPort 为 0
     */
    @Test
    public void testDefaultC2cPort() {
        ServerInfo serverInfo = new ServerInfo();
        assertEquals("默认 C2cPort 应为 0", 0, serverInfo.getC2cPort());
    }

    // ==================== ApigwPort 测试 ====================

    /**
     * 测试 ApigwPort setter/getter
     */
    @Test
    public void testApigwPort() {
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setApigwPort(9443);
        assertEquals("ApigwPort 应正确设置", 9443, serverInfo.getApigwPort());
    }

    // ==================== 综合测试 ====================

    /**
     * 测试所有字段独立设置
     */
    @Test
    public void testAllFieldsIndependent() {
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setCustomServerIp("10.0.0.1");
        serverInfo.setWebsocketPort(8080);
        serverInfo.setRestfulPort(8443);
        serverInfo.setC2cPort(9090);
        serverInfo.setApigwPort(9443);
        assertEquals("10.0.0.1", serverInfo.getServerIp());
        assertEquals(8080, serverInfo.getWebsocketPort());
        assertEquals(8443, serverInfo.getRestfulPort());
        assertEquals(9090, serverInfo.getC2cPort());
        assertEquals(9443, serverInfo.getApigwPort());
    }

    /**
     * 测试多次设置同一字段
     */
    @Test
    public void testFieldOverwrite() {
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setCustomServerIp("192.168.1.1");
        assertEquals("192.168.1.1", serverInfo.getServerIp());
        serverInfo.setCustomServerIp("10.0.0.1");
        assertEquals("10.0.0.1", serverInfo.getServerIp());
        serverInfo.setCustomServerIp(null);
        assertEquals(null, serverInfo.getServerIp());
    }

    /**
     * 测试端口范围
     */
    @Test
    public void testPortRanges() {
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setWebsocketPort(80);
        assertEquals(80, serverInfo.getWebsocketPort());
        serverInfo.setWebsocketPort(443);
        assertEquals(443, serverInfo.getWebsocketPort());
        serverInfo.setWebsocketPort(65535);
        assertEquals(65535, serverInfo.getWebsocketPort());
        serverInfo.setRestfulPort(10000);
        assertEquals(10000, serverInfo.getRestfulPort());
    }

    /**
     * 测试 toString 方法
     */
    @Test
    public void testToString() {
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setCustomServerIp("192.168.1.1");
        serverInfo.setWebsocketPort(8080);
        String str = serverInfo.toString();
        assertNotNull("toString 不应返回 null", str);
        assertTrue("toString 应包含 IP", str.contains("192.168.1.1"));
        assertTrue("toString 应包含端口号", str.contains("8080"));
    }

    /**
     * 测试完整服务器配置场景
     */
    @Test
    public void testCompleteServerConfig() {
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setCustomServerIp("aisf.example.com");
        serverInfo.setWebsocketPort(443);
        serverInfo.setRestfulPort(443);
        serverInfo.setC2cPort(9090);
        serverInfo.setApigwPort(9443);
        assertEquals("aisf.example.com", serverInfo.getServerIp());
        assertEquals(443, serverInfo.getWebsocketPort());
        assertEquals(443, serverInfo.getRestfulPort());
        assertEquals(9090, serverInfo.getC2cPort());
        assertEquals(9443, serverInfo.getApigwPort());
    }

    /**
     * 测试设置 serverEnv 切换到预设环境
     */
    @Test
    public void testServerEnvSwitch() {
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setServerEnv(ServerEnvEnum.CUSTOM_ENV);
        serverInfo.setCustomServerIp("192.168.1.1");
        assertEquals("CUSTOM_ENV 下应返回自定义 IP", "192.168.1.1", serverInfo.getServerIp());
    }
}
