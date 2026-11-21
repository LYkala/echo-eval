/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.login;

import com.huawei.aisfsdkv2.login.auth.tokenrefresher.TokenResult;

/**
 * 登录API接口
 * 定义登录模块对外提供的能力
 */
public interface LoginApi {

    /**
     * 用户登录（异步）
     *
     * <p>所有结果（成功/失败）统一通过回调异步返回：
     * - 成功：{@link LoginCallback#onSuccess()}
     * - 失败：{@link LoginCallback#onFailure(LoginErrorEnum, String)}
     *
     * <p>包括参数校验失败在内的所有错误，都会回调 onFailure，
     * 不会抛出异常。
     *
     * @param param 登录参数
     */
    void login(LoginParam param);

    /**
     * 用户登出
     */
    void logout();

    /**
     * 添加登录回调（全局监听）
     * 支持多监听者，可用于状态栏、日志等全局监听
     *
     * @param callback 回调接口
     */
    void addLoginCallback(LoginCallback callback);

    /**
     * 移除登录回调
     *
     * @param callback 回调接口
     */
    void removeLoginCallback(LoginCallback callback);

    /**
     * 获取当前登录信息（状态 + 用户名 + 设备标识）
     *
     * <p>未登录时 username 和 identifyId 均为空字符串。
     *
     * @return 登录信息
     */
    LoginInfo getLoginInfo();

    /**
     * 获取当前有效的 ACM Token
     *
     * <p>执行逻辑：
     * <ol>
     *   <li>若 Token 未过期，直接返回缓存值</li>
     *   <li>若 Token 即将过期或已过期（剩余时间 &le; 3小时），同步执行刷新</li>
     *   <li>刷新成功返回新 Token，刷新失败返回 null</li>
     * </ol>
     *
     * <p><strong>注意：此方法可能阻塞等待 HTTP 请求完成（Token 刷新时），
     * 建议在后台线程调用，避免阻塞主线程。</strong></p>
     *
     * @return 当前有效的 ACM Token；未登录或刷新失败时返回 null
     */
    String getCurrentToken();

    /**
     * 获取当前 TokenResult（包含完整 Token 信息）
     *
     * <p>返回完整的 TokenResult 对象，包括：
     * <ul>
     *   <li>token - ACM Token 字符串</li>
     *   <li>expireTime - 有效期（秒）</li>
     *   <li>extraData - ACM 返回的额外数据（Map）</li>
     * </ul>
     *
     * <p>此方法仅返回当前缓存的 Token 信息，不触发刷新操作。
     * 若需要确保获取最新 Token，请使用 {@link #getCurrentToken()}。</p>
     *
     * @return TokenResult 对象；未登录或无 Token 时返回失败的 TokenResult
     */
    TokenResult getTokenResult();
}
