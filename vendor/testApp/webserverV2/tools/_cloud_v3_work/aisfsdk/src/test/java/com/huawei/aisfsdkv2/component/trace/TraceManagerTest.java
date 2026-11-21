/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.trace;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * TraceManager 测试
 */
public class TraceManagerTest {

    @After
    public void tearDown() {
        TraceManager.getInstance().clearCurrentTaskId();
    }

    @Test
    public void testGetInstanceNotNull() {
        assertNotNull(TraceManager.getInstance());
    }

    @Test
    public void testGetInstanceSingleton() {
        TraceManager instance1 = TraceManager.getInstance();
        TraceManager instance2 = TraceManager.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    public void testDefaultTaskIdIsEmpty() {
        assertEquals("", TraceManager.getInstance().getCurrentTaskId());
    }

    @Test
    public void testSetAndGetCurrentTaskId() {
        TraceManager.getInstance().setCurrentTaskId("task-123");
        assertEquals("task-123", TraceManager.getInstance().getCurrentTaskId());
    }

    @Test
    public void testClearCurrentTaskId() {
        TraceManager.getInstance().setCurrentTaskId("task-abc");
        TraceManager.getInstance().clearCurrentTaskId();
        assertEquals("", TraceManager.getInstance().getCurrentTaskId());
    }

    @Test
    public void testSetNullTaskIdReturnsEmpty() {
        TraceManager.getInstance().setCurrentTaskId(null);
        assertEquals("", TraceManager.getInstance().getCurrentTaskId());
    }

    @Test
    public void testOverwriteTaskId() {
        TraceManager.getInstance().setCurrentTaskId("task-1");
        assertEquals("task-1", TraceManager.getInstance().getCurrentTaskId());
        TraceManager.getInstance().setCurrentTaskId("task-2");
        assertEquals("task-2", TraceManager.getInstance().getCurrentTaskId());
    }

    @Test
    public void testClearTwiceNoException() {
        TraceManager.getInstance().setCurrentTaskId("task-1");
        TraceManager.getInstance().clearCurrentTaskId();
        TraceManager.getInstance().clearCurrentTaskId();
        assertEquals("", TraceManager.getInstance().getCurrentTaskId());
    }

    @Test
    public void testSetEmptyStringTaskId() {
        TraceManager.getInstance().setCurrentTaskId("");
        assertEquals("", TraceManager.getInstance().getCurrentTaskId());
    }

    @Test
    public void testThreadIsolation() throws InterruptedException {
        TraceManager.getInstance().setCurrentTaskId("main-task");
        final String[] childTaskId = new String[1];
        Thread child = new Thread(() -> {
            childTaskId[0] = TraceManager.getInstance().getCurrentTaskId();
        });
        child.start();
        child.join();
        assertEquals("main-task", TraceManager.getInstance().getCurrentTaskId());
        assertEquals("", childTaskId[0]);
    }
}
