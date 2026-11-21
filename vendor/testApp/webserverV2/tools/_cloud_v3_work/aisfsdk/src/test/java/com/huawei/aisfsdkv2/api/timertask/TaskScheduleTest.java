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
 * TaskSchedule 测试
 */
public class TaskScheduleTest {

    private TaskSchedule taskSchedule;

    @Before
    public void setUp() {
        taskSchedule = new TaskSchedule();
    }

    @Test
    public void testDefaultValues() {
        assertNull(taskSchedule.getTaskId());
        assertFalse(taskSchedule.isEnable());
        assertNull(taskSchedule.getSchedule());
        assertNull(taskSchedule.getAction());
    }

    @Test
    public void testSetAndGetTaskId() {
        taskSchedule.setTaskId("task-001");
        assertEquals("task-001", taskSchedule.getTaskId());
    }

    @Test
    public void testSetEnableTrue() {
        taskSchedule.setEnable(true);
        assertTrue(taskSchedule.isEnable());
    }

    @Test
    public void testSetSchedule() {
        ScheduleInfo schedule = new ScheduleInfo();
        schedule.setType(ScheduleTypeEnum.ONCE);
        taskSchedule.setSchedule(schedule);
        assertNotNull(taskSchedule.getSchedule());
        assertEquals(ScheduleTypeEnum.ONCE, taskSchedule.getSchedule().getType());
    }

    @Test
    public void testSetAction() {
        ScheduleAction action = new ScheduleAction();
        action.setType("remind");
        taskSchedule.setAction(action);
        assertNotNull(taskSchedule.getAction());
        assertEquals("remind", taskSchedule.getAction().getType());
    }

    @Test
    public void testToString() {
        taskSchedule.setTaskId("t-100");
        taskSchedule.setEnable(true);
        String str = taskSchedule.toString();
        assertNotNull(str);
        assertTrue(str.contains("t-100"));
        assertTrue(str.contains("true"));
    }

    @Test
    public void testToStringWithNulls() {
        String str = taskSchedule.toString();
        assertNotNull(str);
        assertTrue(str.contains("TaskSchedule"));
    }
}
