/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.fileserver;

import com.huawei.aisfsdkv2.api.fileserver.FileUploadCallback;
import com.huawei.aisfsdkv2.component.facade.ACMFacade;
import com.huawei.aisfsdkv2.component.facade.model.acm.AcmException;
import com.huawei.aisfsdkv2.component.facade.model.acm.UploadResult;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

/**
 * 文件上传任务
 *
 * <p>在后台线程执行文件上传，通过回调通知结果。</p>
 *
 * @since 2026-04-14
 */
class UploadTask implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger("UploadTask");

    private final String fileName;
    private final byte[] fileData;
    private final FileUploadCallback callback;

    /**
     * 构造方法
     *
     * @param fileName 文件名
     * @param fileData 文件字节数据
     * @param callback 回调接口
     */
    UploadTask(String fileName, byte[] fileData, FileUploadCallback callback) {
        this.fileName = fileName;
        this.fileData = fileData;
        this.callback = callback;
    }

    @Override
    public void run() {
        long startTime = System.currentTimeMillis();
        LOGGER.info("UploadTask.run: start, fileName={}, size={}bytes", fileName, fileData.length);
        try {
            UploadResult result = ACMFacade.getInstance().uploadImageFile(fileName, fileData);
            long elapsed = System.currentTimeMillis() - startTime;
            if (result != null && result.getFileUuid() != null && !result.getFileUuid().isEmpty()) {
                LOGGER.info("UploadTask.run: success, fileName={}, fileUuid={}, elapsed={}ms",
                        fileName, result.getFileUuid(), elapsed);
                callback.onSuccess(fileName, result.getFileUuid());
            } else {
                LOGGER.error("UploadTask.run: failed, result is null or fileUuid is empty, fileName={}, elapsed={}ms",
                        fileName, elapsed);
                callback.onFailure(fileName, -1, "Upload result is null or fileUuid is empty");
            }
        } catch (AcmException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            LOGGER.error("UploadTask.run: AcmException, fileName={}, code={}, msg={}, elapsed={}ms",
                    fileName, e.getCode(), e.getMessage(), elapsed);
            callback.onFailure(fileName, e.getCode(), e.getMessage());
        }
    }
}
