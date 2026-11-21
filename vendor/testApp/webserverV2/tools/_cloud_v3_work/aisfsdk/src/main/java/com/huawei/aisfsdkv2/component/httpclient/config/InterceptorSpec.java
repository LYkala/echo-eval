/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.config;

import com.huawei.aisfsdkv2.component.httpclient.callback.HttpInterceptor;

/**
 * 拦截器规范接口（抽象配置）
 * 
 * <p>每种拦截器类型实现此接口，提供创建拦截器实例的逻辑。
 *
 * @since 2026-05-25
 */
public interface InterceptorSpec {
    
    /**
     * 创建拦截器实例
     * @return HttpInterceptor 实例
     */
    HttpInterceptor createInterceptor();
    
    /**
     * 获取拦截器类型名称（用于日志和调试）
     *
     * @return 类型名称
     */
    String getTypeName();
}