/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.config;

import com.huawei.aisfsdkv2.component.httpclient.callback.HttpInterceptor;
import com.huawei.aisfsdkv2.component.httpclient.callback.LoggingInterceptor;

/**
 * 日志拦截器规范
 * <p><b>重要：创建的拦截器参数与原实现完全一致。</b>
 * @since 2026-05-25
 */
public class LoggingInterceptorSpec implements InterceptorSpec {
    private final LoggingInterceptor.LogLevel level;

    /**
     * 构造函数
     *
     * @param level 日志级别
     */
    public LoggingInterceptorSpec(LoggingInterceptor.LogLevel level) {
        this.level = level != null ? level : LoggingInterceptor.LogLevel.BASIC;
    }
    @Override
    public HttpInterceptor createInterceptor() {
        return new LoggingInterceptor(level);
    }
    @Override
    public String getTypeName() {
        return "LoggingInterceptor";
    }
    public LoggingInterceptor.LogLevel getLevel() {
        return level;
    }
}