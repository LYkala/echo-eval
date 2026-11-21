/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.fileserver;

/**
 * 文件上传回调接口
 *
 * <p>用于接收文件上传结果通知。</p>
 *
 * @since 2026-04-14
 */
public interface FileUploadCallback {
    /**
     * 上传成功回调
     *
     * @param fileName 上传的文件名
     * @param fileUuid 上传成功后生成的文件UUID
     */
    void onSuccess(String fileName, String fileUuid);

    /**
     * 上传失败回调
     *
     * @param fileName 上传的文件名
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     */
    void onFailure(String fileName, int errorCode, String errorMessage);
}
