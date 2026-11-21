/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.log;

/**
 * 日志级别枚举
 *
 * @author l00504561
 * @since 2026-02-06
 */
public enum LogLevelEnum {
    /**
     * DEBUG日志级别
     */
    DEBUG(1),
    /**
     * INFO日志级别
     */
    INFO(2),
    /**
     * WARN日志级别
     */
    WARN(3),
    /**
     * ERROR日志级别
     */
    ERROR(4);

    /**
     * 日志级别
     */
    private final int logLevel;

    LogLevelEnum(int logLevel) {
        this.logLevel = logLevel;
    }

    /**
     * 获取日志级别
     *
     * @return 日志级别数值
     */
    public int getLogLevel() {
        return logLevel;
    }

    /**
     * 比较日志级别
     *
     * @param logLevelEnum 日志级别枚举
     * @return a - b
     */
    public int compare(LogLevelEnum logLevelEnum) {
        return this.logLevel - logLevelEnum.getLogLevel();
    }
}
