/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.log;

/**
 * 日志上传结果回调接口，由宿主 App 注册实现
 *
 * <p>SDK 在日志上传完成后调用此回调通知上传结果。</p>
 *
 * @author l00889076
 * @since 2026-07-24
 */
public interface LogUploadCallback {

    /**
     * 日志上传完成后回调
     *
     * @param success 上传是否成功
     */
    void onUploadResult(boolean success);
}