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
 * ScheduleInfo 测试
 */
public class ScheduleInfoTest {

    private ScheduleInfo scheduleInfo;

    @Before
    public void setUp() {
        scheduleInfo = new ScheduleInfo();
    }

    @Test
    public void testDefaultValues() {
        assertNull(scheduleInfo.getType());
        assertNull(scheduleInfo.getTriggerTime());
        assertNull(scheduleInfo.getCron());
        assertNull(scheduleInfo.getDescription());
    }

    @Test
    public void testSetAndGetType() {
        scheduleInfo.setType(ScheduleTypeEnum.ONCE);
        assertEquals(ScheduleTypeEnum.ONCE, scheduleInfo.getType());
    }

    @Test
    public void testSetAndGetTriggerTime() {
        scheduleInfo.setTriggerTime("2026-04-13T10:00:00");
        assertEquals("2026-04-13T10:00:00", scheduleInfo.getTriggerTime());
    }

    @Test
    public void testSetAndGetCron() {
        scheduleInfo.setCron("0 0 8 * * ?");
        assertEquals("0 0 8 * * ?", scheduleInfo.getCron());
    }

    @Test
    public void testSetAndGetDescription() {
        scheduleInfo.setDescription("Daily reminder");
        assertEquals("Daily reminder", scheduleInfo.getDescription());
    }

    @Test
    public void testToString() {
        scheduleInfo.setType(ScheduleTypeEnum.RECURRING);
        scheduleInfo.setCron("0 30 9 * * ?");
        scheduleInfo.setDescription("Morning alarm");
        String str = scheduleInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("RECURRING"));
        assertTrue(str.contains("0 30 9 * * ?"));
        assertTrue(str.contains("Morning alarm"));
    }

    @Test
    public void testToStringWithNulls() {
        String str = scheduleInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("ScheduleInfo"));
    }

    @Test
    public void testRecurringType() {
        scheduleInfo.setType(ScheduleTypeEnum.RECURRING);
        assertEquals(ScheduleTypeEnum.RECURRING, scheduleInfo.getType());
    }
}
