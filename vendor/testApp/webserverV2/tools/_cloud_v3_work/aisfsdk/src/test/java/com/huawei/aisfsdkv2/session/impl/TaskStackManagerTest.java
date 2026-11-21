/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.session.impl;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TaskStackManager 单元测试
 */
public class TaskStackManagerTest {

    private TaskStackManager manager;

    @Before
    public void setUp() {
        manager = new TaskStackManager("test-session");
    }

    // ======= 初始状态 =======

    @Test
    public void testInitialState() {
        assertNull(manager.getCurrentTaskId());
        assertNull(manager.getParentTaskId());
        assertEquals(0, manager.getStackDepth());
        assertTrue(manager.getTaskStack().isEmpty());
    }

    // ======= pushTask - 顶层任务 =======

    @Test
    public void testPushTopLevelTask() {
        manager.pushTask("A");
        assertEquals("A", manager.getCurrentTaskId());
        assertEquals(1, manager.getStackDepth());
    }

    @Test
    public void testPushTopLevelTask_ReplacesExisting() {
        manager.pushTask("A");
        manager.pushTask("B");
        assertEquals("B", manager.getCurrentTaskId());
        assertEquals(1, manager.getStackDepth());
    }

    @Test
    public void testPushNullTask_Ignored() {
        manager.pushTask(null);
        assertEquals(0, manager.getStackDepth());
    }

    @Test
    public void testPushEmptyTask_Ignored() {
        manager.pushTask("");
        assertEquals(0, manager.getStackDepth());
    }

    // ======= pushTask - 子任务 =======

    @Test
    public void testPushChildTask() {
        manager.pushTask("A");
        manager.pushTask("A_1");
        assertEquals("A_1", manager.getCurrentTaskId());
        assertEquals(2, manager.getStackDepth());
    }

    @Test
    public void testPushGrandChildTask() {
        manager.pushTask("A");
        manager.pushTask("A_1");
        manager.pushTask("A_1_2");
        assertEquals("A_1_2", manager.getCurrentTaskId());
        assertEquals(3, manager.getStackDepth());
    }

    @Test
    public void testPushDuplicateTask_Ignored() {
        manager.pushTask("A");
        manager.pushTask("A_1");
        manager.pushTask("A_1"); // duplicate
        assertEquals(2, manager.getStackDepth());
        assertEquals("A_1", manager.getCurrentTaskId());
    }

    @Test
    public void testPushOrphanChildTask_AutoFillsParent() {
        manager.pushTask("A");
        manager.pushTask("A_1_2"); // parent A_1 missing
        List<String> stack = manager.getTaskStack();
        assertTrue(stack.contains("A"));
        assertTrue(stack.contains("A_1"));
        assertTrue(stack.contains("A_1_2"));
    }

    // ======= popTask =======

    @Test
    public void testPopTask() {
        manager.pushTask("A");
        manager.pushTask("A_1");
        manager.popTask("A_1");
        assertNull(manager.getCurrentTaskId());
        assertEquals(0, manager.getStackDepth());
    }

    @Test
    public void testPopTask_RemovesDescendants() {
        manager.pushTask("A");
        manager.pushTask("A_1");
        manager.pushTask("A_1_2");
        manager.popTask("A_1"); // should remove A_1, A_1_2 and parent A
        assertNull(manager.getCurrentTaskId());
        assertEquals(0, manager.getStackDepth());
    }

    @Test
    public void testPopTask_RemovesParentWhenNoOtherDescendants() {
        manager.pushTask("A");
        manager.pushTask("A_1");
        manager.popTask("A_1");
        assertEquals(0, manager.getStackDepth());
    }

    @Test
    public void testPopTask_NotExist_Ignored() {
        manager.pushTask("A");
        manager.popTask("X"); // not in stack
        assertEquals(1, manager.getStackDepth());
    }

    @Test
    public void testPopTask_Null_Ignored() {
        manager.pushTask("A");
        manager.popTask(null);
        assertEquals(1, manager.getStackDepth());
    }

    @Test
    public void testPopTask_Empty_Ignored() {
        manager.pushTask("A");
        manager.popTask("");
        assertEquals(1, manager.getStackDepth());
    }

    @Test
    public void testPopTopLevelTask_StackEmpty() {
        manager.pushTask("A");
        manager.popTask("A");
        assertEquals(0, manager.getStackDepth());
        assertNull(manager.getCurrentTaskId());
    }

    // ======= getParentTaskId =======

    @Test
    public void testGetParentTaskId_SingleTask() {
        manager.pushTask("A");
        assertEquals("A", manager.getParentTaskId());
    }

    @Test
    public void testGetParentTaskId_TwoTasks() {
        manager.pushTask("A");
        manager.pushTask("A_1");
        assertEquals("A", manager.getParentTaskId());
    }

    @Test
    public void testGetParentTaskId_ThreeTasks() {
        manager.pushTask("A");
        manager.pushTask("A_1");
        manager.pushTask("A_1_2");
        assertEquals("A_1", manager.getParentTaskId());
    }

    @Test
    public void testGetParentTaskId_Empty() {
        assertNull(manager.getParentTaskId());
    }

    // ======= clear =======

    @Test
    public void testClear() {
        manager.pushTask("A");
        manager.pushTask("A_1");
        manager.clear();
        assertEquals(0, manager.getStackDepth());
        assertNull(manager.getCurrentTaskId());
    }

    @Test
    public void testClear_EmptyStack_NoError() {
        manager.clear(); // should not throw
        assertEquals(0, manager.getStackDepth());
    }

    // ======= getTaskStack =======

    @Test
    public void testGetTaskStack_ReturnsCopy() {
        manager.pushTask("A");
        List<String> stack1 = manager.getTaskStack();
        List<String> stack2 = manager.getTaskStack();
        assertEquals(stack1, stack2);
        // Modifying returned list should not affect internal state
        stack1.clear();
        assertEquals(1, manager.getStackDepth());
    }

    @Test
    public void testGetTaskStack_Order() {
        manager.pushTask("A");
        manager.pushTask("A_1");
        manager.pushTask("A_1_2");
        List<String> stack = manager.getTaskStack();
        assertEquals("A", stack.get(0));
        assertEquals("A_1", stack.get(1));
        assertEquals("A_1_2", stack.get(2));
    }
}
