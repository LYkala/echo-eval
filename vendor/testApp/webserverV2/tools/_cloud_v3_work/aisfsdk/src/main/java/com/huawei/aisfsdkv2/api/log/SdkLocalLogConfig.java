/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.log;

/**
 * 本地日志配置
 *
 * <p>配置SDK本地日志的行为，包括日志级别、文件大小和数量限制</p>
 *
 * @author l00504561
 * @since 2026-02-06
 */
public class SdkLocalLogConfig {
    /**
     * 日志级别，默认INFO
     * 低于此级别的日志不会打印
     */
    private LogLevelEnum logLevel = LogLevelEnum.INFO;

    /**
     * 单文件最大大小，默认10MB
     */
    private int maxLogFileSize = 10 * 1024 * 1024;

    /**
     * 日志文件最大数量，默认10个
     */
    private int maxLogFileCount = 10;

    /**
     * 默认构造方法
     */
    public SdkLocalLogConfig() {
    }

    /**
     * 获取日志级别
     *
     * @return 日志级别
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
     * 获取单文件最大大小
     *
     * @return 单文件最大大小（字节）
     */
    public int getMaxLogFileSize() {
        return maxLogFileSize;
    }

    /**
     * 设置单文件最大大小
     *
     * @param maxLogFileSize 单文件最大大小（字节）
     */
    public void setMaxLogFileSize(int maxLogFileSize) {
        this.maxLogFileSize = maxLogFileSize;
    }

    /**
     * 获取日志文件最大数量
     *
     * @return 日志文件最大数量
     */
    public int getMaxLogFileCount() {
        return maxLogFileCount;
    }

    /**
     * 设置日志文件最大数量
     *
     * @param maxLogFileCount 日志文件最大数量
     */
    public void setMaxLogFileCount(int maxLogFileCount) {
        this.maxLogFileCount = maxLogFileCount;
    }

    @Override
    public String toString() {
        return "SdkLocalLogConfig{logLevel=" + logLevel
                + ", maxLogFileSize=" + maxLogFileSize
                + ", maxLogFileCount=" + maxLogFileCount + "}";
    }
}
