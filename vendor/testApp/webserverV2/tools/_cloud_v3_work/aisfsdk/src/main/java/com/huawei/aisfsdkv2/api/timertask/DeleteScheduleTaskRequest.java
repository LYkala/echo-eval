/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.timertask;

/**
 * 删除定时任务请求
 *
 * @since 2026-04-08
 */
public class DeleteScheduleTaskRequest {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 默认构造方法
     */
    public DeleteScheduleTaskRequest() {
    }

    /**
     * 带参数的构造方法
     *
     * @param taskId 任务ID
     */
    public DeleteScheduleTaskRequest(String taskId) {
        this.taskId = taskId;
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

    @Override
    public String toString() {
        return "DeleteScheduleTaskRequest{taskId='" + taskId + "'}";
    }
}
