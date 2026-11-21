/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.log;

/**
 * 日志采集回调接口，由宿主 App 注册实现
 *
 * <p>SDK 在采集自身日志后调用此回调获取 App 侧日志，
 * App 返回的日志压缩包将与 SDK 日志合并后统一上传。</p>
 *
 * @author l00889076
 * @since 2026-07-18
 */
public interface LogCollectCallback {

    /**
     * SDK 采集自身日志后，调用此方法获取 App 侧日志压缩包路径
     *
     * @return App 日志压缩包(zip)的绝对路径，返回 null 表示无 App 日志
     */
    String onCollectLog();
}
