/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.timertask;

/**
 * 任务调度动作
 *
 * @since 2026-04-07
 */
public class ScheduleAction {
    /**
     * 动作类型
     */
    private String type;

    /**
     * 动作标题
     */
    private String title;

    /**
     * 动作内容
     */
    private String body;

    /**
     * 默认构造方法
     */
    public ScheduleAction() {
    }

    /**
     * 获取动作类型
     *
     * @return 动作类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置动作类型
     *
     * @param type 动作类型
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取动作标题
     *
     * @return 动作标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置动作标题
     *
     * @param title 动作标题
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 获取动作内容
     *
     * @return 动作内容
     */
    public String getBody() {
        return body;
    }

    /**
     * 设置动作内容
     *
     * @param body 动作内容
     */
    public void setBody(String body) {
        this.body = body;
    }

    @Override
    public String toString() {
        return "ScheduleAction{type='" + type + "', title='" + title + "', body='" + body + "'}";
    }
}
