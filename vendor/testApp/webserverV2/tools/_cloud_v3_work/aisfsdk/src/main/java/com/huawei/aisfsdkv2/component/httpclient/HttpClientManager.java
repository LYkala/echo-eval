/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient;

import com.huawei.aisfsdkv2.component.httpclient.impl.HttpClientManagerImpl;
import com.huawei.aisfsdkv2.component.httpclient.http.HttpClient;
import com.huawei.aisfsdkv2.component.httpclient.websocket.WebSocketClient;

/**
 * HTTP客户端管理器接口（对外统一入口）
 * 
 * <p>提供HTTP和WebSocket客户端
 * <p>接口风格参考 {@link com.huawei.aisfsdkv2.component.httpclient.HttpClientManager}
 *
 * @since 2026-05-25
 */
public interface HttpClientManager {

    /** 心跳间隔：30秒 */
    /** 低于云侧约 60s 空闲断链；20s 留足边界余量。 */
    long WEBSOCKET_PING_INTERVAL_MS = 20000;

    /**
     * 获取管理器实例（单例）
     */
    static HttpClientManager getInstance() {
        return HttpClientManagerImpl.getInstance();
    }

    /**
     * 获取通用HTTP客户端
     * <p>适用于普通API请求，超时时间较短
     */
    HttpClient getCommonHttpClient();

    /**
     * 获取文件HTTP客户端
     * <p>适用于文件上传/下载，超时时间较长
     */
    HttpClient getFileHttpClient();

    /**
     * 获取WebSocket客户端
     * <p>适用于WebSocket长连接，配置心跳间隔
     */
    WebSocketClient getWebSocketClient();
}