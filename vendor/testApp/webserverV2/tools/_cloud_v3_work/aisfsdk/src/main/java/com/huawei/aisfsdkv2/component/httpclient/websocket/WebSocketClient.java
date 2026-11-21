/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.websocket;

import com.huawei.aisfsdkv2.component.httpclient.callback.WebSocketCallback;

import java.util.Map;

/**
 * WebSocket客户端接口
 *
 * @since 2026-05-25
 */
public interface WebSocketClient {

    /**
     * 连接WebSocket服务端
     */
    boolean connect(String url, Map<String, String> headers, WebSocketCallback callback);

    /**
     * 发送文本消息
     */
    boolean sendText(String message);

    /**
     * 发送二进制消息
     */
    boolean sendBinary(byte[] data);

    /**
     * 断开连接
     */
    void disconnect();

    /**
     * 是否已连接
     */
    boolean isConnected();

    /**
     * 关闭客户端释放资源
     */
    void shutdown();
}