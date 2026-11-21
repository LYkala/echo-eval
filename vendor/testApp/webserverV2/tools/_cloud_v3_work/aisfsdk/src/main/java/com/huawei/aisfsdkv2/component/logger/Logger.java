/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2021. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logger;

/**
 * 日志接口
 *
 * @author l00504561
 */
public interface Logger {
    /**
     * debug日志级别打印
     *
     * @param format format使用{}进行占位符
     * @param arguments 参数列表
     */
    void debug(String format, Object... arguments);

    /**
     * debug日志级别打印
     *
     * @param msg 消息
     * @param t 异常
     */
    void debug(String msg, Throwable t);

    /**
     * info日志级别打印
     *
     * @param format format使用{}进行占位符
     * @param arguments 参数列表
     */
    void info(String format, Object... arguments);

    /**
     * info日志级别打印
     *
     * @param msg 消息
     * @param t 异常
     */
    void info(String msg, Throwable t);

    /**
     * warn日志级别打印
     *
     * @param format format使用{}进行占位符
     * @param arguments 参数列表
     */
    void warn(String format, Object... arguments);

    /**
     * warn日志级别打印
     *
     * @param msg 消息
     * @param t 异常
     */
    void warn(String msg, Throwable t);

    /**
     * error日志级别打印
     *
     * @param format format使用{}进行占位符
     * @param arguments 参数列表
     */
    void error(String format, Object... arguments);

    /**
     * error日志级别打印
     *
     * @param msg 消息
     * @param t 异常
     */
    void error(String msg, Throwable t);
}
