/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.timertask;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * ScheduleAction 测试
 */
public class ScheduleActionTest {

    private ScheduleAction action;

    @Before
    public void setUp() {
        action = new ScheduleAction();
    }

    @Test
    public void testDefaultValues() {
        assertNull(action.getType());
        assertNull(action.getTitle());
        assertNull(action.getBody());
    }

    @Test
    public void testSetAndGetType() {
        action.setType("remind");
        assertEquals("remind", action.getType());
    }

    @Test
    public void testSetAndGetTitle() {
        action.setTitle("Meeting Reminder");
        assertEquals("Meeting Reminder", action.getTitle());
    }

    @Test
    public void testSetAndGetBody() {
        action.setBody("Team meeting at 10am");
        assertEquals("Team meeting at 10am", action.getBody());
    }

    @Test
    public void testToString() {
        action.setType("alarm");
        action.setTitle("Wake up");
        action.setBody("Time to get up!");
        String str = action.toString();
        assertNotNull(str);
        assertTrue(str.contains("alarm"));
        assertTrue(str.contains("Wake up"));
        assertTrue(str.contains("Time to get up!"));
    }

    @Test
    public void testToStringWithNulls() {
        String str = action.toString();
        assertNotNull(str);
        assertTrue(str.contains("ScheduleAction"));
    }
}
