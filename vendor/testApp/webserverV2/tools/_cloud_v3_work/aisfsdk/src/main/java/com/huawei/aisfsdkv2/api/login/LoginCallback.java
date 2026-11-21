/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.login;

import com.huawei.aisfsdkv2.login.auth.tokenrefresher.TokenResult;

/**
 * 登录回调接口
 */
public interface LoginCallback {

    /**
     * 登录成功（原有方法，保持不变）
     *
     * <p>此方法是抽象方法，所有实现类必须实现。</p>
     */
    void onSuccess();

    /**
     * 登录成功（带TokenResult，扩展方法）
     *
     * <p>此方法是默认方法，实现类可选择性覆盖以获取完整的Token结果信息。</p>
     * <p>默认实现调用无参数版本的 onSuccess()，保持向后兼容。</p>
     *
     * @param tokenResult Token获取结果，包含token、expireTime、extraData等字段
     */
    default void onSuccess(TokenResult tokenResult) {
        onSuccess();  // 默认调用原有方法，保持向后兼容
    }

    /**
     * 登录失败
     *
     * @param error   错误码枚举
     * @param message 错误信息（可包含具体原因，如枚举默认值或自定义详情）
     */
    void onFailure(LoginErrorEnum error, String message);

    /**
     * 连接状态变化
     *
     * @param oldStatus 变更前的状态
     * @param newStatus 变更后的状态
     */
    void onConnectStatusChanged(LoginStatus oldStatus, LoginStatus newStatus);
}
