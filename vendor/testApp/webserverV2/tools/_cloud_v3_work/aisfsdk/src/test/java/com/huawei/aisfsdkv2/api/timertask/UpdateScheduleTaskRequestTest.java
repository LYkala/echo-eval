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
 * UpdateScheduleTaskRequest 测试
 */
public class UpdateScheduleTaskRequestTest {

    private UpdateScheduleTaskRequest request;

    @Before
    public void setUp() {
        request = new UpdateScheduleTaskRequest();
    }

    @Test
    public void testDefaultConstructor() {
        assertNull(request.getTaskId());
        assertFalse(request.isEnable());
        assertNull(request.getSchedule());
        assertNull(request.getAction());
    }

    @Test
    public void testParameterizedConstructor() {
        ScheduleInfo schedule = new ScheduleInfo();
        ScheduleAction action = new ScheduleAction();
        UpdateScheduleTaskRequest req = new UpdateScheduleTaskRequest("task-1", true, schedule, action);
        assertEquals("task-1", req.getTaskId());
        assertTrue(req.isEnable());
        assertEquals(schedule, req.getSchedule());
        assertEquals(action, req.getAction());
    }

    @Test
    public void testSetAndGetTaskId() {
        request.setTaskId("task-123");
        assertEquals("task-123", request.getTaskId());
    }

    @Test
    public void testSetAndGetEnable() {
        request.setEnable(true);
        assertTrue(request.isEnable());
        request.setEnable(false);
        assertFalse(request.isEnable());
    }

    @Test
    public void testSetAndGetSchedule() {
        ScheduleInfo schedule = new ScheduleInfo();
        request.setSchedule(schedule);
        assertEquals(schedule, request.getSchedule());
    }

    @Test
    public void testSetAndGetAction() {
        ScheduleAction action = new ScheduleAction();
        request.setAction(action);
        assertEquals(action, request.getAction());
    }

    @Test
    public void testSetNullValues() {
        request.setTaskId(null);
        request.setSchedule(null);
        request.setAction(null);
        assertNull(request.getTaskId());
        assertNull(request.getSchedule());
        assertNull(request.getAction());
    }

    @Test
    public void testToString() {
        request.setTaskId("task-xyz");
        request.setEnable(true);
        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("task-xyz"));
        assertTrue(str.contains("UpdateScheduleTaskRequest"));
        assertTrue(str.contains("true"));
    }

    @Test
    public void testToStringWithNulls() {
        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("UpdateScheduleTaskRequest"));
    }

    @Test
    public void testFieldOverwrite() {
        request.setTaskId("id1");
        assertEquals("id1", request.getTaskId());
        request.setTaskId("id2");
        assertEquals("id2", request.getTaskId());
    }
}
