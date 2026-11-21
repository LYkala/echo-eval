/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.http;

import com.huawei.aisfsdkv2.component.httpclient.model.NettyHttpRequest;
import com.huawei.aisfsdkv2.component.httpclient.model.NettyHttpResponse;
import com.huawei.aisfsdkv2.component.httpclient.callback.HttpCallback;
import com.huawei.aisfsdkv2.component.httpclient.callback.HttpInterceptor;

import java.util.List;

/**
 * HTTP客户端接口
 *
 * @since 2026-05-25
 */
public interface HttpClient {

    /**
     * 同步执行HTTP请求
     *
     * @param request HTTP请求对象
     * @return HTTP响应对象
     * @throws Exception 请求失败时抛出
     */
    NettyHttpResponse executeSync(NettyHttpRequest request) throws Exception;

    /**
     * 异步执行HTTP请求
     *
     * @param request HTTP请求对象
     * @param callback 异步回调接口
     */
    void executeAsync(NettyHttpRequest request, HttpCallback callback);

    /**
     * 添加拦截器
     *
     * @param interceptor 拦截器实例
     */
    void addInterceptor(HttpInterceptor interceptor);

    /**
     * 获取所有拦截器
     *
     * @return 拦截器列表
     */
    List<HttpInterceptor> getInterceptors();

    /**
     * 关闭客户端释放资源
     */
    void shutdown();
}