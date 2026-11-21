/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.timertask;

/**
 * 任务调度信息
 *
 * @since 2026-04-07
 */
public class ScheduleInfo {
    /**
     * 调度类型
     */
    private ScheduleTypeEnum type;

    /**
     * 触发时间
     *
     * <p>格式要求：ISO 8601 格式，带时区偏移，如 "2026-05-10T00:00:00+08:00"</p>
     */
    private String triggerTime;

    /**
     * cron表达式
     */
    private String cron;

    /**
     * 调度描述
     */
    private String description;

    /**
     * 默认构造方法
     */
    public ScheduleInfo() {
    }

    /**
     * 获取调度类型
     *
     * @return 调度类型
     */
    public ScheduleTypeEnum getType() {
        return type;
    }

    /**
     * 设置调度类型
     *
     * @param type 调度类型
     */
    public void setType(ScheduleTypeEnum type) {
        this.type = type;
    }

    /**
     * 获取触发时间
     *
     * @return 触发时间，格式如 "2026-05-10T00:00:00+08:00"
     */
    public String getTriggerTime() {
        return triggerTime;
    }

    /**
     * 设置触发时间
     *
     * <p>格式要求：ISO 8601 格式，带时区偏移，如 "2026-05-10T00:00:00+08:00"</p>
     *
     * @param triggerTime 触发时间
     */
    public void setTriggerTime(String triggerTime) {
        this.triggerTime = triggerTime;
    }

    /**
     * 获取cron表达式
     *
     * @return cron表达式
     */
    public String getCron() {
        return cron;
    }

    /**
     * 设置cron表达式
     *
     * @param cron cron表达式
     */
    public void setCron(String cron) {
        this.cron = cron;
    }

    /**
     * 获取调度描述
     *
     * @return 调度描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置调度描述
     *
     * @param description 调度描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "ScheduleInfo{type='" + type + "', triggerTime='" + triggerTime
                + "', cron='" + cron + "', description='" + description + "'}";
    }
}
