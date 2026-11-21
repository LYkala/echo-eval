/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.impl;

import com.huawei.aisfsdkv2.component.httpclient.config.HttpClientConfig;
import com.huawei.aisfsdkv2.component.httpclient.config.WebSocketConfig;
import com.huawei.aisfsdkv2.component.httpclient.http.HttpClient;
import com.huawei.aisfsdkv2.component.httpclient.websocket.WebSocketClient;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * HTTP/WebSocket 客户端工厂（统一懒加载管理）
 * <ul>
 *   <li>根据配置创建客户端实例</li>
 *   <li>统一懒加载管理</li>
 *   <li>客户端实例缓存</li>
 * </ul>
 * @since 2026-05-25
 */
final class ClientFactory {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("ClientFactory");
    
    // 客户端缓存（key: config hash, value: client instance）
    private final ConcurrentMap<String, HttpClient> httpClientCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WebSocketClient> wsClientCache = new ConcurrentHashMap<>();

    private static final class Holder {
        static final ClientFactory INSTANCE = new ClientFactory();
    }
    
    /**
     * 获取工厂实例（单例）
     *
     * @return ClientFactory 实例
     */
    static ClientFactory getInstance() {
        return Holder.INSTANCE;
    }
    
    /**
     * 私有构造函数
     */
    private ClientFactory() {}
    
    /**
     * 创建或获取 HTTP 客户端（懒加载）
     * 
     * <p><b>双重检查锁机制（与原实现完全一致）：</b>
     * @param config HTTP 客户端配置
     * @return HTTP 客户端实例（单例保证）
     */
    HttpClient createHttpClient(HttpClientConfig config) {
        String cacheKey = generateCacheKey(config);
        HttpClient client = httpClientCache.get(cacheKey);
        if (client == null) {
            synchronized (this) {
                client = httpClientCache.get(cacheKey);
                if (client == null) {
                    LOGGER.info("Creating HTTP client with config: {}", cacheKey);
                    client = doCreateHttpClient(config);
                    httpClientCache.put(cacheKey, client);
                }
            }
        }
        return client;
    }
    
    /**
     * 创建或获取 WebSocket 客户端（懒加载）
     * 
     * <p><b>双重检查锁机制</b>
     *
     * @param config WebSocket 客户端配置
     * @return WebSocket 客户端实例（单例保证）
     */
    WebSocketClient createWebSocketClient(WebSocketConfig config) {
        String cacheKey = generateCacheKey(config);
        WebSocketClient client = wsClientCache.get(cacheKey);
        if (client == null) {
            synchronized (this) {
                client = wsClientCache.get(cacheKey);
                if (client == null) {
                    LOGGER.info("Creating WebSocket client with config: {}", cacheKey);
                    client = doCreateWebSocketClient(config);
                    wsClientCache.put(cacheKey, client);
                }
            }
        }
        return client;
    }
    
    /**
     * 实际创建 HTTP 客户端
     * @param config HTTP 客户端配置
     * @return HTTP 客户端实例
     */
    private HttpClient doCreateHttpClient(HttpClientConfig config) {
        HttpClientImpl client = new HttpClientImpl(
            config.getTimeoutConfig().getConnectTimeoutMs(),
            config.getTimeoutConfig().getReadTimeoutMs(),
            config.getTimeoutConfig().getWriteTimeoutMs(),
            config.getTimeoutConfig().getCallTimeoutMs()
        );
        config.getInterceptorConfig().createInterceptorChain()
            .forEach(client::addInterceptor);
        
        LOGGER.info("HTTP client created successfully: {}", config);
        return client;
    }
    
    /**
     * 实际创建 WebSocket 客户端
     * @param config WebSocket 配置（当前未使用参数）
     * @return WebSocket 客户端实例
     */
    private WebSocketClient doCreateWebSocketClient(WebSocketConfig config) {
        WebSocketClientImpl client = new WebSocketClientImpl();
        
        LOGGER.info("WebSocket client created successfully: {}", config);
        return client;
    }
    
    /**
     * 生成缓存 key（基于配置 hash）
     * 
     * <p>相同配置返回相同 key，确保单例。
     *
     * @param config HTTP 客户端配置
     * @return 缓存 key
     */
    private String generateCacheKey(HttpClientConfig config) {
        return "http-" + config.hashCode();
    }
    
    /**
     * 生成缓存 key（基于配置 hash）
     *
     * @param config WebSocket 配置
     * @return 缓存 key
     */
    private String generateCacheKey(WebSocketConfig config) {
        return "ws-" + config.hashCode();
    }
    
    /**
     * 清理缓存（用于测试或动态配置更新）
     * 
     * <p>注意：清理后重新创建客户端，会创建新实例。
     */
    void clearCache() {
        LOGGER.warn("Clearing client cache");
        httpClientCache.clear();
        wsClientCache.clear();
    }
}