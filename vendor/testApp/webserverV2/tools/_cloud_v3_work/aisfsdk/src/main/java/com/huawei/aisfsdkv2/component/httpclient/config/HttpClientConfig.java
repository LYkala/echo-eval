/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.config;

/**
 * HTTP 客户端配置
 * 
 * <p>统一配置超时、拦截器链等参数。
 * <p>支持 Builder 模式构建。
 * @since 2026-05-25
 */
public class HttpClientConfig {
    
    private final TimeoutConfig timeoutConfig;
    private final InterceptorChainConfig interceptorConfig;
    private HttpClientConfig(Builder builder) {
        this.timeoutConfig = builder.timeoutConfig;
        this.interceptorConfig = builder.interceptorConfig;
    }

    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * 通用 HTTP 客户端默认配置
     * @return 通用客户端配置实例
     */
    public static HttpClientConfig defaultCommon() {
        return builder()
            .timeoutConfig(TimeoutConfig.defaultCommon())
            .interceptorConfig(InterceptorChainConfig.defaultCommon())
            .build();
    }
    
    /**
     * 文件 HTTP 客户端默认配置
     * @return 文件客户端配置实例
     */
    public static HttpClientConfig defaultFile() {
        return builder()
            .timeoutConfig(TimeoutConfig.defaultFile())
            .interceptorConfig(InterceptorChainConfig.defaultFile())
            .build();
    }
    
    public TimeoutConfig getTimeoutConfig() {
        return timeoutConfig;
    }
    public InterceptorChainConfig getInterceptorConfig() {
        return interceptorConfig;
    }
    
    @Override
    public String toString() {
        return "HttpClientConfig{" +
            "timeout=" + timeoutConfig + ", " +
            "interceptors=" + interceptorConfig + "}";
    }

    public static class Builder {
        private TimeoutConfig timeoutConfig = TimeoutConfig.defaultCommon();
        private InterceptorChainConfig interceptorConfig = InterceptorChainConfig.defaultCommon();
        public Builder timeoutConfig(TimeoutConfig config) {
            this.timeoutConfig = config != null ? config : TimeoutConfig.defaultCommon();
            return this;
        }
        public Builder interceptorConfig(InterceptorChainConfig config) {
            this.interceptorConfig = config != null ? config : InterceptorChainConfig.defaultCommon();
            return this;
        }
        public HttpClientConfig build() {
            return new HttpClientConfig(this);
        }
    }
}