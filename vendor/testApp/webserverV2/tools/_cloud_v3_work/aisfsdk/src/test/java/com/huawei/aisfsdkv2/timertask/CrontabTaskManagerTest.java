/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.timertask;

import com.huawei.aisfsdkv2.api.timertask.ScheduleInfo;
import com.huawei.aisfsdkv2.api.timertask.ScheduleTypeEnum;
import com.huawei.aisfsdkv2.api.timertask.TaskSchedule;
import org.junit.Before;
import org.junit.Test;
import java.util.function.Consumer;
import static org.junit.Assert.*;

/**
 * CrontabTaskManager 单元测试
 */
public class CrontabTaskManagerTest {

    private CrontabTaskManager taskManager;

    @Before
    public void setUp() {
        taskManager = CrontabTaskManager.getInstance();
    }

    @Test
    public void testGetInstance_NotNull() {
        assertNotNull(CrontabTaskManager.getInstance());
    }

    @Test
    public void testGetInstance_SameInstance() {
        assertSame(CrontabTaskManager.getInstance(), CrontabTaskManager.getInstance());
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
        TaskSchedule task = createCronTask("test-cron-1", "* * * * *");
        taskManager.addTask(task, null);
    }

    @Test
    public void testAddTask_NullSchedule_Skipped() {
        TaskSchedule task = new TaskSchedule();
        task.setTaskId("test-null-schedule");
        Consumer<TaskSchedule> callback = t -> {};
        taskManager.addTask(task, callback);
    }

    @Test
    public void testAddTask_NullCron_Skipped() {
        TaskSchedule task = new TaskSchedule();
        task.setTaskId("test-null-cron");
        ScheduleInfo schedule = new ScheduleInfo();
        schedule.setType(ScheduleTypeEnum.RECURRING);
        task.setSchedule(schedule);
        Consumer<TaskSchedule> callback = t -> {};
        taskManager.addTask(task, callback);
    }

    @Test
    public void testAddTask_InvalidCron_Skipped() {
        TaskSchedule task = createCronTask("test-invalid-cron", "invalid-cron");
        Consumer<TaskSchedule> callback = t -> {};
        taskManager.addTask(task, callback);
    }

    @Test
    public void testAddTask_ValidCron() {
        String taskId = "test-valid-cron-" + System.currentTimeMillis();
        TaskSchedule task = createCronTask(taskId, "0 * * * *");
        Consumer<TaskSchedule> callback = t -> {};
        taskManager.addTask(task, callback);
        taskManager.removeTask(taskId);
    }

    @Test
    public void testAddTask_DuplicateTaskId_Skipped() {
        String taskId = "test-duplicate-cron-" + System.currentTimeMillis();
        TaskSchedule task1 = createCronTask(taskId, "0 * * * *");
        TaskSchedule task2 = createCronTask(taskId, "30 * * * *");
        taskManager.addTask(task1, t -> {});
        taskManager.addTask(task2, t -> {});
        taskManager.removeTask(taskId);
    }

    @Test
    public void testRemoveTask_ExistingTask() {
        String taskId = "test-remove-cron-" + System.currentTimeMillis();
        TaskSchedule task = createCronTask(taskId, "0 * * * *");
        taskManager.addTask(task, t -> {});
        taskManager.removeTask(taskId);
    }

    @Test
    public void testRemoveTask_NonExistingTask() {
        taskManager.removeTask("non-existing-cron-task");
    }

    @Test
    public void testRemoveTask_NullTaskId() {
        taskManager.removeTask(null);
    }

    @Test
    public void testClearAll() {
        String taskId1 = "test-clear-cron-1-" + System.currentTimeMillis();
        String taskId2 = "test-clear-cron-2-" + System.currentTimeMillis();
        TaskSchedule task1 = createCronTask(taskId1, "0 * * * *");
        TaskSchedule task2 = createCronTask(taskId2, "30 * * * *");
        Consumer<TaskSchedule> callback = t -> {};
        taskManager.addTask(task1, callback);
        taskManager.addTask(task2, callback);
        taskManager.clearAll();
    }

    @Test
    public void testClearAll_CanAddNewTaskAfter() {
        taskManager.clearAll();
        String taskId = "test-new-cron-after-clear-" + System.currentTimeMillis();
        TaskSchedule task = createCronTask(taskId, "0 * * * *");
        Consumer<TaskSchedule> callback = t -> {};
        taskManager.addTask(task, callback);
        taskManager.removeTask(taskId);
    }

    @Test
    public void testAddTask_VariousCronExpressions() {
        String[][] cronExpressions = {
                {"test-cron-every-minute", "* * * * *"},
                {"test-cron-hourly", "0 * * * *"},
                {"test-cron-daily", "0 0 * * *"},
                {"test-cron-specific", "30 14 * * 1-5"},
                {"test-cron-range", "0 9-17 * * 1-5"}
        };
        for (String[] pair : cronExpressions) {
            TaskSchedule task = createCronTask(pair[0] + "-" + System.currentTimeMillis(), pair[1]);
            taskManager.addTask(task, t -> {});
            taskManager.removeTask(task.getTaskId());
        }
    }

    private TaskSchedule createCronTask(String taskId, String cron) {
        TaskSchedule task = new TaskSchedule();
        task.setTaskId(taskId);
        task.setEnable(true);
        ScheduleInfo schedule = new ScheduleInfo();
        schedule.setType(ScheduleTypeEnum.RECURRING);
        schedule.setCron(cron);
        task.setSchedule(schedule);
        return task;
    }
}
