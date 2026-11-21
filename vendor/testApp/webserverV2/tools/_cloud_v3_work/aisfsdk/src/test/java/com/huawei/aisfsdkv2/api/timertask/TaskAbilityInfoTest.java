/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.timertask;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TaskAbilityInfo 测试
 */
public class TaskAbilityInfoTest {

    private TaskAbilityInfo info;

    @Before
    public void setUp() {
        info = new TaskAbilityInfo();
    }

    @Test
    public void testDefaultValues() {
        assertNull(info.getId());
        assertNull(info.getShowLabel());
        assertFalse(info.isDefault());
        assertNull(info.getDescription());
    }

    @Test
    public void testSetAndGetId() {
        info.setId("ability-001");
        assertEquals("ability-001", info.getId());
    }

    @Test
    public void testSetAndGetShowLabel() {
        info.setShowLabel("Weather Check");
        assertEquals("Weather Check", info.getShowLabel());
    }

    @Test
    public void testSetDefaultTrue() {
        info.setDefault(true);
        assertTrue(info.isDefault());
    }

    @Test
    public void testSetAndGetDescription() {
        info.setDescription("Check the weather forecast");
        assertEquals("Check the weather forecast", info.getDescription());
    }

    @Test
    public void testToString() {
        info.setId("a-1");
        info.setShowLabel("label");
        info.setDefault(true);
        info.setDescription("desc");
        String str = info.toString();
        assertNotNull(str);
        assertTrue(str.contains("a-1"));
        assertTrue(str.contains("label"));
        assertTrue(str.contains("true"));
        assertTrue(str.contains("desc"));
    }

    @Test
    public void testToStringWithNulls() {
        String str = info.toString();
        assertNotNull(str);
        assertTrue(str.contains("TaskAbilityInfo"));
    }
}
