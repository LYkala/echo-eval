/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.session;

import com.huawei.aisfsdkv2.api.session.SessionStatus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * SessionStatus 枚举测试
 */
public class SessionStatusTest {
    @Test
    public void testEnumValues() {
        assertEquals(3, SessionStatus.values().length);
        assertNotNull(SessionStatus.ACTIVE);
        assertNotNull(SessionStatus.PAUSED);
        assertNotNull(SessionStatus.DESTROYED);
    }
}
