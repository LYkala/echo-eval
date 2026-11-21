/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.accessibility.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * SystemKey 测试
 */
public class SystemKeyTest {

    @Test
    public void testEnumValues() {
        SystemKey[] values = SystemKey.values();
        assertEquals(5, values.length);
    }

    @Test
    public void testHomeKey() {
        assertNotNull(SystemKey.HOME);
        assertEquals("HOME", SystemKey.HOME.name());
    }

    @Test
    public void testBackKey() {
        assertNotNull(SystemKey.BACK);
        assertEquals("BACK", SystemKey.BACK.name());
    }

    @Test
    public void testRecentsKey() {
        assertNotNull(SystemKey.RECENTS);
        assertEquals("RECENTS", SystemKey.RECENTS.name());
    }

    @Test
    public void testEnterKey() {
        assertNotNull(SystemKey.ENTER);
        assertEquals("ENTER", SystemKey.ENTER.name());
    }

    @Test
    public void testPowerKey() {
        assertNotNull(SystemKey.POWER);
        assertEquals("POWER", SystemKey.POWER.name());
    }

    @Test
    public void testValueOf() {
        assertEquals(SystemKey.HOME, SystemKey.valueOf("HOME"));
        assertEquals(SystemKey.BACK, SystemKey.valueOf("BACK"));
        assertEquals(SystemKey.RECENTS, SystemKey.valueOf("RECENTS"));
        assertEquals(SystemKey.ENTER, SystemKey.valueOf("ENTER"));
        assertEquals(SystemKey.POWER, SystemKey.valueOf("POWER"));
    }

    @Test
    public void testOrdinalOrder() {
        assertEquals(0, SystemKey.HOME.ordinal());
        assertEquals(1, SystemKey.BACK.ordinal());
        assertEquals(2, SystemKey.RECENTS.ordinal());
        assertEquals(3, SystemKey.ENTER.ordinal());
        assertEquals(4, SystemKey.POWER.ordinal());
    }
}
