/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

/**
 * 会话响应数据类
 *
 * <p>封装服务端返回的响应数据。</p>
 */
public class SessionResponseData {
    /** 用户输出类型，标识消息类型（文本或提醒） */
    private OutputTypeEnum outputType;

    /** 用户文本输出信息，当 outputType 为 TEXT 或 GUI_TASK 时使用，可能为 null */
    private TextInfo textInfo;

    /** 会话通知信息，当 outputType 为 NOTIFY 时使用，可能为 null */
    private NotifyInfo notifyInfo;

    /** 任务信息，当 outputType 为 TASK_STATUS 时使用，可能为 null */
    private TaskInfo taskInfo;

    /** GUI任务信息，当 outputType 为 GUI_TASK 时使用，可能为 null */
    private GuiTaskInfo guiTaskInfo;

    /** 弹窗信息，当 outputType 为 DIALOG 时使用，可能为 null */
    private DialogInfo dialogInfo;

    /**
     * 获取用户输出类型
     *
     * @return 用户输出类型
     */
    public OutputTypeEnum getOutputType() {
        return outputType;
    }

    /**
     * 设置用户输出类型
     *
     * @param outputType 用户输出类型
     */
    public void setOutputType(OutputTypeEnum outputType) {
        this.outputType = outputType;
    }

    /**
     * 获取用户文本输出信息
     *
     * @return 用户文本输出信息，可能为null
     */
    public TextInfo getTextInfo() {
        return textInfo;
    }

    /**
     * 设置用户文本输出信息
     *
     * @param textInfo 用户文本输出信息
     */
    public void setTextInfo(TextInfo textInfo) {
        this.textInfo = textInfo;
    }

    /**
     * 获取会话通知信息
     *
     * @return 会话通知信息，可能为null
     */
    public NotifyInfo getNotifyInfo() {
        return notifyInfo;
    }

    /**
     * 设置会话通知信息
     *
     * @param notifyInfo 会话通知信息
     */
    public void setNotifyInfo(NotifyInfo notifyInfo) {
        this.notifyInfo = notifyInfo;
    }

    /**
     * 获取任务信息
     *
     * @return 任务信息，可能为null
     */
    public TaskInfo getTaskInfo() {
        return taskInfo;
    }

    /**
     * 设置任务信息
     *
     * @param taskInfo 任务信息
     */
    public void setTaskInfo(TaskInfo taskInfo) {
        this.taskInfo = taskInfo;
    }

    /**
     * 获取GUI任务信息
     *
     * @return GUI任务信息，可能为null
     */
    public GuiTaskInfo getGuiTaskInfo() {
        return guiTaskInfo;
    }

    /**
     * 设置GUI任务信息
     *
     * @param guiTaskInfo GUI任务信息
     */
    public void setGuiTaskInfo(GuiTaskInfo guiTaskInfo) {
        this.guiTaskInfo = guiTaskInfo;
    }

    /**
     * 获取弹窗信息
     *
     * @return 弹窗信息，可能为null
     */
    public DialogInfo getDialogInfo() {
        return dialogInfo;
    }

    /**
     * 设置弹窗信息
     *
     * @param dialogInfo 弹窗信息
     */
    public void setDialogInfo(DialogInfo dialogInfo) {
        this.dialogInfo = dialogInfo;
    }

    /**
     * 返回对象的JSON格式字符串表示
     *
     * @return JSON格式的字符串
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"outputType\":\"").append(outputType != null ? outputType.getDescription() : "null").append("\"");
        if (textInfo != null) {
            sb.append(",\"textInfo\":").append(textInfo.toString());
        }
        if (notifyInfo != null) {
            sb.append(",\"notifyInfo\":").append(notifyInfo.toString());
        }
        if (taskInfo != null) {
            sb.append(",\"taskInfo\":").append(taskInfo.toString());
        }
        if (guiTaskInfo != null) {
            sb.append(",\"guiTaskInfo\":").append(guiTaskInfo.toString());
        }
        if (dialogInfo != null) {
            sb.append(",\"dialogInfo\":").append(dialogInfo.toString());
        }
        sb.append("}");
        return sb.toString();
    }
}
