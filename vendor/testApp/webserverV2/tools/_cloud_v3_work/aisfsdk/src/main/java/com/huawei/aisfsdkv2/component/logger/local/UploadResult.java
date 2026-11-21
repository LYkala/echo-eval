/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logger.local;

/**
 * 日志上传结果
 *
 * @author l00889076
 * @since 2026-07-18
 */
public class UploadResult {

    /**
     * 上传是否成功
     */
    public final boolean success;

    /**
     * 构造方法
     *
     * @param success 上传是否成功
     */
    public UploadResult(boolean success) {
        this.success = success;
    }
}
