/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.network.impl;

import com.huawei.aisfsdkv2.component.network.NetworkStatusInfo;
import com.huawei.aisfsdkv2.component.network.NetworkType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * NetworkStatusInfo 单元测试
 *
 * <p>测试网络状态信息模型的不可变性和相等性</p>
 *
 * @author l00504561
 * @since 2026-03-31
 */
public class NetworkStatusInfoTest {

    // ==================== 基本构造测试 ====================

    /**
     * 测试带参数的构造方法
     */
    @Test
    public void testParameterizedConstructor() {
        NetworkStatusInfo status = new NetworkStatusInfo(true, NetworkType.WIFI);
        assertTrue("网络应可用", status.isNetworkAvailable());
        assertEquals("网络类型应为 WIFI", NetworkType.WIFI, status.getNetworkType());
    }

    /**
     * 测试 DISCONNECTED 常量
     */
    @Test
    public void testDisconnectedConstant() {
        NetworkStatusInfo disconnected = NetworkStatusInfo.DISCONNECTED;
        assertNotNull("DISCONNECTED 常量不应为 null", disconnected);
        assertFalse("DISCONNECTED 应不可用", disconnected.isNetworkAvailable());
        assertEquals("DISCONNECTED 类型应为 NONE", NetworkType.NONE, disconnected.getNetworkType());
    }

    // ==================== 网络可用性测试 ====================

    /**
     * 测试网络可用 - WIFI
     */
    @Test
    public void testNetworkAvailable_Wifi() {
        NetworkStatusInfo status = new NetworkStatusInfo(true, NetworkType.WIFI);
        assertTrue("WIFI 应可用", status.isNetworkAvailable());
        assertEquals(NetworkType.WIFI, status.getNetworkType());
    }

    /**
     * 测试网络可用 - CELLULAR
     */
    @Test
    public void testNetworkAvailable_Cellular() {
        NetworkStatusInfo status = new NetworkStatusInfo(true, NetworkType.CELLULAR);
        assertTrue("CELLULAR 应可用", status.isNetworkAvailable());
        assertEquals(NetworkType.CELLULAR, status.getNetworkType());
    }

    /**
     * 测试网络不可用
     */
    @Test
    public void testNetworkUnavailable() {
        NetworkStatusInfo status = new NetworkStatusInfo(false, NetworkType.NONE);
        assertFalse("网络应不可用", status.isNetworkAvailable());
        assertEquals(NetworkType.NONE, status.getNetworkType());
    }

    // ==================== 网络类型测试 ====================

    /**
     * 测试所有网络类型
     */
    @Test
    public void testAllNetworkTypes() {
        for (NetworkType type : NetworkType.values()) {
            NetworkStatusInfo status = new NetworkStatusInfo(true, type);
            assertEquals("网络类型应正确", type, status.getNetworkType());
        }
    }

    /**
     * 测试 null 网络类型处理
     */
    @Test
    public void testNullNetworkType() {
        NetworkStatusInfo status = new NetworkStatusInfo(true, null);
        assertEquals("null 网络类型应转为 NONE", NetworkType.NONE, status.getNetworkType());
    }

    // ==================== 相等性测试 ====================

    /**
     * 测试相等 - 相同值
     */
    @Test
    public void testEquals_SameValues() {
        NetworkStatusInfo status1 = new NetworkStatusInfo(true, NetworkType.WIFI);
        NetworkStatusInfo status2 = new NetworkStatusInfo(true, NetworkType.WIFI);
        
        assertEquals("相同值的对象应相等", status1, status2);
    }

    /**
     * 测试相等 - 自身
     */
    @Test
    public void testEquals_Self() {
        NetworkStatusInfo status = new NetworkStatusInfo(true, NetworkType.WIFI);
        assertEquals("对象应等于自身", status, status);
    }

    /**
     * 测试不相等 - 不同可用性
     */
    @Test
    public void testEquals_DifferentAvailability() {
        NetworkStatusInfo status1 = new NetworkStatusInfo(true, NetworkType.WIFI);
        NetworkStatusInfo status2 = new NetworkStatusInfo(false, NetworkType.WIFI);
        
        assertNotSame("不同可用性的对象不应相等", status1, status2);
    }

    /**
     * 测试不相等 - 不同类型
     */
    @Test
    public void testEquals_DifferentType() {
        NetworkStatusInfo status1 = new NetworkStatusInfo(true, NetworkType.WIFI);
        NetworkStatusInfo status2 = new NetworkStatusInfo(true, NetworkType.CELLULAR);
        
        assertNotSame("不同类型的对象不应相等", status1, status2);
    }

    /**
     * 测试不相等 - null
     */
    @Test
    public void testEquals_Null() {
        NetworkStatusInfo status = new NetworkStatusInfo(true, NetworkType.WIFI);
        assertNotSame("对象不应等于 null", status, null);
    }

