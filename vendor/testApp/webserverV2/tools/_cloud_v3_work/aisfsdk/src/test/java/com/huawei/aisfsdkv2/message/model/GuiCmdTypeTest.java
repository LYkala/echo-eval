/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * GuiCmdType 单元测试
 *
 * <p>测试 GUI 命令类型枚举</p>
 *
 * @author l00504561
 * @since 2026-03-31
 */
public class GuiCmdTypeTest {

    // ==================== 枚举值存在性测试 ====================

    /**
     * 测试 UNKNOWN 枚举值存在
     */
    @Test
    public void testUnknownExists() {
        GuiCmdType type = GuiCmdType.UNKNOWN;
        assertNotNull("UNKNOWN 不应为 null", type);
        assertEquals("UNKNOWN 名称应正确", "UNKNOWN", type.name());
    }

    /**
     * 测试 CHECK_APP_INSTALLED 枚举值存在
     */
    @Test
    public void testCheckAppInstalledExists() {
        GuiCmdType type = GuiCmdType.CHECK_APP_INSTALLED;
        assertNotNull("CHECK_APP_INSTALLED 不应为 null", type);
        assertEquals("CHECK_APP_INSTALLED 名称应正确", "CHECK_APP_INSTALLED", type.name());
    }

    /**
     * 测试 SCREEN_SHOT 枚举值存在
     */
    @Test
    public void testScreenShotExists() {
        GuiCmdType type = GuiCmdType.SCREEN_SHOT;
        assertNotNull("SCREEN_SHOT 不应为 null", type);
        assertEquals("SCREEN_SHOT 名称应正确", "SCREEN_SHOT", type.name());
    }

    /**
     * 测试 APP_INSTALL_STATUS 枚举值存在
     */
    @Test
    public void testAppInstallStatusExists() {
        GuiCmdType type = GuiCmdType.APP_INSTALL_STATUS;
        assertNotNull("APP_INSTALL_STATUS 不应为 null", type);
        assertEquals("APP_INSTALL_STATUS 名称应正确", "APP_INSTALL_STATUS", type.name());
    }

    // ==================== 枚举值数量测试 ====================

    /**
     * 测试枚举值数量
     */
    @Test
    public void testEnumCount() {
        GuiCmdType[] values = GuiCmdType.values();
        assertTrue("枚举值数量应大于 0", values.length > 0);
        assertTrue("枚举值数量应至少包含已知值", values.length >= 4);
    }

    /**
     * 测试所有枚举值不为 null
     */
    @Test
    public void testAllEnumValuesNotNull() {
        for (GuiCmdType type : GuiCmdType.values()) {
            assertNotNull("每个枚举值不应为 null", type);
        }
    }

    // ==================== valueOf 测试 ====================

    /**
     * 测试 valueOf - UNKNOWN
     */
    @Test
    public void testValueOf_Unknown() {
        GuiCmdType type = GuiCmdType.valueOf("UNKNOWN");
        assertEquals("应解析为 UNKNOWN", GuiCmdType.UNKNOWN, type);
    }

    /**
     * 测试 valueOf - CHECK_APP_INSTALLED
     */
    @Test
    public void testValueOf_CheckAppInstalled() {
        GuiCmdType type = GuiCmdType.valueOf("CHECK_APP_INSTALLED");
        assertEquals("应解析为 CHECK_APP_INSTALLED", GuiCmdType.CHECK_APP_INSTALLED, type);
    }

    /**
     * 测试 valueOf - SCREEN_SHOT
     */
    @Test
    public void testValueOf_ScreenShot() {
        GuiCmdType type = GuiCmdType.valueOf("SCREEN_SHOT");
        assertEquals("应解析为 SCREEN_SHOT", GuiCmdType.SCREEN_SHOT, type);
    }

    // ==================== ordinal 测试 ====================

    /**
     * 测试枚举序号唯一性
     */
    @Test
    public void testOrdinalUniqueness() {
        GuiCmdType[] values = GuiCmdType.values();
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                assertTrue("每个枚举值的 ordinal 应唯一",
                    values[i].ordinal() != values[j].ordinal());
            }
        }
    }

    // ==================== 相等性测试 ====================

    /**
     * 测试同一枚举值相等
     */
    @Test
    public void testEnumEquality() {
        assertEquals("同一枚举值应相等", GuiCmdType.UNKNOWN, GuiCmdType.UNKNOWN);
        assertEquals("同一枚举值应相等", GuiCmdType.SCREEN_SHOT, GuiCmdType.SCREEN_SHOT);
    }

    /**
     * 测试枚举单例特性
     */
    @Test
    public void testEnumSingleton() {
        assertTrue("UNKNOWN == UNKNOWN", GuiCmdType.UNKNOWN == GuiCmdType.UNKNOWN);
        assertTrue("SCREEN_SHOT == SCREEN_SHOT", GuiCmdType.SCREEN_SHOT == GuiCmdType.SCREEN_SHOT);
    }

    // ==================== toString 测试 ====================

    /**
     * 测试 toString 返回名称
     */
    @Test
    public void testToString() {
        assertEquals("toString 应返回名称", "UNKNOWN", GuiCmdType.UNKNOWN.toString());
        assertEquals("toString 应返回名称", "CHECK_APP_INSTALLED", GuiCmdType.CHECK_APP_INSTALLED.toString());
        assertEquals("toString 应返回名称", "SCREEN_SHOT", GuiCmdType.SCREEN_SHOT.toString());
    }

    // ==================== 使用场景测试 ====================

    /**
     * 测试 switch 语句可用性
     */
    @Test
    public void testSwitchStatement() {
        GuiCmdType type = GuiCmdType.CHECK_APP_INSTALLED;
        String result;
        
        switch (type) {
            case CHECK_APP_INSTALLED:
                result = "check";
                break;
            case SCREEN_SHOT:
                result = "screenshot";
                break;
            case APP_INSTALL_STATUS:
                result = "status";
                break;
            case UNKNOWN:
                result = "unknown";
                break;
            default:
                result = "other";
        }
        
        assertEquals("switch 应正确匹配", "check", result);
    }

    /**
     * 测试枚举作为 Map key
     */
    @Test
    public void testEnumAsMapKey() {
        java.util.Map<GuiCmdType, String> handlerMap = new java.util.HashMap<>();
        handlerMap.put(GuiCmdType.CHECK_APP_INSTALLED, "CheckAppInstalledHandler");
        handlerMap.put(GuiCmdType.SCREEN_SHOT, "ScreenShotHandler");
        handlerMap.put(GuiCmdType.APP_INSTALL_STATUS, "AppInstallStatusHandler");
        
        assertEquals("CheckAppInstalledHandler", handlerMap.get(GuiCmdType.CHECK_APP_INSTALLED));
        assertEquals("ScreenShotHandler", handlerMap.get(GuiCmdType.SCREEN_SHOT));
        assertEquals("AppInstallStatusHandler", handlerMap.get(GuiCmdType.APP_INSTALL_STATUS));
    }

    // ==================== hashCode 测试 ====================

    /**
     * 测试 hashCode 一致性
     */
    @Test
    public void testHashCode() {
        // 同一枚举值的 hashCode 应一致
        assertEquals("同一枚举值 hashCode 应一致",
            GuiCmdType.UNKNOWN.hashCode(), GuiCmdType.UNKNOWN.hashCode());
        
        assertEquals("同一枚举值 hashCode 应一致",
            GuiCmdType.SCREEN_SHOT.hashCode(), GuiCmdType.SCREEN_SHOT.hashCode());
    }
}
