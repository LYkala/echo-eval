/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.login;

import com.huawei.aisfsdkv2.util.RetryUtil;

/**
 * 登录参数类
 * 使用 Builder 模式构建，支持可扩展的登录配置
 *
 * <p>使用示例：
 * <pre>
 * LoginParam param = new LoginParam.Builder()
 *     .username("user123")
 *     .password("abc123")
 *     .timeoutMs(60000)
 *     .autoReconnect(false)
 *     .build();
 * loginApi.login(param);
 * </pre>
 */
public class LoginParam {

    /**
     * 单次登录回调（可选）
     * 优先级高于全局回调，仅对本次登录有效
     */
    private final LoginCallback callback;

    /**
     * 默认登录超时时间：30秒
     */
    private static final long DEFAULT_TIMEOUT_MS = 30000;

    /**
     * 默认是否自动重连：是
     */
    private static final boolean DEFAULT_AUTO_RECONNECT = true;

    private final String username;
    private final String password;
    private final long timeoutMs;
    private final boolean autoReconnect;
    private final int maxRetryCount;
    private final TicketProvider ticketProvider;

    private LoginParam(Builder builder) {
        this.username = builder.username;
        this.password = builder.password;
        this.callback = builder.callback;
        this.timeoutMs = builder.timeoutMs;
        this.autoReconnect = builder.autoReconnect;
        this.maxRetryCount = builder.maxRetryCount;
        this.ticketProvider = builder.ticketProvider;
    }

    /**
     * 获取用户名
     *
     * @return 用户名
     */
    public String getUsername() {
        return username;
    }

    /**
     * 获取用户密码
     *
     * @return 用户密码
     */
    public String getPassword() {
        return password;
    }

    /**
     * 获取登录超时时间（毫秒）
     *
     * @return 超时时间
     */
    public long getTimeoutMs() {
        return timeoutMs;
    }

    /**
     * 是否自动重连
     *
     * @return true 表示自动重连
     */
    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    /**
     * 获取最大重试次数
     *
     * @return 最大重试次数
     */
    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    /**
     * 获取 Ticket 提供器
     *
     * @return Ticket 提供器，可能为 null
     */
    public TicketProvider getTicketProvider() {
        return ticketProvider;
    }

    /**
     * 获取单次登录回调
     *
     * @return 回调接口，可能为 null
     */
    public LoginCallback getCallback() {
        return callback;
    }

    /**
     * LoginParam 构建器
     */
    public static class Builder {
        private String username;
        private String password;
        private LoginCallback callback;
        private long timeoutMs = DEFAULT_TIMEOUT_MS;
        private boolean autoReconnect = DEFAULT_AUTO_RECONNECT;
        private int maxRetryCount = RetryUtil.RETRY_COUNT_3;
        private TicketProvider ticketProvider;

        /**
         * 设置用户名（必需）
         *
         * @param username 用户名
         * @return Builder
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * 设置用户密码（必需）
         *
         * @param password 用户密码
         * @return Builder
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * 设置登录超时时间（可选，默认30000ms）
         *
         * @param timeoutMs 超时时间（毫秒）
         * @return Builder
         */
        public Builder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        /**
         * 设置是否自动重连（可选，默认true）
         *
         * @param autoReconnect 是否自动重连
         * @return Builder
         */
        public Builder autoReconnect(boolean autoReconnect) {
            this.autoReconnect = autoReconnect;
            return this;
        }

        /**
         * 设置最大重试次数（可选，默认3次）
         *
         * @param maxRetryCount 最大重试次数
         * @return Builder
         */
        public Builder maxRetryCount(int maxRetryCount) {
            this.maxRetryCount = maxRetryCount;
            return this;
        }

        /**
         * 设置单次登录回调（可选）
         * 该回调仅对本次登录有效，优先级高于全局回调
         *
         * @param callback 回调接口
         * @return Builder
         */
        public Builder callback(LoginCallback callback) {
            this.callback = callback;
            return this;
        }

        /**
         * 设置 Ticket 提供器（可选）
         * 设置后，SDK 将使用 Ticket 换取 Token，而非 username/password 方式
         *
         * @param ticketProvider Ticket 提供器
         * @return Builder
         */
        public Builder ticketProvider(TicketProvider ticketProvider) {
            this.ticketProvider = ticketProvider;
            return this;
        }

        /**
         * 构建 LoginParam 对象
         *
         * @return LoginParam 实例
         * @throws IllegalArgumentException 如果必需参数未设置
         */
        public LoginParam build() {
            if (ticketProvider == null) {
                // 传统方式：需要 username 和 password
                if (username == null || username.isEmpty()) {
                    throw new IllegalArgumentException("username is required");
                }
                if (password == null || password.isEmpty()) {
                    throw new IllegalArgumentException("password is required");
                }
            } else {
                // Ticket 方式：username 可选，password 不需要
                if (username == null || username.isEmpty()) {
                    throw new IllegalArgumentException("username is required");
                }
            }
            return new LoginParam(this);
        }
    }
}
