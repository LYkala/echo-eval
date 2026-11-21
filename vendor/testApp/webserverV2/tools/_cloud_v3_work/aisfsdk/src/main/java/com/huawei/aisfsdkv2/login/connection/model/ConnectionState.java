/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.login.connection.model;

import com.huawei.aisfsdkv2.component.httpclient.websocket.WebSocketClient;

/**
 * 连接状态快照（不可变）
 * 封装 WebSocketClient 实例和连接状态，确保两者始终一致
 *
 * @author l00504561
 * @since 2026-04-19
 */
public class ConnectionState {

    /**
     * 连接状态枚举
     */
    public enum State {
        DISCONNECTED, CONNECTING, CONNECTED
    }

    /**
     * 断开状态（单例复用）
     */
    public static final ConnectionState DISCONNECTED = new ConnectionState(null, State.DISCONNECTED);

    public final WebSocketClient webSocketClient;
    public final State state;

    private ConnectionState(WebSocketClient webSocketClient, State state) {
        this.webSocketClient = webSocketClient;
        this.state = state;
    }

    /**
     * 创建连接中状态
     *
     * @param webSocketClient WebSocketClient 实例
     * @return 连接中状态
     */
    public static ConnectionState connecting(WebSocketClient webSocketClient) {
        return new ConnectionState(webSocketClient, State.CONNECTING);
    }

    /**
     * 创建已连接状态
     *
     * @param webSocketClient WebSocketClient 实例
     * @return 已连接状态
     */
    public static ConnectionState connected(WebSocketClient webSocketClient) {
        return new ConnectionState(webSocketClient, State.CONNECTED);
    }

    /**
     * 检查是否已连接
     *
     * @return true 已连接
     */
    public boolean isConnected() {
        return state == State.CONNECTED;
    }

    /**
     * 检查是否连接中
     *
     * @return true 连接中
     */
    public boolean isConnecting() {
        return state == State.CONNECTING;
    }

    /**
     * 检查是否可以发送消息
     *
     * @return true 可以发送
     */
    public boolean canSend() {
        return state == State.CONNECTED && webSocketClient != null;
    }

    /**
     * 获取 WebSocketClient（发送前需检查 canSend）
     *
     * @return WebSocketClient 实例
     */
    public WebSocketClient getWebSocketClientForSend() {
        return webSocketClient;
    }

    @Override
    public String toString() {
        return "ConnectionState{" + state + ", wsClient=" + (webSocketClient != null) + "}";
    }
}
