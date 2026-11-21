/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.session;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * SessionApiImpl 单元测试
 *
 * <p>注意：SessionApiImpl 的构造函数会触发 SessionManager 初始化，
 * 而 SessionManager 依赖 MessageProcessor，后者在单元测试环境中无法正常工作。
 * 因此，本测试类只测试可以通过反射验证的逻辑，避免触发复杂的依赖链。</p>
 *
 * <p>不使用 Robolectric 以获得正确的 JaCoCo 覆盖率。</p>
 */
public class SessionApiImplTest {

    /**
     * 测试单例模式 - 通过反射验证 Holder 模式
     */
    @Test
    public void testHolderPattern() throws Exception {
        // 验证 Holder 类存在
        Class<?> holderClass = Class.forName(
            "com.huawei.aisfsdkv2.apiimpl.session.SessionApiImpl$Holder");
        assertNotNull(holderClass);

        // 验证 INSTANCE 字段存在
        java.lang.reflect.Field instanceField = holderClass.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Object instance = instanceField.get(null);
        assertNotNull(instance);
        assertTrue(instance instanceof SessionApiImpl);
    }

    /**
     * 测试单例一致性
     */
    @Test
    public void testSingletonConsistency() throws Exception {
        // 通过反射获取 Holder.INSTANCE
        Class<?> holderClass = Class.forName(
            "com.huawei.aisfsdkv2.apiimpl.session.SessionApiImpl$Holder");
        java.lang.reflect.Field instanceField = holderClass.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);

        SessionApiImpl instance1 = (SessionApiImpl) instanceField.get(null);
        SessionApiImpl instance2 = (SessionApiImpl) instanceField.get(null);

        assertSame(instance1, instance2);
    }

    /**
     * 测试 getInstance 方法签名
     */
    @Test
    public void testGetInstanceMethod() throws Exception {
        java.lang.reflect.Method getInstanceMethod = SessionApiImpl.class.getMethod("getInstance");
        assertNotNull(getInstanceMethod);
        assertEquals(SessionApiImpl.class, getInstanceMethod.getReturnType());
    }

    /**
     * 测试 SessionApi 接口方法存在
     */
    @Test
    public void testApiMethodsExist() throws Exception {
        // 验证所有 API 方法存在
        assertNotNull(SessionApiImpl.class.getMethod("createSession",
            Class.forName("com.huawei.aisfsdkv2.api.session.SessionCallback")));
        assertNotNull(SessionApiImpl.class.getMethod("destroySession", String.class));
        assertNotNull(SessionApiImpl.class.getMethod("destroyAllSessions"));
        assertNotNull(SessionApiImpl.class.getMethod("sendMessage",
            String.class, Class.forName("com.huawei.aisfsdkv2.api.session.SessionRequest")));
        assertNotNull(SessionApiImpl.class.getMethod("sendMessage",
            String.class, String.class, java.util.List.class, String.class, String.class));
        assertNotNull(SessionApiImpl.class.getMethod("sendMessage",
            String.class, Class.forName("com.huawei.aisfsdkv2.api.session.GuiTaskEnum"), java.util.Map.class));
        assertNotNull(SessionApiImpl.class.getMethod("stopTask", String.class));
        assertNotNull(SessionApiImpl.class.getMethod("getSessionStatus", String.class));
        assertNotNull(SessionApiImpl.class.getMethod("setSessionStatus",
            String.class, Class.forName("com.huawei.aisfsdkv2.api.session.SessionStatus")));
        assertNotNull(SessionApiImpl.class.getMethod("getAllSessionIds"));
        assertNotNull(SessionApiImpl.class.getMethod("stopMessageHandle"));
        assertNotNull(SessionApiImpl.class.getMethod("resumeMessageHandle"));
        assertNotNull(SessionApiImpl.class.getMethod("isMessageHandlePaused"));
    }

    /**
     * 测试 sessionManager 字段存在且为 final
     */
    @Test
    public void testSessionManagerField() throws Exception {
        java.lang.reflect.Field sessionManagerField = SessionApiImpl.class.getDeclaredField("sessionManager");
        assertNotNull(sessionManagerField);
        assertTrue(java.lang.reflect.Modifier.isFinal(sessionManagerField.getModifiers()));
    }

    /**
     * 测试构造函数存在
     */
    @Test
    public void testConstructorExists() throws Exception {
        java.lang.reflect.Constructor<?>[] constructors = SessionApiImpl.class.getDeclaredConstructors();
        assertTrue(constructors.length > 0);
    }

    /**
     * 测试类实现了 SessionApi 接口
     */
    @Test
    public void testImplementsSessionApi() throws Exception {
        Class<?> sessionApiClass = Class.forName("com.huawei.aisfsdkv2.api.session.SessionApi");
        assertTrue(sessionApiClass.isAssignableFrom(SessionApiImpl.class));
    }

    /**
     * 测试返回值类型 - destroySession 返回 boolean
     */
    @Test
    public void testDestroySessionReturnType() throws Exception {
        java.lang.reflect.Method method = SessionApiImpl.class.getMethod("destroySession", String.class);
        assertEquals(boolean.class, method.getReturnType());
    }

    /**
     * 测试返回值类型 - getSessionStatus 返回 SessionStatus
     */
    @Test
    public void testGetSessionStatusReturnType() throws Exception {
        java.lang.reflect.Method method = SessionApiImpl.class.getMethod("getSessionStatus", String.class);
        assertEquals(Class.forName("com.huawei.aisfsdkv2.api.session.SessionStatus"),
            method.getReturnType());
    }

    /**
     * 测试返回值类型 - getAllSessionIds 返回 List
     */
    @Test
    public void testGetAllSessionIdsReturnType() throws Exception {
        java.lang.reflect.Method method = SessionApiImpl.class.getMethod("getAllSessionIds");
        assertEquals(java.util.List.class, method.getReturnType());
    }

    /**
     * 测试返回值类型 - isMessageHandlePaused 返回 boolean
     */
    @Test
    public void testIsMessageHandlePausedReturnType() throws Exception {
        java.lang.reflect.Method method = SessionApiImpl.class.getMethod("isMessageHandlePaused");
        assertEquals(boolean.class, method.getReturnType());
    }

    /**
     * 测试 destroySession 方法参数注解
     */
    @Test
    public void testDestroySessionParamAnnotation() throws Exception {
        java.lang.reflect.Method method = SessionApiImpl.class.getMethod("destroySession", String.class);
        java.lang.reflect.Parameter[] params = method.getParameters();
        assertEquals(1, params.length);
        assertEquals("sessionId", params[0].getName());
    }

    /**
     * 测试 sendMessage 重载方法数量
     */
    @Test
    public void testSendMessageOverloads() throws Exception {
        java.lang.reflect.Method[] methods = SessionApiImpl.class.getMethods();
        int sendMessageCount = 0;
        for (java.lang.reflect.Method method : methods) {
            if (method.getName().equals("sendMessage")) {
                sendMessageCount++;
            }
        }
        assertEquals(3, sendMessageCount);
    }

    /**
     * 测试类有正确的包名
     */
    @Test
    public void testPackageName() {
        assertEquals("com.huawei.aisfsdkv2.apiimpl.session",
            SessionApiImpl.class.getPackage().getName());
    }
}
