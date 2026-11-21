/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.impl;

import com.huawei.aisfsdkv2.component.httpclient.config.HttpClientConfig;
import com.huawei.aisfsdkv2.component.httpclient.http.HttpClient;

import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

/**
 * ClientFactory 测试
 * 
 * <p><b>测试目的：验证工厂创建的客户端单例保证。</b>
 * <p><b>注意：ClientFactory 位于 impl 包中（包级私有访问）。</b>
 *
 * @since 2026-05-25
 */
public class ClientFactoryTest {
    
    /**
     * 测试相同配置创建的 HTTP 客户端是同一个实例（单例保证）
     */
    @Test
    public void testHttpClientSingletonWithSameConfig() {
        ClientFactory factory = ClientFactory.getInstance();
        HttpClientConfig config = HttpClientConfig.defaultCommon();
        
        // 第一次创建
        HttpClient client1 = factory.createHttpClient(config);
        assertNotNull("First client should not be null", client1);
        
        // 第二次创建（相同配置）
        HttpClient client2 = factory.createHttpClient(config);
        assertNotNull("Second client should not be null", client2);
        
        // 验证单例：必须是同一个实例
        assertSame("Same config should return same instance", client1, client2);
    }
    
    /**
     * 测试不同配置创建的 HTTP 客户端是不同实例
     */
    @Test
    public void testHttpClientDifferentInstanceWithDifferentConfig() {
        ClientFactory factory = ClientFactory.getInstance();
        
        // 创建 Common 配置客户端
        HttpClientConfig commonConfig = HttpClientConfig.defaultCommon();
        HttpClient commonClient = factory.createHttpClient(commonConfig);
        
        // 创建 File 配置客户端
        HttpClientConfig fileConfig = HttpClientConfig.defaultFile();
        HttpClient fileClient = factory.createHttpClient(fileConfig);
        
        // 验证：不同配置返回不同实例
        assertNotSame("Different config should return different instance", 
            commonClient, fileClient);
    }
    
    /**
     * 测试清理缓存后重新创建客户端
     */
    @Test
    public void testClearCacheAndRecreate() {
        ClientFactory factory = ClientFactory.getInstance();
        HttpClientConfig config = HttpClientConfig.defaultCommon();
        
        // 第一次创建
        HttpClient client1 = factory.createHttpClient(config);
        
        // 清理缓存
        factory.clearCache();
        
        // 第二次创建（清理后）
        HttpClient client2 = factory.createHttpClient(config);
        
        // 验证：清理后创建的是新实例
        assertNotSame("After clear cache, should create new instance", client1, client2);
    }
}