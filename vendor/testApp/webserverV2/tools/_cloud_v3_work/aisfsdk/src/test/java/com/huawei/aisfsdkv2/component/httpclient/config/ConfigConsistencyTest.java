/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.config;

import com.huawei.aisfsdkv2.component.httpclient.callback.HttpInterceptor;
import com.huawei.aisfsdkv2.component.httpclient.callback.LoggingInterceptor;
import com.huawei.aisfsdkv2.component.httpclient.callback.RetryInterceptor;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

/**
 * 配置一致性测试
 * 
 * <p><b>测试目的：验证重构后的配置值与原实现完全一致。</b>
 *
 * @since 2026-05-25
 */
public class ConfigConsistencyTest {
    
    /**
     * 测试 TimeoutConfig.defaultCommon() 的值与原常量一致
     * 
     * <p>原常量：
     * <ul>
     *   <li>COMMON_CONNECT_TIMEOUT_MS = 2000</li>
     *   <li>COMMON_READ_TIMEOUT_MS = 5000</li>
     *   <li>COMMON_WRITE_TIMEOUT_MS = 5000</li>
     *   <li>COMMON_CALL_TIMEOUT_MS = 10000</li>
     * </ul>
     */
    @Test
    public void testCommonTimeoutConsistency() {
        TimeoutConfig config = TimeoutConfig.defaultCommon();
        
        assertEquals("Common connect timeout", 2000, config.getConnectTimeoutMs());
        assertEquals("Common read timeout", 5000, config.getReadTimeoutMs());
        assertEquals("Common write timeout", 5000, config.getWriteTimeoutMs());
        assertEquals("Common call timeout", 10000, config.getCallTimeoutMs());
    }
    
    /**
     * 测试 TimeoutConfig.defaultFile() 的值与原常量一致
     * 
     * <p>原常量：
     * <ul>
     *   <li>FILE_CONNECT_TIMEOUT_MS = 5000</li>
     *   <li>FILE_READ_TIMEOUT_MS = 120000</li>
     *   <li>FILE_WRITE_TIMEOUT_MS = 120000</li>
     *   <li>FILE_CALL_TIMEOUT_MS = 300000</li>
     * </ul>
     */
    @Test
    public void testFileTimeoutConsistency() {
        TimeoutConfig config = TimeoutConfig.defaultFile();
        
        assertEquals("File connect timeout", 5000, config.getConnectTimeoutMs());
        assertEquals("File read timeout", 120000, config.getReadTimeoutMs());
        assertEquals("File write timeout", 120000, config.getWriteTimeoutMs());
        assertEquals("File call timeout", 300000, config.getCallTimeoutMs());
    }
    
    /**
     * 测试 InterceptorChainConfig.defaultCommon() 创建的拦截器链与原实现一致
     * 
     * <p>原实现：
     * <pre>
     * client.addInterceptor(new LoggingInterceptor(LoggingInterceptor.LogLevel.BASIC));
     * client.addInterceptor(new RetryInterceptor(3, 1000, RetryInterceptor.RetryCondition.DEFAULT));
     * </pre>
     */
    @Test
    public void testCommonInterceptorChainConsistency() {
        InterceptorChainConfig config = InterceptorChainConfig.defaultCommon();
        List<HttpInterceptor> interceptors = config.createInterceptorChain();
        
        // 验证拦截器数量
        assertEquals("Interceptor count", 2, interceptors.size());
        
        // 验证第一个拦截器：LoggingInterceptor
        assertTrue("First interceptor should be LoggingInterceptor", 
            interceptors.get(0) instanceof LoggingInterceptor);
        LoggingInterceptor loggingInterceptor = (LoggingInterceptor) interceptors.get(0);
        assertEquals("Logging level should be BASIC", 
            LoggingInterceptor.LogLevel.BASIC, loggingInterceptor.getLevel());
        
        // 验证第二个拦截器：RetryInterceptor
        assertTrue("Second interceptor should be RetryInterceptor", 
            interceptors.get(1) instanceof RetryInterceptor);
        RetryInterceptor retryInterceptor = (RetryInterceptor) interceptors.get(1);
        assertEquals("Max retries should be 3", 3, retryInterceptor.getMaxRetries());
        assertEquals("Retry delay should be 1000ms", 1000, retryInterceptor.getRetryDelayMs());
        assertEquals("Retry condition should be DEFAULT", 
            RetryInterceptor.RetryCondition.DEFAULT, retryInterceptor.getRetryCondition());
    }
    
