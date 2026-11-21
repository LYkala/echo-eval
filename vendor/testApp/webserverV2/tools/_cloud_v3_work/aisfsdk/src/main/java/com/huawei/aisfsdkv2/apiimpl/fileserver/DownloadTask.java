/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.fileserver;

import com.huawei.aisfsdkv2.api.fileserver.FileDownloadCallback;
import com.huawei.aisfsdkv2.component.facade.ACMFacade;
import com.huawei.aisfsdkv2.component.facade.model.acm.AcmException;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

import java.io.File;

/**
 * 文件下载任务
 *
 * <p>在后台线程执行文件下载，通过回调通知结果。</p>
 *
 * @since 2026-04-11
 */
class DownloadTask implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger("DownloadTask");

    private final String fileUrl;
    private final String savePath;
    private final FileDownloadCallback callback;

    /**
     * 构造方法
     *
     * @param fileUrl 文件URL
     * @param savePath 保存路径
     * @param callback 回调接口
     */
    DownloadTask(String fileUrl, String savePath, FileDownloadCallback callback) {
        this.fileUrl = fileUrl;
        this.savePath = savePath;
        this.callback = callback;
    }

    @Override
    public void run() {
        long startTime = System.currentTimeMillis();
        LOGGER.info("DownloadTask.run: start, fileUrl={}, savePath={}", fileUrl, savePath);
        try {
            File result = ACMFacade.getInstance().downloadFile(fileUrl, savePath);
            long elapsed = System.currentTimeMillis() - startTime;
            if (result != null && result.exists()) {
                LOGGER.info("DownloadTask.run: success, fileUrl={}, savePath={}, elapsed={}ms",
                        fileUrl, savePath, elapsed);
                callback.onSuccess(fileUrl, savePath);
            } else {
                LOGGER.error("DownloadTask.run: failed, file not exists, fileUrl={}, savePath={}, elapsed={}ms",
                        fileUrl, savePath, elapsed);
                callback.onFailure(fileUrl, savePath, -1, "Downloaded file not exists");
            }
        } catch (AcmException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            LOGGER.error("DownloadTask.run: AcmException, fileUrl={}, savePath={}, code={}, msg={}, elapsed={}ms",
                    fileUrl, savePath, e.getCode(), e.getMessage(), elapsed);
            callback.onFailure(fileUrl, savePath, e.getCode(), e.getMessage());
        }
    }
}
