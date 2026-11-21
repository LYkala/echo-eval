/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logger.local;

import com.huawei.aisfsdkv2.component.facade.ACMFacade;
import com.huawei.aisfsdkv2.component.facade.model.acm.AcmException;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

import java.io.File;

/**
 * 上传日志管理
 *
 * @author l00504561
 * @since 2026-02-08
 */
public class UploadLogManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("UploadLogManager");
    private static final UploadLogManager uploadLogManager = new UploadLogManager();

    private UploadLogManager() {
    }

    /**
     * 获取单例
     *
     * @return 获取单例
     */
    public static UploadLogManager getInstance() {
        return uploadLogManager;
    }

    /**
     * 上传单个日志文件（同步执行，需在后台线程调用）
     *
     * <p>上传成功后不删除本地文件，依赖日志老化机制清理。</p>
     *
     * @param zipFile 要上传的 zip 文件
     * @return 上传结果
     */
    public UploadResult uploadLogFile(File zipFile) {
        if (zipFile == null || !zipFile.exists()) {
            LOGGER.warn("UploadLogManager.uploadLogFile: file not exist");
            return new UploadResult(false);
        }
        try {
            LOGGER.info("UploadLogManager.uploadLogFile: uploading {}", zipFile.getName());
            ACMFacade.getInstance().uploadLogFile(zipFile);
            LOGGER.info("UploadLogManager.uploadLogFile: success {}", zipFile.getName());
            return new UploadResult(true);
        } catch (AcmException e) {
            LOGGER.error("UploadLogManager.uploadLogFile: upload failed: {}",
                    zipFile.getName(), e);
            return new UploadResult(false);
        }
    }
}
