/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.acm;

/**
 * ACM 认证结果
 * 封装从 ACM 服务获取的认证令牌信息，包括 Token 和过期时间
 */
public class AuthResult {

    /**
     * ACM Token 字符串
     * 用于后续 WebSocket 连接和 HTTP 接口认证的身份凭证
     * 有效期由 expireTime 指定，过期后需要重新获取或刷新
     */
    private final String token;

    /**
     * Token 过期时间（秒）
     * 从获取时刻开始计算的有效期，用于定时刷新监控
     * 例如：7200 表示 Token 在 2 小时后过期
     */
    private final int expireTime;

    /**
     * 构造认证结果
     *
     * @param token      ACM Token 字符串
     * @param expireTime 过期时间（秒）
     */
    public AuthResult(String token, int expireTime) {
        this.token = token;
        this.expireTime = expireTime;
    }

    /**
     * 获取 ACM Token
     *
     * @return Token 字符串，用于后续请求的身份认证
     */
    public String getToken() {
        return token;
    }

    /**
     * 获取 Token 过期时间
     *
     * @return 过期时间（秒），从获取时刻开始计算
     */
    public int getExpireTime() {
        return expireTime;
    }
}
