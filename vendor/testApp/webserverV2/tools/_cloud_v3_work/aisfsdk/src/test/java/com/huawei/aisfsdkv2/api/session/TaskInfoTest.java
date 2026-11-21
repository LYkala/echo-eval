/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TaskInfo 测试
 */
public class TaskInfoTest {

    private TaskInfo taskInfo;

    @Before
    public void setUp() {
        taskInfo = new TaskInfo();
    }

    @Test
    public void testDefaultValues() {
        assertFalse(taskInfo.isTaskCompleted());
        assertEquals(0, taskInfo.getTaskStatusCode());
        assertNull(taskInfo.getTaskId());
        assertFalse(taskInfo.isGuiTaskRunning());
    }

    @Test
    public void testSetTaskCompleted() {
        taskInfo.setTaskCompleted(true);
        assertTrue(taskInfo.isTaskCompleted());
    }

    @Test
    public void testSetTaskStatusCode() {
        taskInfo.setTaskStatusCode(200);
        assertEquals(200, taskInfo.getTaskStatusCode());
    }

    @Test
    public void testSetTaskStatusCodeError() {
        taskInfo.setTaskStatusCode(500);
        assertEquals(500, taskInfo.getTaskStatusCode());
    }

    @Test
    public void testSetTaskId() {
        taskInfo.setTaskId("task-001");
        assertEquals("task-001", taskInfo.getTaskId());
    }

    @Test
    public void testSetGuiTaskRunning() {
        taskInfo.setGuiTaskRunning(true);
        assertTrue(taskInfo.isGuiTaskRunning());
    }

    @Test
    public void testToString() {
        taskInfo.setTaskCompleted(true);
        taskInfo.setTaskStatusCode(200);
        taskInfo.setTaskId("t-001");
        taskInfo.setGuiTaskRunning(false);
        String str = taskInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("true"));
        assertTrue(str.contains("200"));
        assertTrue(str.contains("t-001"));
    }

    @Test
    public void testToStringWithNullTaskId() {
        String str = taskInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("taskId"));
    }

    @Test
    public void testGuiTaskRunningToggle() {
        taskInfo.setGuiTaskRunning(true);
        assertTrue(taskInfo.isGuiTaskRunning());
        taskInfo.setGuiTaskRunning(false);
        assertFalse(taskInfo.isGuiTaskRunning());
    }
}
