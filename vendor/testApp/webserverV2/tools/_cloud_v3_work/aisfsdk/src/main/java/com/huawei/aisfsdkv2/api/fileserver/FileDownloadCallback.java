/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.fileserver;

/**
 * 文件下载回调接口
 *
 * <p>用于接收文件下载结果通知。</p>
 *
 * @since 2026-04-11
 */
public interface FileDownloadCallback {
    /**
     * 下载成功回调
     *
     * @param fileUrl 下载的文件URL
     * @param savePath 文件保存路径
     */
    void onSuccess(String fileUrl, String savePath);

    /**
     * 下载失败回调
     *
     * @param fileUrl 下载的文件URL
     * @param savePath 文件保存路径
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     */
    void onFailure(String fileUrl, String savePath, int errorCode, String errorMessage);
}
