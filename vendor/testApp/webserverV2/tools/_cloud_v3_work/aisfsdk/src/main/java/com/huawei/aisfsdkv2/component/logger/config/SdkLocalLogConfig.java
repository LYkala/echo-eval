/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logger.config;

import com.huawei.aisfsdkv2.api.log.LogLevelEnum;

/**
 * SDK本地日志配置
 *
 * @author l00504561
 * @since 2026-02-07
 */
public class SdkLocalLogConfig {
    /**
     * 日志级别
     */
    private LogLevelEnum logLevel = LogLevelEnum.INFO;

    /**
     * 最大日志文件大小（字节）默认10MB
     */
    private long maxLogFileSize = 10 * 1024 * 1024;

    /**
     * 最大日志文件数量
     */
    private int maxLogFileCount = 5;

    /**
     * 获取日志级别
     *
     * @return 日志级别枚举
     */
    public LogLevelEnum getLogLevel() {
        return logLevel;
    }

    /**
     * 设置日志级别
     *
     * @param logLevel 日志级别
     */
    public void setLogLevel(LogLevelEnum logLevel) {
        this.logLevel = logLevel;
    }

    /**
     * 获取最大日志文件大小
     *
     * @return 最大文件大小（字节）
     */
    public long getMaxLogFileSize() {
        return maxLogFileSize;
    }

    /**
     * 设置最大日志文件大小
     *
     * @param maxLogFileSize 最大文件大小（字节）
     */
    public void setMaxLogFileSize(long maxLogFileSize) {
        this.maxLogFileSize = maxLogFileSize;
    }

    /**
     * 获取最大日志文件数量
     *
     * @return 最大文件数量
     */
    public int getMaxLogFileCount() {
        return maxLogFileCount;
    }

    /**
     * 设置最大日志文件数量
     *
     * @param maxLogFileCount 最大文件数量
     */
    public void setMaxLogFileCount(int maxLogFileCount) {
        this.maxLogFileCount = maxLogFileCount;
    }
}
