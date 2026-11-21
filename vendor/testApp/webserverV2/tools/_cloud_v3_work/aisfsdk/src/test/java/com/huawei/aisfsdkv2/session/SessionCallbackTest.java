/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.session;

import com.huawei.aisfsdkv2.api.session.SessionCallback;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * SessionCallback 接口测试
 */
public class SessionCallbackTest {
    @Test
    public void testInterfaceExists() {
        assertTrue(SessionCallback.class.isInterface());
    }
}
