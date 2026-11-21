/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * SessionStatus 单元测试
 *
 * <p>测试会话状态枚举的定义和基本特性</p>
 *
 * @author l00504561
 * @since 2026-03-31
 */
public class SessionStatusTest {

    // ==================== 枚举值存在性测试 ====================

    /**
     * 测试 ACTIVE 枚举值存在
     */
    @Test
    public void testActiveExists() {
        SessionStatus status = SessionStatus.ACTIVE;
        assertNotNull("ACTIVE 不应为 null", status);
        assertEquals("ACTIVE 名称应正确", "ACTIVE", status.name());
    }

    /**
     * 测试 PAUSED 枚举值存在
     */
    @Test
    public void testPausedExists() {
        SessionStatus status = SessionStatus.PAUSED;
        assertNotNull("PAUSED 不应为 null", status);
        assertEquals("PAUSED 名称应正确", "PAUSED", status.name());
    }

    /**
     * 测试 DESTROYED 枚举值存在
     */
    @Test
    public void testDestroyedExists() {
        SessionStatus status = SessionStatus.DESTROYED;
        assertNotNull("DESTROYED 不应为 null", status);
        assertEquals("DESTROYED 名称应正确", "DESTROYED", status.name());
    }

    // ==================== 枚举值数量测试 ====================

    /**
     * 测试枚举值数量
     */
    @Test
    public void testEnumCount() {
        SessionStatus[] values = SessionStatus.values();
        assertEquals("应有 3 个枚举值", 3, values.length);
    }

    /**
     * 测试所有枚举值
     */
    @Test
    public void testAllEnumValues() {
        SessionStatus[] expected = {SessionStatus.ACTIVE, SessionStatus.PAUSED, SessionStatus.DESTROYED};
        SessionStatus[] actual = SessionStatus.values();
        
        for (int i = 0; i < expected.length; i++) {
            assertEquals("枚举值应匹配", expected[i], actual[i]);
        }
    }

    // ==================== 枚举名称测试 ====================

    /**
     * 测试枚举名称
     */
    @Test
    public void testEnumNames() {
        assertEquals("ACTIVE", SessionStatus.ACTIVE.name());
        assertEquals("PAUSED", SessionStatus.PAUSED.name());
        assertEquals("DESTROYED", SessionStatus.DESTROYED.name());
    }

    // ==================== valueOf 测试 ====================

    /**
     * 测试 valueOf - ACTIVE
     */
    @Test
    public void testValueOf_Active() {
        SessionStatus status = SessionStatus.valueOf("ACTIVE");
        assertEquals("应解析为 ACTIVE", SessionStatus.ACTIVE, status);
    }

    /**
     * 测试 valueOf - PAUSED
     */
    @Test
    public void testValueOf_Paused() {
        SessionStatus status = SessionStatus.valueOf("PAUSED");
        assertEquals("应解析为 PAUSED", SessionStatus.PAUSED, status);
    }

    /**
     * 测试 valueOf - DESTROYED
     */
    @Test
    public void testValueOf_Destroyed() {
        SessionStatus status = SessionStatus.valueOf("DESTROYED");
        assertEquals("应解析为 DESTROYED", SessionStatus.DESTROYED, status);
    }

    // ==================== ordinal 测试 ====================

    /**
     * 测试枚举序号
     */
    @Test
    public void testOrdinal() {
        // 注意：ordinal 取决于定义顺序，这里仅验证它们是不同的
        int activeOrdinal = SessionStatus.ACTIVE.ordinal();
        int pausedOrdinal = SessionStatus.PAUSED.ordinal();
        int destroyedOrdinal = SessionStatus.DESTROYED.ordinal();
        
        // 验证每个序号都是唯一的
        assertTrue("序号应唯一", activeOrdinal != pausedOrdinal);
        assertTrue("序号应唯一", pausedOrdinal != destroyedOrdinal);
        assertTrue("序号应唯一", activeOrdinal != destroyedOrdinal);
    }

    // ==================== 相等性测试 ====================

    /**
     * 测试同一枚举值相等
     */
    @Test
    public void testEnumEquality() {
        assertEquals("同一枚举值应相等", SessionStatus.ACTIVE, SessionStatus.ACTIVE);
        assertEquals("同一枚举值应相等", SessionStatus.PAUSED, SessionStatus.PAUSED);
        assertEquals("同一枚举值应相等", SessionStatus.DESTROYED, SessionStatus.DESTROYED);
    }

    /**
     * 测试枚举单例特性
     */
    @Test
    public void testEnumSingleton() {
        // 枚举是单例的，== 和 equals 都应成立
        assertTrue("ACTIVE == ACTIVE", SessionStatus.ACTIVE == SessionStatus.ACTIVE);
        assertTrue("PAUSED == PAUSED", SessionStatus.PAUSED == SessionStatus.PAUSED);
        assertTrue("DESTROYED == DESTROYED", SessionStatus.DESTROYED == SessionStatus.DESTROYED);
    }

    // ==================== toString 测试 ====================

    /**
     * 测试 toString 返回名称
     */
    @Test
    public void testToString() {
        assertEquals("toString 应返回名称", "ACTIVE", SessionStatus.ACTIVE.toString());
        assertEquals("toString 应返回名称", "PAUSED", SessionStatus.PAUSED.toString());
        assertEquals("toString 应返回名称", "DESTROYED", SessionStatus.DESTROYED.toString());
    }

    // ==================== 使用场景测试 ====================

    /**
     * 测试状态切换场景
     */
    @Test
    public void testStatusTransitionScenario() {
        // 模拟会话状态流转
        SessionStatus currentStatus = SessionStatus.ACTIVE;
        
        // ACTIVE -> PAUSED
        currentStatus = SessionStatus.PAUSED;
        assertEquals(SessionStatus.PAUSED, currentStatus);
        
        // PAUSED -> ACTIVE
        currentStatus = SessionStatus.ACTIVE;
        assertEquals(SessionStatus.ACTIVE, currentStatus);
        
        // ACTIVE -> DESTROYED
        currentStatus = SessionStatus.DESTROYED;
        assertEquals(SessionStatus.DESTROYED, currentStatus);
    }

    /**
     * 测试 switch 语句可用性
     */
    @Test
    public void testSwitchStatement() {
        SessionStatus status = SessionStatus.ACTIVE;
        String result;
        
        switch (status) {
            case ACTIVE:
                result = "active";
                break;
            case PAUSED:
                result = "paused";
                break;
            case DESTROYED:
                result = "destroyed";
                break;
            default:
                result = "unknown";
        }
        
        assertEquals("switch 应正确匹配", "active", result);
    }

    // ==================== hashCode 测试 ====================

    /**
     * 测试 hashCode 一致性
     */
    @Test
    public void testHashCode() {
        // 同一枚举值的 hashCode 应一致
        assertEquals("同一枚举值 hashCode 应一致", 
            SessionStatus.ACTIVE.hashCode(), SessionStatus.ACTIVE.hashCode());
        
        // 不同枚举值的 hashCode 可能不同（但不一定）
        // 这里仅验证不会抛出异常
        int activeHash = SessionStatus.ACTIVE.hashCode();
        int pausedHash = SessionStatus.PAUSED.hashCode();
        int destroyedHash = SessionStatus.DESTROYED.hashCode();
        
        assertTrue("hashCode 应为有效整数", activeHash >= Integer.MIN_VALUE);
        assertTrue("hashCode 应为有效整数", pausedHash >= Integer.MIN_VALUE);
        assertTrue("hashCode 应为有效整数", destroyedHash >= Integer.MIN_VALUE);
    }
}
