/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api;

import com.huawei.aisfsdkv2.api.base.DeviceInfo;
import com.huawei.aisfsdkv2.api.base.KeyFileInfo;
import com.huawei.aisfsdkv2.api.base.ServerInfo;
import com.huawei.aisfsdkv2.api.log.SdkLogConfig;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * AISFClientParam 测试
 */
public class AISFClientParamTest {

    private AISFClientParam param;

    @Before
    public void setUp() {
        param = new AISFClientParam();
    }

    @Test
    public void testDefaultValues() {
        assertNull(param.getSdkRoleName());
        assertNull(param.getAppContext());
        assertNull(param.getDeviceInfo());
        assertNull(param.getSdkLogConfig());
        assertNotNull(param.getServerInfo());
        assertNull(param.getKeyFileInfo());
    }

    @Test
    public void testSetAndGetSdkRoleName() {
        param.setSdkRoleName("role-test");
        assertEquals("role-test", param.getSdkRoleName());
    }

    @Test
    public void testSetNullSdkRoleName() {
        param.setSdkRoleName(null);
        assertNull(param.getSdkRoleName());
    }

    @Test
    public void testSetAndGetDeviceInfo() {
        DeviceInfo deviceInfo = new DeviceInfo();
        param.setDeviceInfo(deviceInfo);
        assertEquals(deviceInfo, param.getDeviceInfo());
    }

    @Test
    public void testSetNullDeviceInfo() {
        param.setDeviceInfo(null);
        assertNull(param.getDeviceInfo());
    }

    @Test
    public void testSetAndGetSdkLogConfig() {
        SdkLogConfig logConfig = new SdkLogConfig();
        param.setSdkLogConfig(logConfig);
        assertEquals(logConfig, param.getSdkLogConfig());
    }

    @Test
    public void testSetNullSdkLogConfig() {
        param.setSdkLogConfig(null);
        assertNull(param.getSdkLogConfig());
    }

    @Test
    public void testSetAndGetServerInfo() {
        ServerInfo serverInfo = new ServerInfo();
        param.setServerInfo(serverInfo);
        assertEquals(serverInfo, param.getServerInfo());
    }

    @Test
    public void testSetAndGetKeyFileInfo() {
        KeyFileInfo keyFileInfo = new KeyFileInfo();
        param.setKeyFileInfo(keyFileInfo);
        assertEquals(keyFileInfo, param.getKeyFileInfo());
    }

    @Test
    public void testSetNullKeyFileInfo() {
        param.setKeyFileInfo(null);
        assertNull(param.getKeyFileInfo());
    }

    @Test
    public void testToStringWithNullContext() {
        param.setSdkRoleName("testRole");
        String str = param.toString();
        assertNotNull(str);
        assertTrue(str.contains("AISFClientParam"));
        assertTrue(str.contains("testRole"));
        assertTrue(str.contains("null"));
    }

    @Test
    public void testToStringWithAllFieldsSet() {
        param.setSdkRoleName("myRole");
        param.setDeviceInfo(new DeviceInfo());
        param.setSdkLogConfig(new SdkLogConfig());
        param.setServerInfo(new ServerInfo());
        param.setKeyFileInfo(new KeyFileInfo());
        String str = param.toString();
        assertNotNull(str);
        assertTrue(str.contains("AISFClientParam"));
        assertTrue(str.contains("myRole"));
    }

    @Test
    public void testDefaultServerInfoNotNull() {
        assertNotNull(param.getServerInfo());
    }

    @Test
    public void testFieldOverwrite() {
        param.setSdkRoleName("role1");
        assertEquals("role1", param.getSdkRoleName());
        param.setSdkRoleName("role2");
        assertEquals("role2", param.getSdkRoleName());
    }
}
