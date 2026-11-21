/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

/**
 * 任务信息类
 *
 * <p>封装任务完成状态和相关信息。</p>
 */
public class TaskInfo {

    /** 任务完成状态，true 表示已完成 */
    private boolean taskCompleted;

    /** 任务状态码，200 表示正常，非 200 表示问题 */
    private int taskStatusCode;

    /** 任务ID，从 Header.TaskID 获取 */
    private String taskId;

    /** GUI任务是否正在运行，true=运行中，false=未运行 */
    private boolean guiTaskRunning;

    /**
     * 获取任务完成状态
     *
     * @return true 表示任务已完成
     */
    public boolean isTaskCompleted() {
        return taskCompleted;
    }

    /**
     * 设置任务完成状态
     *
     * @param taskCompleted 任务完成状态
     */
    public void setTaskCompleted(boolean taskCompleted) {
        this.taskCompleted = taskCompleted;
    }

    /**
     * 获取任务状态码
     *
     * @return 任务状态码，200 表示正常
     */
    public int getTaskStatusCode() {
        return taskStatusCode;
    }

    /**
     * 设置任务状态码
     *
     * @param taskStatusCode 任务状态码
     */
    public void setTaskStatusCode(int taskStatusCode) {
        this.taskStatusCode = taskStatusCode;
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
     * 获取GUI任务是否正在运行
     *
     * @return true 表示GUI任务正在运行，false 表示未运行
     */
    public boolean isGuiTaskRunning() {
        return guiTaskRunning;
    }

    /**
     * 设置GUI任务是否正在运行
     *
     * @param guiTaskRunning GUI任务运行状态，true=运行中，false=未运行
     */
    public void setGuiTaskRunning(boolean guiTaskRunning) {
        this.guiTaskRunning = guiTaskRunning;
    }

    /**
     * 返回对象的JSON格式字符串表示
     *
     * @return JSON格式的字符串
     */
    @Override
    public String toString() {
        return "{\"taskCompleted\":" + taskCompleted + ",\"taskStatusCode\":" + taskStatusCode + ",\"taskId\":\"" + (taskId != null ? taskId : "") + "\",\"guiTaskRunning\":" + guiTaskRunning + "}";
    }
}
