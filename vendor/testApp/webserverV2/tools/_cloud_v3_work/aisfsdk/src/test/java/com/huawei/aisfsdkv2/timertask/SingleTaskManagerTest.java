/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.timertask;

import com.huawei.aisfsdkv2.api.timertask.ScheduleInfo;
import com.huawei.aisfsdkv2.api.timertask.ScheduleTypeEnum;
import com.huawei.aisfsdkv2.api.timertask.TaskSchedule;
import org.junit.Before;
import org.junit.Test;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import static org.junit.Assert.*;

/**
 * SingleTaskManager 单元测试
 */
public class SingleTaskManagerTest {

    private SingleTaskManager taskManager;

    @Before
    public void setUp() {
        taskManager = SingleTaskManager.getInstance();
    }

    @Test
    public void testGetInstance_NotNull() {
        assertNotNull(SingleTaskManager.getInstance());
    }

    @Test
    public void testGetInstance_SameInstance() {
        assertSame(SingleTaskManager.getInstance(), SingleTaskManager.getInstance());
    }

    @Test
    public void testAddTask_NullTask_Skipped() {
        Consumer<TaskSchedule> callback = t -> {};
        taskManager.addTask(null, callback);
    }

    @Test
    public void testAddTask_NullTaskId_Skipped() {
        TaskSchedule task = new TaskSchedule();
        Consumer<TaskSchedule> callback = t -> {};
        taskManager.addTask(task, callback);
    }

    @Test
    public void testAddTask_NullCallback_Skipped() {
        TaskSchedule task = createTestTask("test-task-1", 5000);
        taskManager.addTask(task, null);
    }

    @Test
    public void testAddTask_NullSchedule_Skipped() {
        TaskSchedule task = new TaskSchedule();
        task.setTaskId("test-task-null-schedule");
        Consumer<TaskSchedule> callback = t -> {};
        taskManager.addTask(task, callback);
    }

    @Test
    public void testAddTask_NullTriggerTime_Skipped() {
        TaskSchedule task = new TaskSchedule();
        task.setTaskId("test-task-null-trigger");
        ScheduleInfo schedule = new ScheduleInfo();
        schedule.setType(ScheduleTypeEnum.ONCE);
        task.setSchedule(schedule);
        Consumer<TaskSchedule> callback = t -> {};
        taskManager.addTask(task, callback);
    }

    @Test
    public void testAddTask_PastTriggerTime_Skipped() {
        TaskSchedule task = createTestTask("test-task-past", -10000);
        Consumer<TaskSchedule> callback = t -> {};
        taskManager.addTask(task, callback);
    }

    @Test
    public void testAddTask_InvalidTriggerTime_Skipped() {
        TaskSchedule task = new TaskSchedule();
        task.setTaskId("test-task-invalid-trigger");
        ScheduleInfo schedule = new ScheduleInfo();
        schedule.setType(ScheduleTypeEnum.ONCE);
        schedule.setTriggerTime("invalid-time");
        task.setSchedule(schedule);
        Consumer<TaskSchedule> callback = t -> {};
        taskManager.addTask(task, callback);
    }

    @Test
    public void testAddTask_DuplicateTaskId_Skipped() {
        String taskId = "test-duplicate-" + System.currentTimeMillis();
        TaskSchedule task1 = createTestTask(taskId, 60000);
        TaskSchedule task2 = createTestTask(taskId, 70000);
        AtomicBoolean callback1Called = new AtomicBoolean(false);
        AtomicBoolean callback2Called = new AtomicBoolean(false);
        taskManager.addTask(task1, t -> callback1Called.set(true));
        taskManager.addTask(task2, t -> callback2Called.set(true));
        taskManager.removeTask(taskId);
        assertFalse(callback1Called.get());
        assertFalse(callback2Called.get());
    }

    @Test
    public void testAddTask_TaskTriggered() throws InterruptedException {
        String taskId = "test-trigger-" + System.currentTimeMillis();
        TaskSchedule task = createTestTask(taskId, 200);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> triggeredTaskId = new AtomicReference<>();
        Consumer<TaskSchedule> callback = t -> {
            triggeredTaskId.set(t.getTaskId());
            latch.countDown();
        };
        taskManager.addTask(task, callback);
        assertTrue("Task should be triggered within 2 seconds", latch.await(2, TimeUnit.SECONDS));
        assertEquals(taskId, triggeredTaskId.get());
    }

    @Test
    public void testAddTask_CallbackException_Handled() throws InterruptedException {
        String taskId = "test-exception-" + System.currentTimeMillis();
        TaskSchedule task = createTestTask(taskId, 200);
        CountDownLatch latch = new CountDownLatch(1);
        Consumer<TaskSchedule> callback = t -> {
            latch.countDown();
            throw new RuntimeException("Test exception");
        };
        taskManager.addTask(task, callback);
        assertTrue("Task should be triggered even with exception", latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void testRemoveTask_ExistingTask() {
        String taskId = "test-remove-" + System.currentTimeMillis();
        TaskSchedule task = createTestTask(taskId, 60000);
        AtomicBoolean callbackCalled = new AtomicBoolean(false);
        taskManager.addTask(task, t -> callbackCalled.set(true));
        taskManager.removeTask(taskId);
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertFalse(callbackCalled.get());
    }

    @Test
    public void testRemoveTask_NonExistingTask() {
        taskManager.removeTask("non-existing-task");
    }

    @Test
    public void testRemoveTask_NullTaskId() {
        taskManager.removeTask(null);
    }

    @Test
    public void testClearAll() {
        String taskId1 = "test-clear-1-" + System.currentTimeMillis();
        String taskId2 = "test-clear-2-" + System.currentTimeMillis();
        TaskSchedule task1 = createTestTask(taskId1, 60000);
        TaskSchedule task2 = createTestTask(taskId2, 70000);
        AtomicBoolean callbackCalled = new AtomicBoolean(false);
        Consumer<TaskSchedule> callback = t -> callbackCalled.set(true);
        taskManager.addTask(task1, callback);
        taskManager.addTask(task2, callback);
        taskManager.clearAll();
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertFalse(callbackCalled.get());
    }

    @Test
    public void testClearAll_CanAddNewTaskAfter() throws InterruptedException {
        taskManager.clearAll();
        String taskId = "test-new-after-clear-" + System.currentTimeMillis();
        TaskSchedule task = createTestTask(taskId, 200);
        CountDownLatch latch = new CountDownLatch(1);
        taskManager.addTask(task, t -> latch.countDown());
        assertTrue("New task should be triggered after clearAll", latch.await(2, TimeUnit.SECONDS));
    }

    private TaskSchedule createTestTask(String taskId, long delayFromNowMs) {
        TaskSchedule task = new TaskSchedule();
        task.setTaskId(taskId);
        task.setEnable(true);
        ScheduleInfo schedule = new ScheduleInfo();
        schedule.setType(ScheduleTypeEnum.ONCE);
        OffsetDateTime triggerTime = OffsetDateTime.now(ZoneOffset.ofHours(8)).plusNanos(delayFromNowMs * 1_000_000);
        schedule.setTriggerTime(triggerTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        task.setSchedule(schedule);
        return task;
    }
}
