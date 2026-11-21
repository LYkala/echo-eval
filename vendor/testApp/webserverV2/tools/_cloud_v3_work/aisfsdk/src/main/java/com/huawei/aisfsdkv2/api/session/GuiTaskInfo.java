/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

/**
 * GUI任务信息类
 *
 * <p>封装GUI任务的类型信息和关联文本。</p>
 */
public class GuiTaskInfo {
    /** GUI任务类型 */
    private GuiTaskEnum guiTaskType;

    /** 关联的用户输出文本，来自 UserOutputText 字段 */
    private String text;

    /**
     * 获取GUI任务类型
     *
     * @return GUI任务类型
     */
    public GuiTaskEnum getGuiTaskType() {
        return guiTaskType;
    }

    /**
     * 设置GUI任务类型
     *
     * @param guiTaskType GUI任务类型
     */
    public void setGuiTaskType(GuiTaskEnum guiTaskType) {
        this.guiTaskType = guiTaskType;
    }

    /**
     * 获取关联的用户输出文本
     *
     * @return 用户输出文本，可能为null
     */
    public String getText() {
        return text;
    }

    /**
     * 设置关联的用户输出文本
     *
     * @param text 用户输出文本
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * 返回对象的JSON格式字符串表示
     *
     * @return JSON格式的字符串
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"guiTaskType\":\"").append(guiTaskType != null ? guiTaskType.getDescription() : "null").append("\"");
        if (text != null && !text.isEmpty()) {
            sb.append(",\"text\":\"").append(text).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }
}
