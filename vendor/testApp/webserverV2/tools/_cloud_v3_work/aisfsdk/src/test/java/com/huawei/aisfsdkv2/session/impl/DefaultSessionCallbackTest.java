/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.session.impl;

import com.huawei.aisfsdkv2.api.session.SessionCallback;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * DefaultSessionCallback 单元测试
 */
public class DefaultSessionCallbackTest {

    @Test
    public void testImplementsSessionCallback() {
        DefaultSessionCallback callback = new DefaultSessionCallback();
        assertTrue(callback instanceof SessionCallback);
    }

    @Test
    public void testOnReceived_DoesNotThrow() {
        DefaultSessionCallback callback = new DefaultSessionCallback();
        // 所有参数为 null 时不应抛出异常
        callback.onReceived(0, null, null);
    }

    @Test
    public void testOnReceived_WithErrorCode() {
        DefaultSessionCallback callback = new DefaultSessionCallback();
        callback.onReceived(500, "Internal error", null);
    }

    @Test
    public void testOnReceived_WithMessage() {
        DefaultSessionCallback callback = new DefaultSessionCallback();
        callback.onReceived(0, "Success", null);
    }

    @Test
    public void testInstantiation() {
        DefaultSessionCallback callback = new DefaultSessionCallback();
        assertNotNull(callback);
    }
}
