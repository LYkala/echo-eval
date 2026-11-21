/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient;

/**
 * HTTP客户端异常
 *
 * <p>用于封装HTTP请求过程中发生的异常，包括连接失败、超时、重试失败等场景。
 *
 * @since 2026-05-25
 */
public class HttpClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造函数
     *
     * @param message 异常信息
     */
    public HttpClientException(String message) {
        super(message);
    }

    /**
     * 构造函数
     *
     * @param message 异常信息
     * @param cause 原始异常
     */
    public HttpClientException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造函数
     *
     * @param cause 原始异常
     */
    public HttpClientException(Throwable cause) {
        super(cause);
    }
}