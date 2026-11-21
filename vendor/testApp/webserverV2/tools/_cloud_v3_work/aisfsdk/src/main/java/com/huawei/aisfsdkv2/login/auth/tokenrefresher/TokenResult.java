/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.login.auth.tokenrefresher;

import java.util.HashMap;
import java.util.Map;

/**
 * Token获取结果
 * 封装Token获取操作的结果，包括成功时的Token数据和失败时的错误信息
 */
public class TokenResult {

    /**
     * 是否获取成功
     * true 表示获取Token成功，false 表示获取失败
     */
    private final boolean success;

    /**
     * ACM Token字符串
     * 获取成功时返回的有效Token，用于后续的WebSocket连接和HTTP接口认证
     * 获取失败时为null
     */
    private final String token;

    /**
     * Token过期时间（秒）
     * 从获取时刻开始计算的过期时间，用于定时刷新监控
     * 获取失败时为0
     */
    private final int expireTime;

    /**
     * 扩展数据Map
     * 包含服务端返回的原始Data字段中的所有键值对
     * 用于透传给用户端
     */
    private final Map<String, String> extraData;

    /**
     * 错误信息
     * 获取失败时的错误描述，用于日志记录和故障排查
     * 获取成功时为null
     */
    private final String message;

    /**
     * 构造函数（不含extraData）
     *
     * @param success    是否成功
     * @param token      ACM Token
     * @param expireTime 过期时间（秒）
     * @param message    错误信息
     */
    public TokenResult(boolean success, String token, int expireTime, String message) {
        this(success, token, expireTime, new HashMap<>(), message);
    }

    /**
     * 构造函数（含extraData）
     *
     * @param success    是否成功
     * @param token      ACM Token
     * @param expireTime 过期时间（秒）
     * @param extraData  扩展数据Map
     * @param message    错误信息
     */
    public TokenResult(boolean success, String token, int expireTime, Map<String, String> extraData, String message) {
        this.success = success;
        this.token = token;
        this.expireTime = expireTime;
        this.extraData = extraData != null ? extraData : new HashMap<>();
        this.message = message;
    }

    /**
     * 创建成功的Token结果（不含extraData）
     *
     * @param token      ACM Token
     * @param expireTime 过期时间（秒）
     * @return TokenResult 成功结果
     */
    public static TokenResult success(String token, int expireTime) {
        return new TokenResult(true, token, expireTime, new HashMap<>(), null);
    }

    /**
     * 创建成功的Token结果（含extraData）
     *
     * @param token      ACM Token
     * @param expireTime 过期时间（秒）
     * @param extraData  扩展数据Map
     * @return TokenResult 成功结果
     */
    public static TokenResult success(String token, int expireTime, Map<String, String> extraData) {
        return new TokenResult(true, token, expireTime, extraData, null);
    }

    /**
     * 创建失败的Token结果
     *
     * @param message 错误信息
     * @return TokenResult 失败结果
     */
    public static TokenResult failure(String message) {
        return new TokenResult(false, null, 0, new HashMap<>(), message);
    }

    /**
     * 判断是否获取成功
     *
     * @return true 成功，false 失败
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取Token
     *
     * @return ACM Token，失败时返回null
     */
    public String getToken() {
        return token;
    }

    /**
     * 获取过期时间
     *
     * @return 过期时间（秒），失败时返回0
     */
    public int getExpireTime() {
        return expireTime;
    }

    /**
     * 获取扩展数据
     *
     * @return 扩展数据Map，失败时返回空Map
     */
    public Map<String, String> getExtraData() {
        return extraData;
    }

    /**
     * 获取错误信息
     *
     * @return 错误信息，成功时返回null
     */
    public String getMessage() {
        return message;
    }

    /**
     * 返回对象的字符串表示
     *
     * <p>包含所有字段信息，用于日志打印和调试。</p>
     *
     * @return 对象的字符串表示
     */
    @Override
    public String toString() {
        return "TokenResult{" +
                "success=" + success +
                ", token='" + token + '\'' +
                ", expireTime=" + expireTime +
                ", extraData=" + extraData +
                ", message='" + message + '\'' +
                '}';
    }
}
