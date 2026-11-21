/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.login.connection.connhandle;

/**
 * WebSocket 连接参数
 * 封装建立连接所需的所有参数
 */
public class ConnectionParam {
    private final String url;
    private final String identifyId;
    private final String token;
    private final String userName;
    private final ConnectionCallback callback;

    private ConnectionParam(Builder builder) {
        this.url = builder.url;
        this.identifyId = builder.identifyId;
        this.token = builder.token;
        this.userName = builder.userName;
        this.callback = builder.callback;
    }

    /**
     * 创建 Builder
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    public String getUrl() {
        return url;
    }

    public String getIdentifyId() {
        return identifyId;
    }

    public String getToken() {
        return token;
    }

    public String getUserName() {
        return userName;
    }

    public ConnectionCallback getCallback() {
        return callback;
    }

    /**
     * Builder 模式构建参数
     */
    public static class Builder {
        private String url;
        private String identifyId;
        private String token;
        private String userName;
        private ConnectionCallback callback;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder identifyId(String identifyId) {
            this.identifyId = identifyId;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder userName(String userName) {
            this.userName = userName;
            return this;
        }

        public Builder callback(ConnectionCallback callback) {
            this.callback = callback;
            return this;
        }

        /**
         * 构建 ConnectionParam
         *
         * @return ConnectionParam 实例
         * @throws IllegalStateException 如果必填参数未设置
         */
        public ConnectionParam build() {
            if (url == null || url.isEmpty()) {
                throw new IllegalStateException("url is required");
            }
            if (identifyId == null || identifyId.isEmpty()) {
                throw new IllegalStateException("identifyId is required");
            }
            if (token == null || token.isEmpty()) {
                throw new IllegalStateException("token is required");
            }
            if (userName == null || userName.isEmpty()) {
                throw new IllegalStateException("userName is required");
            }
            return new ConnectionParam(this);
        }
    }

    @Override
    public String toString() {
        return "ConnectionParam{" +
                "url='" + url + '\'' +
                ", identifyId='" + identifyId + '\'' +
                ", token='" + (token != null ? "***" : null) + '\'' +
                ", userName='" + userName + '\'' +
                ", callback=" + callback +
                '}';
    }
}
