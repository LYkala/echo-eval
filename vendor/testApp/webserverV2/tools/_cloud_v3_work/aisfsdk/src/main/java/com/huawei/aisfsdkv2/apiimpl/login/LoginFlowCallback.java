/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.login;

import com.huawei.aisfsdkv2.api.login.LoginErrorEnum;

/**
 * 登录流程回调接口
 * 用于协调登录流程中各阶段的回调通知
 */
interface LoginFlowCallback {

    /**
     * Token获取成功
     *
     * @param identifyId 设备标识ID
     * @param token      Token
     */
    void onTokenAcquired(String identifyId, String token);

    /**
     * Token获取失败
     */
    void onTokenFailed(LoginErrorEnum error, String message);

    /**
     * 连接成功
     */
    void onConnected();

    /**
     * 连接断开
     */
    void onDisconnected(int code, String reason);

    /**
     * 认证失败
     */
    void onAuthFailed();
}