    /**
     * 测试 InterceptorChainConfig.defaultFile() 创建的拦截器链与原实现一致
     * 
     * <p>原实现：
     * <pre>
     * client.addInterceptor(new LoggingInterceptor(LoggingInterceptor.LogLevel.BASIC));
     * client.addInterceptor(new RetryInterceptor(2, 2000, RetryInterceptor.RetryCondition.DEFAULT));
     * </pre>
     */
    @Test
    public void testFileInterceptorChainConsistency() {
        InterceptorChainConfig config = InterceptorChainConfig.defaultFile();
        List<HttpInterceptor> interceptors = config.createInterceptorChain();
        
        // 验证拦截器数量
        assertEquals("Interceptor count", 2, interceptors.size());
        
        // 验证第一个拦截器：LoggingInterceptor
        assertTrue("First interceptor should be LoggingInterceptor", 
            interceptors.get(0) instanceof LoggingInterceptor);
        LoggingInterceptor loggingInterceptor = (LoggingInterceptor) interceptors.get(0);
        assertEquals("Logging level should be BASIC", 
            LoggingInterceptor.LogLevel.BASIC, loggingInterceptor.getLevel());
        
        // 验证第二个拦截器：RetryInterceptor
        assertTrue("Second interceptor should be RetryInterceptor", 
            interceptors.get(1) instanceof RetryInterceptor);
        RetryInterceptor retryInterceptor = (RetryInterceptor) interceptors.get(1);
        assertEquals("Max retries should be 2", 2, retryInterceptor.getMaxRetries());
        assertEquals("Retry delay should be 2000ms", 2000, retryInterceptor.getRetryDelayMs());
        assertEquals("Retry condition should be DEFAULT", 
            RetryInterceptor.RetryCondition.DEFAULT, retryInterceptor.getRetryCondition());
    }
    
    /**
     * 测试 HttpClientConfig.defaultCommon() 整体配置一致性
     */
    @Test
    public void testCommonHttpClientConfigConsistency() {
        HttpClientConfig config = HttpClientConfig.defaultCommon();
        
        // 验证超时配置
        assertNotNull("Timeout config should not be null", config.getTimeoutConfig());
        assertEquals(2000, config.getTimeoutConfig().getConnectTimeoutMs());
        assertEquals(5000, config.getTimeoutConfig().getReadTimeoutMs());
        assertEquals(5000, config.getTimeoutConfig().getWriteTimeoutMs());
        assertEquals(10000, config.getTimeoutConfig().getCallTimeoutMs());
        
        // 验证拦截器配置
        assertNotNull("Interceptor config should not be null", config.getInterceptorConfig());
        assertEquals(2, config.getInterceptorConfig().getInterceptorSpecs().size());
    }
    
    /**
     * 测试 HttpClientConfig.defaultFile() 整体配置一致性
     */
    @Test
    public void testFileHttpClientConfigConsistency() {
        HttpClientConfig config = HttpClientConfig.defaultFile();
        
        // 验证超时配置
        assertNotNull("Timeout config should not be null", config.getTimeoutConfig());
        assertEquals(5000, config.getTimeoutConfig().getConnectTimeoutMs());
        assertEquals(120000, config.getTimeoutConfig().getReadTimeoutMs());
        assertEquals(120000, config.getTimeoutConfig().getWriteTimeoutMs());
        assertEquals(300000, config.getTimeoutConfig().getCallTimeoutMs());
        
        // 验证拦截器配置
        assertNotNull("Interceptor config should not be null", config.getInterceptorConfig());
        assertEquals(2, config.getInterceptorConfig().getInterceptorSpecs().size());
    }
    
    /**
     * 测试 WebSocketConfig.defaultConfig() 配置一致性
     */
    @Test
    public void testWebSocketConfigConsistency() {
        WebSocketConfig config = WebSocketConfig.defaultConfig();
        
        // 验证心跳间隔（低于云侧约 60s 空闲断链）
        assertEquals("Ping interval", 20000, config.getPingIntervalMs());
    }
}