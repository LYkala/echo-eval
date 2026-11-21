/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.timertask;

/**
 * 调度类型枚举
 *
 * <p>用于指定定时任务的调度类型。</p>
 *
 * @since 2026-04-08
 */
public enum ScheduleTypeEnum {

    /**
     * 一次性任务
     */
    ONCE("once"),

    /**
     * 周期性任务
     */
    RECURRING("recurring");

    private final String value;

    ScheduleTypeEnum(String value) {
        this.value = value;
    }

    /**
     * 获取对应的字符串值（用于服务端接口传参）
     *
     * @return 枚举对应的字符串值
     */
    public String getValue() {
        return value;
    }

    /**
     * 将字符串转换为枚举值
     *
     * <p>支持按枚举名称（如 "ONCE"）或字符串值（如 "once"）进行匹配，忽略大小写。
     * 如果不匹配则返回 null。</p>
     *
     * @param value 字符串值，可为 null 或空
     * @return 对应的枚举值，如果不匹配则返回 null
     */
    public static ScheduleTypeEnum fromValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (ScheduleTypeEnum e : values()) {
            if (e.value.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        return null;
    }
}
