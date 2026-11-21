/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.callback;

import com.huawei.aisfsdkv2.component.httpclient.model.NettyHttpResponse;

/**
 * HTTP请求回调接口
 *
 * @since 2026-05-25
 */
public interface HttpCallback {

    /**
     * 请求成功回调
     */
    void onSuccess(NettyHttpResponse response);

    /**
     * 请求失败回调
     */
    void onFailure(Exception error);
}