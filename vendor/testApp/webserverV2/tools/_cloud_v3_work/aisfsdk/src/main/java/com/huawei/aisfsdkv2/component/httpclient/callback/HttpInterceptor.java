/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.callback;

import com.huawei.aisfsdkv2.component.httpclient.model.NettyHttpRequest;
import com.huawei.aisfsdkv2.component.httpclient.model.NettyHttpResponse;

/**
 * HTTP拦截器接口
 *
 * <p>参考OkHttp拦截器设计，支持请求/响应拦截和链式调用。
 * <p>拦截器可用于：日志记录、请求重试、认证添加、响应处理等。
 *
 * @since 2026-05-25
 */
public interface HttpInterceptor {

    /**
     * 拦截HTTP请求/响应
     *
     * @param chain 拦截器链，用于继续执行下一个拦截器或发起实际请求
     * @return HTTP响应
     * @throws Exception 拦截过程中发生异常
     */
    NettyHttpResponse intercept(Chain chain) throws Exception;

    /**
     * 拦截器链接口
     */
    interface Chain {

        /**
         * 获取当前请求
         */
        NettyHttpRequest request();

        /**
         * 继续执行拦截器链，发起实际请求
         *
         * @param request 可修改的请求对象
         * @return HTTP响应
         * @throws Exception 执行过程中发生异常
         */
        NettyHttpResponse proceed(NettyHttpRequest request) throws Exception;

        /**
         * 获取当前拦截器在链中的位置
         */
        int index();

        /**
         * 获取所有拦截器数量
         */
        int interceptorCount();
    }
}