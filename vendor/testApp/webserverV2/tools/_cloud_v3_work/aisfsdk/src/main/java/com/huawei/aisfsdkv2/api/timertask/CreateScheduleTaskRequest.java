/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.timertask;

/**
 * 创建定时任务请求
 *
 * @since 2026-04-08
 */
public class CreateScheduleTaskRequest {

    /**
     * 定时任务是否启用
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
    public CreateScheduleTaskRequest() {
    }

    /**
     * 带参数的构造方法
     *
     * @param enable   定时任务是否启用
     * @param schedule 调度信息
     * @param action   调度动作
     */
    public CreateScheduleTaskRequest(boolean enable, ScheduleInfo schedule, ScheduleAction action) {
        this.enable = enable;
        this.schedule = schedule;
        this.action = action;
    }

    /**
     * 获取定时任务是否启用
     *
     * @return true 表示启用
     */
    public boolean isEnable() {
        return enable;
    }

    /**
     * 设置定时任务是否启用
     *
     * @param enable 定时任务是否启用
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
        return "CreateScheduleTaskRequest{enable=" + enable + ", schedule=" + schedule + ", action=" + action + "}";
    }
}
