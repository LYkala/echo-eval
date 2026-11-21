/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.config;

import com.huawei.aisfsdkv2.component.httpclient.HttpClientManager;

/**
 * WebSocket 客户端配置
 *
 * @since 2026-05-25
 */
public class WebSocketConfig {
    
    private final long pingIntervalMs;
    
    /**
     * 私有构造函数
     */
    private WebSocketConfig(long pingIntervalMs) {
        this.pingIntervalMs = pingIntervalMs;
    }
    
    /**
     * WebSocket 默认配置
     * 
     * <p>心跳间隔引用 {@link HttpClientManager#WEBSOCKET_PING_INTERVAL_MS}
     *
     * @return WebSocket 默认配置实例
     */
    public static WebSocketConfig defaultConfig() {
        return new WebSocketConfig(HttpClientManager.WEBSOCKET_PING_INTERVAL_MS);
    }
    
    /**
     * 自定义 WebSocket 配置
     *
     * @param pingIntervalMs 心跳间隔（毫秒）
     * @return WebSocket 配置实例
     */
    public static WebSocketConfig custom(long pingIntervalMs) {
        return new WebSocketConfig(pingIntervalMs);
    }
    
    /**
     * 获取心跳间隔
     *
     * @return 心跳间隔（毫秒）
     */
    public long getPingIntervalMs() {
        return pingIntervalMs;
    }
    
    @Override
    public String toString() {
        return "WebSocketConfig{pingInterval=" + pingIntervalMs + "ms}";
    }
}