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
 * DeleteScheduleTaskRequest 测试
 */
public class DeleteScheduleTaskRequestTest {

    private DeleteScheduleTaskRequest request;

    @Before
    public void setUp() {
        request = new DeleteScheduleTaskRequest();
    }

    @Test
    public void testDefaultConstructor() {
        assertNull(request.getTaskId());
    }

    @Test
    public void testParameterizedConstructor() {
        DeleteScheduleTaskRequest req = new DeleteScheduleTaskRequest("task-001");
        assertEquals("task-001", req.getTaskId());
    }

    @Test
    public void testSetAndGetTaskId() {
        request.setTaskId("task-123");
        assertEquals("task-123", request.getTaskId());
    }

    @Test
    public void testSetNullTaskId() {
        request.setTaskId(null);
        assertNull(request.getTaskId());
    }

    @Test
    public void testSetEmptyTaskId() {
        request.setTaskId("");
        assertEquals("", request.getTaskId());
    }

    @Test
    public void testToString() {
        request.setTaskId("task-abc");
        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("task-abc"));
        assertTrue(str.contains("DeleteScheduleTaskRequest"));
    }

    @Test
    public void testToStringWithNull() {
        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("DeleteScheduleTaskRequest"));
    }

    @Test
    public void testFieldOverwrite() {
        request.setTaskId("id1");
        assertEquals("id1", request.getTaskId());
        request.setTaskId("id2");
        assertEquals("id2", request.getTaskId());
    }
}
