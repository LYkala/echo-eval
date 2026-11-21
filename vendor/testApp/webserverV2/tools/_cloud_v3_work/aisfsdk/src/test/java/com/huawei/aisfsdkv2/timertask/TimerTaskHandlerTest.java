/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.timertask;

import com.huawei.aisfsdkv2.api.timertask.ScheduleAction;
import com.huawei.aisfsdkv2.api.timertask.TaskSchedule;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * TimerTaskHandler 单元测试（仅测试防御性分支，不依赖 ACM/Session 外部组件）
 */
public class TimerTaskHandlerTest {

    private final TimerTaskHandler handler = new TimerTaskHandler();

    // ======= accept - null/空值防御 =======

    @Test
    public void testAccept_NullTask_NoException() {
        handler.accept(null);
    }

    @Test
    public void testAccept_NullAction_NoException() {
        TaskSchedule task = new TaskSchedule();
        task.setTaskId("task-1");
        task.setAction(null);
        handler.accept(task); // action is null, should log and return
    }

    @Test
    public void testAccept_EmptyBody_NoException() {
        TaskSchedule task = new TaskSchedule();
        task.setTaskId("task-1");
        ScheduleAction action = new ScheduleAction();
        action.setBody("");
        task.setAction(action);
        handler.accept(task); // body is empty, should log and return
    }

    @Test
    public void testAccept_NullBody_NoException() {
        TaskSchedule task = new TaskSchedule();
        task.setTaskId("task-1");
        ScheduleAction action = new ScheduleAction();
        action.setBody(null);
        task.setAction(action);
        handler.accept(task);
    }

    // ======= handler instance =======

    @Test
    public void testHandlerNotNull() {
        assertNotNull(handler);
    }
}