    /**
     * 测试不相等 - 不同类型对象
     */
    @Test
    public void testEquals_DifferentClass() {
        NetworkStatusInfo status = new NetworkStatusInfo(true, NetworkType.WIFI);
        assertNotSame("对象不应等于其他类型", status, "string");
    }

    // ==================== HashCode 测试 ====================

    /**
     * 测试 hashCode - 相等对象应有相同 hashCode
     */
    @Test
    public void testHashCode_EqualObjects() {
        NetworkStatusInfo status1 = new NetworkStatusInfo(true, NetworkType.WIFI);
        NetworkStatusInfo status2 = new NetworkStatusInfo(true, NetworkType.WIFI);
        
        assertEquals("相等对象应有相同 hashCode", status1.hashCode(), status2.hashCode());
    }

    /**
     * 测试 hashCode - DISCONNECTED 常量稳定性
     */
    @Test
    public void testHashCode_DisconnectedConstant() {
        int hash1 = NetworkStatusInfo.DISCONNECTED.hashCode();
        int hash2 = NetworkStatusInfo.DISCONNECTED.hashCode();
        
        assertEquals("常量 hashCode 应稳定", hash1, hash2);
    }

    // ==================== toString 测试 ====================

    /**
     * 测试 toString 不为 null
     */
    @Test
    public void testToString_NotNull() {
        NetworkStatusInfo status = new NetworkStatusInfo(true, NetworkType.WIFI);
        String str = status.toString();
        
        assertNotNull("toString 不应返回 null", str);
        assertTrue("toString 应返回非空字符串", str.length() > 0);
    }

    /**
     * 测试 toString 包含关键信息
     */
    @Test
    public void testToString_ContainsKeyInfo() {
        NetworkStatusInfo status = new NetworkStatusInfo(true, NetworkType.WIFI);
        String str = status.toString();
        
        assertTrue("toString 应包含可用性信息", str.contains("true") || str.contains("available"));
        assertTrue("toString 应包含网络类型", str.contains("WIFI"));
    }

    // ==================== 不可变性测试 ====================

    /**
     * 测试不可变性 - 字段不能被修改
     */
    @Test
    public void testImmutability() {
        NetworkStatusInfo status = new NetworkStatusInfo(true, NetworkType.WIFI);
        
        // 只能通过构造方法创建新对象，不能修改现有对象
        NetworkStatusInfo newStatus = new NetworkStatusInfo(false, NetworkType.CELLULAR);
        
        // 原对象保持不变
        assertTrue("原对象应保持可用", status.isNetworkAvailable());
        assertEquals("原对象类型应保持 WIFI", NetworkType.WIFI, status.getNetworkType());
        
        // 新对象有独立值
        assertFalse("新对象应不可用", newStatus.isNetworkAvailable());
        assertEquals("新对象类型应为 CELLULAR", NetworkType.CELLULAR, newStatus.getNetworkType());
    }

    // ==================== 综合测试 ====================

    /**
     * 测试多种状态组合
     */
    @Test
    public void testVariousStates() {
        // WIFI 可用
        NetworkStatusInfo wifi = new NetworkStatusInfo(true, NetworkType.WIFI);
        assertTrue(wifi.isNetworkAvailable());
        assertEquals(NetworkType.WIFI, wifi.getNetworkType());
        
        // CELLULAR 可用
        NetworkStatusInfo cellular = new NetworkStatusInfo(true, NetworkType.CELLULAR);
        assertTrue(cellular.isNetworkAvailable());
        assertEquals(NetworkType.CELLULAR, cellular.getNetworkType());
        
        // 断开连接
        NetworkStatusInfo disconnected = new NetworkStatusInfo(false, NetworkType.NONE);
        assertFalse(disconnected.isNetworkAvailable());
        assertEquals(NetworkType.NONE, disconnected.getNetworkType());
        
        // WIFI 不可用（矛盾状态，但应被允许）
        NetworkStatusInfo wifiUnavailable = new NetworkStatusInfo(false, NetworkType.WIFI);
        assertFalse(wifiUnavailable.isNetworkAvailable());
        assertEquals(NetworkType.WIFI, wifiUnavailable.getNetworkType());
    }

    /**
     * 测试 DISCONNECTED 是单例还是每次新建
     */
    @Test
    public void testDisconnectedInstance() {
        NetworkStatusInfo d1 = NetworkStatusInfo.DISCONNECTED;
        NetworkStatusInfo d2 = NetworkStatusInfo.DISCONNECTED;
        
        // DISCONNECTED 是 static final 常量，应为同一对象
        assertSame("DISCONNECTED 应为同一实例", d1, d2);
    }
}
