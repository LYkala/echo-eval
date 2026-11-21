/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.base;

import com.huawei.aisfsdkv2.api.base.DeviceInfo;
import com.huawei.aisfsdkv2.api.base.KeyFileInfo;
import com.huawei.aisfsdkv2.api.base.ServerInfo;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * BaseApiImpl 单元测试
 *
 * <p>测试基础API实现类的核心功能</p>
 *
 * @author l00504561
 * @since 2026-03-31
 */
public class BaseApiImplTest {

    private BaseApiImpl baseApi;

    @Before
    public void setUp() {
        baseApi = BaseApiImpl.getInstance();
    }

    @After
    public void tearDown() {
        // 清理状态，避免影响其他测试
        baseApi.setAppContext(null);
        baseApi.setDeviceInfo(null);
        baseApi.setKeyFileInfo(null);
        baseApi.setServerInfo(null);
        baseApi.setSdkRoleName(null);
    }

    // ==================== 单例模式测试 ====================

    /**
     * 测试单例模式：多次获取应为同一实例
     */
    @Test
    public void testSingletonInstance() {
        BaseApiImpl instance1 = BaseApiImpl.getInstance();
        BaseApiImpl instance2 = BaseApiImpl.getInstance();
        assertSame("单例模式应返回同一实例", instance1, instance2);
    }

    // ==================== SDK版本测试 ====================

    /**
     * 测试 SDK 版本号
     */
    @Test
    public void testGetSdkVersion() {
        String version = baseApi.getSdkVersion();
        assertNotNull("SDK版本号不应为null", version);
        assertEquals("SDK版本号应为 1.0.0", "1.0.0", version);
    }

    // ==================== SDK角色名称测试 ====================

    /**
     * 测试 SDK 角色名称 setter/getter
     */
    @Test
    public void testSdkRoleName() {
        String roleName = "TestRole";
        baseApi.setSdkRoleName(roleName);
        assertEquals("SDK角色名称应正确设置", roleName, baseApi.getSdkRoleName());
    }

    /**
     * 测试 SDK 角色名称设置 null
     */
    @Test
    public void testSdkRoleName_Null() {
        baseApi.setSdkRoleName(null);
        assertNull("SDK角色名称应可为null", baseApi.getSdkRoleName());
    }

    /**
     * 测试 SDK 角色名称设置空字符串
     */
    @Test
    public void testSdkRoleName_Empty() {
        baseApi.setSdkRoleName("");
        assertEquals("SDK角色名称可设置为空字符串", "", baseApi.getSdkRoleName());
    }

    /**
     * 测试 SDK 角色名称多次设置
     */
    @Test
    public void testSdkRoleName_MultipleSet() {
        baseApi.setSdkRoleName("Role1");
        assertEquals("Role1", baseApi.getSdkRoleName());
        
        baseApi.setSdkRoleName("Role2");
        assertEquals("Role2", baseApi.getSdkRoleName());
        
        baseApi.setSdkRoleName("");
        assertEquals("", baseApi.getSdkRoleName());
    }

    // ==================== 设备信息测试 ====================

    /**
     * 测试设备信息 setter/getter
     */
    @Test
    public void testDeviceInfo() {
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.setDeviceUuid("test-uuid-123");
        
        baseApi.setDeviceInfo(deviceInfo);
        assertSame("设备信息应正确设置", deviceInfo, baseApi.getDeviceInfo());
        assertEquals("设备UUID应正确", "test-uuid-123", baseApi.getDeviceInfo().getDeviceUuid());
    }

    /**
     * 测试设备信息设置为 null
     */
    @Test
    public void testDeviceInfo_Null() {
        baseApi.setDeviceInfo(null);
        assertNull("设备信息应可为null", baseApi.getDeviceInfo());
    }

    // ==================== 关键文件信息测试 ====================

    /**
     * 测试关键文件信息 setter/getter
     */
    @Test
    public void testKeyFileInfo() {
        KeyFileInfo keyFileInfo = new KeyFileInfo();
        // KeyFileInfo 的具体字段设置...
        
        baseApi.setKeyFileInfo(keyFileInfo);
        assertSame("关键文件信息应正确设置", keyFileInfo, baseApi.getKeyFileInfo());
    }

    /**
     * 测试关键文件信息设置为 null
     */
    @Test
    public void testKeyFileInfo_Null() {
        baseApi.setKeyFileInfo(null);
        assertNull("关键文件信息应可为null", baseApi.getKeyFileInfo());
    }

    // ==================== 服务器信息测试 ====================

    /**
     * 测试服务器信息 setter/getter
     */
    @Test
    public void testServerInfo() {
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setCustomServerIp("192.168.1.1");
        serverInfo.setWebsocketPort(8080);

        baseApi.setServerInfo(serverInfo);
        assertSame("服务器信息应正确设置", serverInfo, baseApi.getServerInfo());
        assertEquals("服务器IP应正确", "192.168.1.1", baseApi.getServerInfo().getServerIp());
    }

    /**
     * 测试服务器信息设置为 null
     */
    @Test
    public void testServerInfo_Null() {
        baseApi.setServerInfo(null);
        assertNull("服务器信息应可为null", baseApi.getServerInfo());
    }

    // ==================== 综合测试 ====================

    /**
     * 测试所有字段独立设置
     */
    @Test
    public void testAllFieldsIndependent() {
        // 设置所有字段
        baseApi.setSdkRoleName("Role1");
        baseApi.setDeviceInfo(new DeviceInfo());
        baseApi.setKeyFileInfo(new KeyFileInfo());
        baseApi.setServerInfo(new ServerInfo());
        
        // 验证所有字段都正确设置，互不干扰
        assertNotNull(baseApi.getSdkRoleName());
        assertNotNull(baseApi.getDeviceInfo());
        assertNotNull(baseApi.getKeyFileInfo());
        assertNotNull(baseApi.getServerInfo());
    }

    /**
     * 测试多次设置同一字段覆盖原值
     */
    @Test
    public void testFieldOverwrite() {
        DeviceInfo deviceInfo1 = new DeviceInfo();
        deviceInfo1.setDeviceUuid("uuid-1");
        
        DeviceInfo deviceInfo2 = new DeviceInfo();
        deviceInfo2.setDeviceUuid("uuid-2");
        
        baseApi.setDeviceInfo(deviceInfo1);
        assertEquals("uuid-1", baseApi.getDeviceInfo().getDeviceUuid());
        
        baseApi.setDeviceInfo(deviceInfo2);
        assertEquals("uuid-2", baseApi.getDeviceInfo().getDeviceUuid());
    }
}
