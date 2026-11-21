/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.base;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * DeviceInfo 单元测试
 *
 * <p>测试设备信息模型的核心字段</p>
 *
 * @author l00504561
 * @since 2026-03-31
 */
public class DeviceInfoTest {

    // ==================== 基本构造测试 ====================

    /**
     * 测试默认构造
     */
    @Test
    public void testDefaultConstructor() {
        DeviceInfo deviceInfo = new DeviceInfo();
        assertNotNull("构造后对象不应为null", deviceInfo);
    }

    // ==================== DeviceUuid 测试 ====================

    /**
     * 测试 DeviceUuid setter/getter
     */
    @Test
    public void testDeviceUuid() {
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.setDeviceUuid("uuid-123-abc");
        assertEquals("DeviceUuid 应正确设置", "uuid-123-abc", deviceInfo.getDeviceUuid());
    }

    /**
     * 测试 DeviceUuid 设置为 null
     */
    @Test
    public void testDeviceUuid_Null() {
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.setDeviceUuid(null);
        assertEquals("DeviceUuid 可为 null", null, deviceInfo.getDeviceUuid());
    }

    // ==================== DeviceType 测试 ====================

    /**
     * 测试默认 DeviceType
     */
    @Test
    public void testDefaultDeviceType() {
        DeviceInfo deviceInfo = new DeviceInfo();
        assertEquals("默认 DeviceType 应为 CLOUD_PHONE", 
            DeviceTypeEnum.CLOUD_PHONE, deviceInfo.getDeviceType());
    }

    /**
     * 测试 DeviceType setter/getter
     */
    @Test
    public void testDeviceType() {
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.setDeviceType(DeviceTypeEnum.PHY_PHONE);
        assertEquals("DeviceType 应正确设置", 
            DeviceTypeEnum.PHY_PHONE, deviceInfo.getDeviceType());
    }

    /**
     * 测试所有 DeviceTypeEnum 值
     */
    @Test
    public void testAllDeviceTypes() {
        DeviceInfo deviceInfo = new DeviceInfo();
        
        for (DeviceTypeEnum type : DeviceTypeEnum.values()) {
            deviceInfo.setDeviceType(type);
            assertEquals("DeviceType " + type + " 应正确设置", type, deviceInfo.getDeviceType());
        }
    }

    // ==================== AppName 测试 ====================

    /**
     * 测试 AppName setter/getter
     */
    @Test
    public void testAppName() {
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.setAppName("TestApp");
        assertEquals("AppName 应正确设置", "TestApp", deviceInfo.getAppName());
    }

    /**
     * 测试 AppName 设置为 null（会使用默认值）
     */
    @Test
    public void testAppName_Null() {
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.setAppName(null);
        // 当 appName 为 null 时，getAppName 会尝试获取应用包名
        // 在测试环境中可能返回空字符串
        String appName = deviceInfo.getAppName();
        assertNotNull("AppName 不应为 null", appName);
    }

    // ==================== 综合测试 ====================

    /**
     * 测试所有字段独立设置
     */
    @Test
    public void testAllFieldsIndependent() {
        DeviceInfo deviceInfo = new DeviceInfo();
        
        deviceInfo.setDeviceUuid("device-uuid-123");
        deviceInfo.setDeviceType(DeviceTypeEnum.CLOUD_PHONE);
        deviceInfo.setAppName("MyApp");
        
        assertEquals("device-uuid-123", deviceInfo.getDeviceUuid());
        assertEquals(DeviceTypeEnum.CLOUD_PHONE, deviceInfo.getDeviceType());
        assertEquals("MyApp", deviceInfo.getAppName());
    }

    /**
     * 测试多次设置同一字段
     */
    @Test
    public void testFieldOverwrite() {
        DeviceInfo deviceInfo = new DeviceInfo();
        
        deviceInfo.setDeviceUuid("uuid-1");
        assertEquals("uuid-1", deviceInfo.getDeviceUuid());
        
        deviceInfo.setDeviceUuid("uuid-2");
        assertEquals("uuid-2", deviceInfo.getDeviceUuid());
        
        deviceInfo.setDeviceUuid(null);
        assertEquals(null, deviceInfo.getDeviceUuid());
    }

    /**
     * 测试 DeviceType 切换
     */
    @Test
    public void testDeviceTypeSwitch() {
        DeviceInfo deviceInfo = new DeviceInfo();
        
        deviceInfo.setDeviceType(DeviceTypeEnum.PHY_PHONE);
        assertEquals(DeviceTypeEnum.PHY_PHONE, deviceInfo.getDeviceType());
        
        deviceInfo.setDeviceType(DeviceTypeEnum.CLOUD_PHONE);
        assertEquals(DeviceTypeEnum.CLOUD_PHONE, deviceInfo.getDeviceType());
    }

    /**
     * 测试 toString 方法
     */
    @Test
    public void testToString() {
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.setDeviceUuid("test-uuid");
        
        String str = deviceInfo.toString();
        assertNotNull("toString 不应返回 null", str);
        assertTrue("toString 应返回非空字符串", str.length() > 0);
        assertTrue("toString 应包含 deviceUuid", str.contains("test-uuid"));
    }

    /**
     * 测试空对象状态
     */
    @Test
    public void testEmptyObject() {
        DeviceInfo deviceInfo = new DeviceInfo();
        
        assertEquals("默认 DeviceUuid 应为 null", null, deviceInfo.getDeviceUuid());
        assertEquals("默认 DeviceType 应为 CLOUD_PHONE", 
            DeviceTypeEnum.CLOUD_PHONE, deviceInfo.getDeviceType());
    }
}
