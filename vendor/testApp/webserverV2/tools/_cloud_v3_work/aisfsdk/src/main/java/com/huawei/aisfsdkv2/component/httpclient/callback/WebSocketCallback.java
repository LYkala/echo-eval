/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.callback;

/**
 * WebSocket回调接口
 *
 * @since 2026-05-25
 */
public interface WebSocketCallback {

    /**
     * 连接成功回调
     */
    void onConnected();

    /**
     * 连接失败回调
     */
    void onConnectionFailed(Exception error);

    /**
     * 收到文本消息回调
     */
    void onTextMessage(String message);

    /**
     * 收到二进制消息回调
     */
    void onBinaryMessage(byte[] data);

    /**
     * 断开连接回调
     */
    void onDisconnected(int code, String reason);

    /**
     * 异常回调
     */
    void onError(Exception error);
}