/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.callback;

import com.huawei.aisfsdkv2.component.httpclient.HttpClientException;
import com.huawei.aisfsdkv2.component.httpclient.model.NettyHttpRequest;
import com.huawei.aisfsdkv2.component.httpclient.model.NettyHttpResponse;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

/**
 * 自动重试拦截器
 *
 * <p>参考OkHttp自动重试机制，支持配置最大重试次数和重试条件。
 *
 * @since 2026-05-25
 */
public final class RetryInterceptor implements HttpInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetryInterceptor");
    private final int maxRetries;
    private final long retryDelayMs;
    private final RetryCondition retryCondition;

    public RetryInterceptor() {
        this(3, 1000, RetryCondition.DEFAULT);
    }

    public RetryInterceptor(int maxRetries, long retryDelayMs, RetryCondition retryCondition) {
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.retryCondition = retryCondition != null ? retryCondition : RetryCondition.DEFAULT;
    }

    @Override
    public NettyHttpResponse intercept(Chain chain) throws Exception {
        NettyHttpRequest request = chain.request();
        int retryCount = 0;
        Exception lastException = null;
        while (retryCount <= maxRetries) {
            try {
                NettyHttpResponse response = chain.proceed(request);
                if (!shouldRetry(response) || retryCount >= maxRetries) {
                    return response;
                }
                LOGGER.warn("RetryInterceptor: response status={}, will retry (count={})", 
                    response.getStatusCode(), retryCount + 1);
            } catch (Exception e) {
                lastException = e;
                if (!shouldRetry(e) || retryCount >= maxRetries) {
                    throw e;
                }
                LOGGER.warn("RetryInterceptor: request failed, will retry (count={}, error={})",
                    retryCount + 1, e.getMessage());
            }
            retryCount++;
            if (retryDelayMs > 0) {
                Thread.sleep(retryDelayMs);
            }
        }
        if (lastException != null) {
            throw new HttpClientException("Max retries exceeded: " + maxRetries, lastException);
        }
        throw new HttpClientException("Max retries exceeded: " + maxRetries);
    }

    private boolean shouldRetry(NettyHttpResponse response) {
        return retryCondition.shouldRetry(response);
    }

    private boolean shouldRetry(Exception exception) {
        return retryCondition.shouldRetry(exception);
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
    public RetryCondition getRetryCondition() {
        return retryCondition;
    }

    public interface RetryCondition {
        boolean shouldRetry(NettyHttpResponse response);
        boolean shouldRetry(Exception exception);

        RetryCondition DEFAULT = new RetryCondition() {
            @Override
            public boolean shouldRetry(NettyHttpResponse response) {
                int code = response.getStatusCode();
                return code == 503 || code == 502 || code == 504 || code == 429;
            }
            @Override
            public boolean shouldRetry(Exception exception) {
                Throwable current = exception;
                while (current != null) {
                    String message = current.getMessage();
                    if (message != null && isRetryableMessage(message)) {
                        return true;
                    }
                    current = current.getCause();
                }
                return false;
            }

            private boolean isRetryableMessage(String message) {
                return message.contains("timeout") ||
                    message.contains("connection") ||
                    message.contains("Acquire") ||
                    message.contains("Channel inactive") ||
                    message.contains("inactive") ||
                    message.contains("Write failed");
            }
        };
    }
}