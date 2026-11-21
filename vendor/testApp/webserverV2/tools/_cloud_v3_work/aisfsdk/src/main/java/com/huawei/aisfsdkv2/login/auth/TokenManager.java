/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.login.auth;

import com.huawei.aisfsdkv2.login.auth.impl.TokenManagerImpl;
import com.huawei.aisfsdkv2.login.auth.tokenrefresher.TokenResult;

/**
 * Token管理器接口
 * 负责Token的获取、存储、刷新和过期监控
 */
public interface TokenManager {

    /**
     * 获取 TokenManager 实例（单例）
     *
     * @return TokenManager 实例
     */
    static TokenManager getInstance() {
        return TokenManagerImpl.getInstance();
    }

    /**
     * 同步获取Token（带重试机制）
     * <p>注意：此方法是同步阻塞的，内部有重试机制（最多60次，每次3秒），
     * 最长可能阻塞3分钟，必须在后台线程调用，避免ANR。
     *
     * @param identifyId 设备标识ID
     * @param username   用户名
     * @param password   密码
     * @return ACM Token，获取失败返回空字符串
     */
    String fetchToken(String identifyId, String username, String password);

    /**
     * 异步刷新Token
     * <p>在后台线程执行Token刷新操作，刷新成功后会回调监听器
     */
    void refreshToken();

    /**
     * 获取当前Token
     *
     * @return 当前有效的Token，不存在返回null
     */
    String getCurrentToken();

    /**
     * 获取当前Token结果（包含extraData）
     *
     * <p>返回完整的 TokenResult 对象，包括 token、expireTime、extraData，
     * 用于在登录成功回调中透传给用户端。</p>
     *
     * @return TokenResult 对象；未登录或无Token时返回失败的TokenResult
     */
    TokenResult getTokenResult();

    /**
     * 清除Token数据（登出时调用）
     * <p>清除当前保存的Token、过期时间等数据
     */
    void clearToken();
}
