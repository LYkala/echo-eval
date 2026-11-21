/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.config;

import com.huawei.aisfsdkv2.component.httpclient.callback.HttpInterceptor;
import com.huawei.aisfsdkv2.component.httpclient.callback.LoggingInterceptor;
import com.huawei.aisfsdkv2.component.httpclient.callback.RetryInterceptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 拦截器链配置
 * <p>封装拦截器链的配置，支持配置拦截器类型、顺序和参数。
 * @since 2026-05-25
 */
public class InterceptorChainConfig {
    
    private final List<InterceptorSpec> interceptorSpecs;
    private InterceptorChainConfig(List<InterceptorSpec> specs) {
        this.interceptorSpecs = specs != null ? Collections.unmodifiableList(specs) : Collections.emptyList();
    }
    
    /**
     * 通用 HTTP 客户端默认拦截器链配置
     * @return 通用客户端拦截器链配置
     */
    public static InterceptorChainConfig defaultCommon() {
        List<InterceptorSpec> specs = new ArrayList<>();
        specs.add(new LoggingInterceptorSpec(LoggingInterceptor.LogLevel.BASIC));
        specs.add(new RetryInterceptorSpec(3, 1000, RetryInterceptor.RetryCondition.DEFAULT));
        return new InterceptorChainConfig(specs);
    }
    
    /**
     * 文件 HTTP 客户端默认拦截器链配置
     * @return 文件客户端拦截器链配置
     */
    public static InterceptorChainConfig defaultFile() {
        List<InterceptorSpec> specs = new ArrayList<>();
        specs.add(new LoggingInterceptorSpec(LoggingInterceptor.LogLevel.BASIC));
        specs.add(new RetryInterceptorSpec(2, 2000, RetryInterceptor.RetryCondition.DEFAULT));
        return new InterceptorChainConfig(specs);
    }
    
    /**
     * 自定义拦截器链配置
     *
     * @param specs 拦截器规范列表
     * @return 自定义拦截器链配置实例
     */
    public static InterceptorChainConfig custom(List<InterceptorSpec> specs) {
        return new InterceptorChainConfig(specs);
    }
    
    /**
     * 创建拦截器实例链
     * @return 拦截器实例列表
     */
    public List<HttpInterceptor> createInterceptorChain() {
        List<HttpInterceptor> chain = new ArrayList<>();
        for (InterceptorSpec spec : interceptorSpecs) {
            chain.add(spec.createInterceptor());
        }
        return chain;
    }
    
    /**
     * 获取拦截器规范列表
     *
     * @return 拦截器规范列表
     */
    public List<InterceptorSpec> getInterceptorSpecs() {
        return interceptorSpecs;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("InterceptorChainConfig{");
        for (InterceptorSpec spec : interceptorSpecs) {
            sb.append(spec.getTypeName()).append(", ");
        }
        if (!interceptorSpecs.isEmpty()) {
            sb.setLength(sb.length() - 2);
        }
        sb.append("}");
        return sb.toString();
    }
}