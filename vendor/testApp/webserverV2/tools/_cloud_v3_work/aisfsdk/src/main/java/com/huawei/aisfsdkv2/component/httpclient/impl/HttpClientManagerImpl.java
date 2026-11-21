/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.impl;

import com.huawei.aisfsdkv2.component.httpclient.HttpClientManager;
import com.huawei.aisfsdkv2.component.httpclient.http.HttpClient;
import com.huawei.aisfsdkv2.component.httpclient.websocket.WebSocketClient;
import com.huawei.aisfsdkv2.component.httpclient.config.HttpClientConfig;
import com.huawei.aisfsdkv2.component.httpclient.config.WebSocketConfig;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

/**
 * HTTP客户端管理器实现
 * @since 2026-05-25
 */
public final class HttpClientManagerImpl implements HttpClientManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("HttpClientManagerImpl");

    private static final class Holder {
        static final HttpClientManagerImpl INSTANCE = new HttpClientManagerImpl();
    }

    public static HttpClientManagerImpl getInstance() {
        return Holder.INSTANCE;
    }

    private volatile HttpClientConfig commonConfig;
    private volatile HttpClientConfig fileConfig;
    private volatile WebSocketConfig wsConfig;

    private final ClientFactory clientFactory;

    private HttpClientManagerImpl() {
        this.clientFactory = ClientFactory.getInstance();
    }

    @Override
    public HttpClient getCommonHttpClient() {
        if (commonConfig == null) {
            synchronized (this) {
                if (commonConfig == null) {
                    LOGGER.info("Initializing common HTTP client config");
                    commonConfig = HttpClientConfig.defaultCommon();
                }
            }
        }
        return clientFactory.createHttpClient(commonConfig);
    }

    @Override
    public HttpClient getFileHttpClient() {
        if (fileConfig == null) {
            synchronized (this) {
                if (fileConfig == null) {
                    LOGGER.info("Initializing file HTTP client config");
                    fileConfig = HttpClientConfig.defaultFile();
                }
            }
        }
        return clientFactory.createHttpClient(fileConfig);
    }

    @Override
    public WebSocketClient getWebSocketClient() {
        if (wsConfig == null) {
            synchronized (this) {
                if (wsConfig == null) {
                    LOGGER.info("Initializing WebSocket client config");
                    wsConfig = WebSocketConfig.defaultConfig();
                }
            }
        }
        return clientFactory.createWebSocketClient(wsConfig);
    }
    
    /**
     * 扩展方法：使用自定义配置创建 HTTP 客户端
     * 
     * <p>用于高级场景（如动态调整超时）。
     * <p>注意：不同配置会创建不同实例（通过工厂缓存管理）。
     *
     * @param config HTTP 客户端配置
     * @return HTTP 客户端实例
     */
    public HttpClient createCustomHttpClient(HttpClientConfig config) {
        LOGGER.info("Creating custom HTTP client with config: {}", config);
        return clientFactory.createHttpClient(config);
    }
}