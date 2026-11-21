/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.constants;

/**
 * HTTP 状态码常量
 *
 * <p>定义常用的 HTTP 响应状态码，主要用于 WebSocket 连接和 HTTP 请求处理。</p>
 */
public class HttpStatusCode {

    /**
     * 私有构造方法，防止实例化
     */
    private HttpStatusCode() {
    }

    /** 请求成功 */
    public static final int OK = 200;

    /** WebSocket 握手成功 */
    public static final int SWITCHING_PROTOCOLS = 101;

    /** 未授权（Token 无效或过期） */
    public static final int UNAUTHORIZED = 401;

    /** 代理认证失败 */
    public static final int PROXY_AUTH_REQUIRED = 407;
}
