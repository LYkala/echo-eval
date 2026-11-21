/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.config;

import com.huawei.aisfsdkv2.component.httpclient.callback.HttpInterceptor;
import com.huawei.aisfsdkv2.component.httpclient.callback.RetryInterceptor;

/**
 * 重试拦截器规范
 * <p><b>重要：创建的拦截器参数与原实现完全一致。</b>
 * @since 2026-05-25
 */
public class RetryInterceptorSpec implements InterceptorSpec {
    
    private final int maxRetries;
    private final long retryDelayMs;
    private final RetryInterceptor.RetryCondition retryCondition;
    
    /**
     * 构造函数
     *
     * @param maxRetries 最大重试次数
     * @param retryDelayMs 重试延迟（毫秒）
     * @param retryCondition 重试条件
     */
    public RetryInterceptorSpec(int maxRetries, long retryDelayMs, RetryInterceptor.RetryCondition retryCondition) {
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.retryCondition = retryCondition != null ? retryCondition : RetryInterceptor.RetryCondition.DEFAULT;
    }
    @Override
    public HttpInterceptor createInterceptor() {
        return new RetryInterceptor(maxRetries, retryDelayMs, retryCondition);
    }
    @Override
    public String getTypeName() {
        return "RetryInterceptor";
    }
    
    /**
     * 获取最大重试次数
     *
     * @return 最大重试次数
     */
    public int getMaxRetries() {
        return maxRetries;
    }
    
    /**
     * 获取重试延迟
     *
     * @return 重试延迟（毫秒）
     */
    public long getRetryDelayMs() {
        return retryDelayMs;
    }
    
    /**
     * 获取重试条件
     *
     * @return 重试条件
     */
    public RetryInterceptor.RetryCondition getRetryCondition() {
        return retryCondition;
    }
}