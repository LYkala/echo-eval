/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.callback;

import com.huawei.aisfsdkv2.component.httpclient.model.NettyHttpRequest;
import com.huawei.aisfsdkv2.component.httpclient.model.NettyHttpResponse;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

/**
 * 日志记录拦截器
 *
 * <p>参考OkHttp日志拦截器，记录请求和响应详情。
 *
 * @since 2026-05-25
 */
public final class LoggingInterceptor implements HttpInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger("LoggingInterceptor");
    private final LogLevel level;

    public LoggingInterceptor() {
        this(LogLevel.BASIC);
    }

    public LoggingInterceptor(LogLevel level) {
        this.level = level != null ? level : LogLevel.BASIC;
    }

    /**
     * 获取日志级别
     *
     * @return 日志级别
     */
    public LogLevel getLevel() {
        return level;
    }

    @Override
    public NettyHttpResponse intercept(Chain chain) throws Exception {
        NettyHttpRequest request = chain.request();
        long startTime = System.currentTimeMillis();
        logRequest(request);
        try {
            NettyHttpResponse response = chain.proceed(request);
            long elapsed = System.currentTimeMillis() - startTime;
            logResponse(response, elapsed);
            return response;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            LOGGER.error("LoggingInterceptor: request failed, url={}, elapsed={}ms, error={}",
                request.getUrl(), elapsed, e.getMessage());
            throw e;
        }
    }

    private void logRequest(NettyHttpRequest request) {
        if (level == LogLevel.NONE) {
            return;
        }
        LOGGER.info("LoggingInterceptor: --> {} {}", request.getMethod(), request.getUrl());
        if (level == LogLevel.HEADERS || level == LogLevel.BODY) {
            request.getHeaders().forEach((k, v) -> 
                LOGGER.info("LoggingInterceptor: Header: {}={}", k, v));
        }
        if (level == LogLevel.BODY && request.getBody() != null) {
            LOGGER.info("LoggingInterceptor: Body: {} bytes", request.getBody().length);
        }
    }

    private void logResponse(NettyHttpResponse response, long elapsed) {
        if (level == LogLevel.NONE) {
            return;
        }
        LOGGER.info("LoggingInterceptor: <-- {} {} ({}ms)", 
            response.getStatusCode(), response.getStatusText(), elapsed);
        if (level == LogLevel.HEADERS || level == LogLevel.BODY) {
            if (response.getHeaders() != null && !response.getHeaders().isEmpty()) {
                response.getHeaders().forEach((k, v) ->
                    LOGGER.info("LoggingInterceptor: Header: {}={}", k, v));
            }
        }
        if (level == LogLevel.BODY) {
            LOGGER.info("LoggingInterceptor: Body: {} bytes", response.getContentLength());
        }
    }

    public enum LogLevel {
        NONE,
        BASIC,
        HEADERS,
        BODY
    }
}