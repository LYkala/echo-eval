/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * HTTP 客户端配置包
 * 
 * <p>提供配置抽象层，将硬编码的常量和拦截器配置抽象为独立的配置类。
 * 
 * <p>核心类：
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.httpclient.config.TimeoutConfig} - 超时配置</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.httpclient.config.InterceptorSpec} - 拦截器规范接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.httpclient.config.LoggingInterceptorSpec} - 日志拦截器规范</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.httpclient.config.RetryInterceptorSpec} - 重试拦截器规范</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.httpclient.config.InterceptorChainConfig} - 拦截器链配置</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.httpclient.config.HttpClientConfig} - HTTP 客户端完整配置</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.httpclient.config.WebSocketConfig} - WebSocket 配置</li>
 * </ul>
 *
 * @since 2026-05-25
 */
package com.huawei.aisfsdkv2.component.httpclient.config;