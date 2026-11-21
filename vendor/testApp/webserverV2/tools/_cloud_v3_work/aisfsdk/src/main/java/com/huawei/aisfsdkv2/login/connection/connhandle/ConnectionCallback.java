/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.login.connection.connhandle;

/**
 * 连接回调接口
 */
public interface ConnectionCallback {

    /**
     * 连接成功
     */
    void onConnected();

    /**
     * 连接断开
     *
     * @param code   错误码
     * @param reason 原因
     */
    void onDisconnected(int code, String reason);

    /**
     * 认证失败
     */
    void onAuthFailed();
}
