/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * NotifyInfo 测试
 */
public class NotifyInfoTest {

    private NotifyInfo notifyInfo;

    @Before
    public void setUp() {
        notifyInfo = new NotifyInfo();
    }

    @Test
    public void testDefaultValues() {
        assertEquals(0, notifyInfo.getCode());
        assertNotNull(notifyInfo);
    }

    @Test
    public void testSetAndGetCode() {
        notifyInfo.setCode(100);
        assertEquals(100, notifyInfo.getCode());
    }

    @Test
    public void testToString() {
        notifyInfo.setCode(200);
        String str = notifyInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("200"));
    }

    @Test
    public void testNegativeCode() {
        notifyInfo.setCode(-1);
        assertEquals(-1, notifyInfo.getCode());
    }
}
