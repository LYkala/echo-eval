/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.accessibility.model;

/**
 * 操作回调
 */
public interface ActionCallback {
    /**
     * 操作成功
     */
    void onSuccess();

    /**
     * 操作失败
     *
     * @param errorCode 错误码
     * @param errorMsg 错误信息
     */
    void onError(int errorCode, String errorMsg);
}
