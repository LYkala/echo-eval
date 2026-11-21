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
 * CreateScheduleTaskRequest 测试
 */
public class CreateScheduleTaskRequestTest {

    private CreateScheduleTaskRequest request;

    @Before
    public void setUp() {
        request = new CreateScheduleTaskRequest();
    }

    @Test
    public void testDefaultConstructor() {
        assertFalse(request.isEnable());
        assertNull(request.getSchedule());
        assertNull(request.getAction());
    }

    @Test
    public void testParameterizedConstructor() {
        ScheduleInfo schedule = new ScheduleInfo();
        ScheduleAction action = new ScheduleAction();
        CreateScheduleTaskRequest req = new CreateScheduleTaskRequest(true, schedule, action);
        assertTrue(req.isEnable());
        assertEquals(schedule, req.getSchedule());
        assertEquals(action, req.getAction());
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
    public void testSetNullSchedule() {
        request.setSchedule(null);
        assertNull(request.getSchedule());
    }

    @Test
    public void testSetNullAction() {
        request.setAction(null);
        assertNull(request.getAction());
    }

    @Test
    public void testToString() {
        request.setEnable(true);
        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("CreateScheduleTaskRequest"));
        assertTrue(str.contains("true"));
    }

    @Test
    public void testToStringWithNulls() {
        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("CreateScheduleTaskRequest"));
    }
}
