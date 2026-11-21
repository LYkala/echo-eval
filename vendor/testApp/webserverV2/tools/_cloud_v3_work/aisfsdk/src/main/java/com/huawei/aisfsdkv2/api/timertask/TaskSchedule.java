/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.timertask;

/**
 * 通通任务信息
 *
 * @since 2026-04-07
 */
public class TaskSchedule {
    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 是否启用
     */
    private boolean enable;

    /**
     * 调度信息
     */
    private ScheduleInfo schedule;

    /**
     * 调度动作
     */
    private ScheduleAction action;

    /**
     * 默认构造方法
     */
    public TaskSchedule() {
    }

    /**
     * 获取任务ID
     *
     * @return 任务ID
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * 设置任务ID
     *
     * @param taskId 任务ID
     */
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    /**
     * 获取是否启用
     *
     * @return true表示启用
     */
    public boolean isEnable() {
        return enable;
    }

    /**
     * 设置是否启用
     *
     * @param enable 是否启用
     */
    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    /**
     * 获取调度信息
     *
     * @return 调度信息
     */
    public ScheduleInfo getSchedule() {
        return schedule;
    }

    /**
     * 设置调度信息
     *
     * @param schedule 调度信息
     */
    public void setSchedule(ScheduleInfo schedule) {
        this.schedule = schedule;
    }

    /**
     * 获取调度动作
     *
     * @return 调度动作
     */
    public ScheduleAction getAction() {
        return action;
    }

    /**
     * 设置调度动作
     *
     * @param action 调度动作
     */
    public void setAction(ScheduleAction action) {
        this.action = action;
    }

    @Override
    public String toString() {
        return "TaskSchedule{taskId='" + taskId + "', enable=" + enable
                + ", schedule=" + schedule + ", action=" + action + "}";
    }
}
