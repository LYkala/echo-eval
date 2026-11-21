/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.fileserver;

import com.huawei.aisfsdkv2.api.fileserver.FileDownloadCallback;
import com.huawei.aisfsdkv2.api.fileserver.FileServerApi;
import com.huawei.aisfsdkv2.api.fileserver.FileUploadCallback;
import com.huawei.aisfsdkv2.component.facade.ACMFacade;
import com.huawei.aisfsdkv2.component.facade.model.acm.AcmException;
import com.huawei.aisfsdkv2.component.facade.model.acm.UploadResult;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;

import java.io.File;

/**
 * 文件服务接口实现
 *
 * @since 2026-04-11
 */
public class FileServerApiImpl implements FileServerApi {

    private static final Logger LOGGER = LoggerFactory.getLogger("FileServerApiImpl");

    private static class Holder {
        private static final FileServerApiImpl INSTANCE = new FileServerApiImpl();
    }

    private FileServerApiImpl() {
    }

    /**
     * 获取单例
     *
     * @return 单例实例
     */
    public static FileServerApiImpl getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 异步下载文件
     *
     * <p>从指定URL下载文件并保存到本地路径，下载结果通过回调通知。</p>
     *
     * @param fileUrl 文件下载URL，不能为null或空
     * @param savePath 文件保存的本地路径，不能为null或空
     * @param callback 下载结果回调，不能为null
     */
    @Override
    public void downloadFileAsync(String fileUrl, String savePath, FileDownloadCallback callback) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            LOGGER.warn("FileServerApiImpl.downloadFileAsync: fileUrl is null or empty");
            if (callback != null) {
                callback.onFailure(fileUrl, savePath, -1, "fileUrl is null or empty");
            }
            return;
        }
        if (savePath == null || savePath.trim().isEmpty()) {
            LOGGER.warn("FileServerApiImpl.downloadFileAsync: savePath is null or empty");
            if (callback != null) {
                callback.onFailure(fileUrl, savePath, -1, "savePath is null or empty");
            }
            return;
        }
        if (callback == null) {
            LOGGER.warn("FileServerApiImpl.downloadFileAsync: callback is null");
            return;
        }
        ThreadPoolManager.getInstance().executeSharedThreadPool(new DownloadTask(fileUrl, savePath, callback));
    }

    /**
     * 同步下载文件
     *
     * <p>从指定URL下载文件并保存到本地路径，阻塞直到下载完成。</p>
     *
     * @param fileUrl 文件下载URL，不能为null或空
     * @param savePath 文件保存的本地路径，不能为null或空
     * @return true 下载成功，false 下载失败
     */
    @Override
    public boolean downloadFileSync(String fileUrl, String savePath) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            LOGGER.warn("FileServerApiImpl.downloadFileSync: fileUrl is null or empty");
            return false;
        }
        if (savePath == null || savePath.trim().isEmpty()) {
            LOGGER.warn("FileServerApiImpl.downloadFileSync: savePath is null or empty");
            return false;
        }
        long startTime = System.currentTimeMillis();
        LOGGER.info("FileServerApiImpl.downloadFileSync: start, fileUrl={}, savePath={}", fileUrl, savePath);
        try {
            File result = ACMFacade.getInstance().downloadFile(fileUrl, savePath);
            long elapsed = System.currentTimeMillis() - startTime;
            if (result != null && result.exists()) {
                LOGGER.info("FileServerApiImpl.downloadFileSync: success, fileUrl={}, savePath={}, elapsed={}ms",
                        fileUrl, savePath, elapsed);
                return true;
            } else {
                LOGGER.error("FileServerApiImpl.downloadFileSync: failed, file not exists, "
                        + "fileUrl={}, savePath={}, elapsed={}ms", fileUrl, savePath, elapsed);
                return false;
            }
        } catch (AcmException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            LOGGER.error("FileServerApiImpl.downloadFileSync: AcmException, "
                    + "fileUrl={}, savePath={}, code={}, msg={}, elapsed={}ms",
                    fileUrl, savePath, e.getCode(), e.getMessage(), elapsed);
            return false;
        }
    }

    /**
     * 异步上传文件
     *
     * <p>上传文件数据到服务器，上传结果通过回调通知。</p>
     *
     * @param fileName 文件名，不能为null或空
     * @param fileData 文件字节数据，不能为null或空
     * @param callback 上传结果回调，不能为null
     */
    @Override
    public void uploadFileAsync(String fileName, byte[] fileData, FileUploadCallback callback) {
        if (fileName == null || fileName.trim().isEmpty()) {
            LOGGER.warn("FileServerApiImpl.uploadFileAsync: fileName is null or empty");
            if (callback != null) {
                callback.onFailure(fileName, -1, "fileName is null or empty");
            }
            return;
        }
        if (fileData == null || fileData.length == 0) {
            LOGGER.warn("FileServerApiImpl.uploadFileAsync: fileData is null or empty");
            if (callback != null) {
                callback.onFailure(fileName, -1, "fileData is null or empty");
            }
            return;
        }
        if (callback == null) {
            LOGGER.warn("FileServerApiImpl.uploadFileAsync: callback is null");
            return;
        }
        ThreadPoolManager.getInstance().executeSharedThreadPool(new UploadTask(fileName, fileData, callback));
    }

    /**
     * 同步上传文件
     *
     * <p>上传文件数据到服务器，阻塞直到上传完成。</p>
     *
     * @param fileName 文件名，不能为null或空
     * @param fileData 文件字节数据，不能为null或空
     * @return 上传成功返回文件UUID，失败返回null
     */
    @Override
    public String uploadFileSync(String fileName, byte[] fileData) {
        return uploadFileSync(fileName, fileData, false);
    }

    @Override
    public String uploadFileSync(String fileName, byte[] fileData, boolean isClientClose) {
        if (fileName == null || fileName.trim().isEmpty()) {
            LOGGER.warn("FileServerApiImpl.uploadFileSync: fileName is null or empty");
            return null;
        }
        if (fileData == null || fileData.length == 0) {
            LOGGER.warn("FileServerApiImpl.uploadFileSync: fileData is null or empty");
            return null;
        }
        long startTime = System.currentTimeMillis();
        LOGGER.info("FileServerApiImpl.uploadFileSync: start, fileName={}, size={}bytes, isClientClose={}",
                fileName, fileData.length, isClientClose);
        try {
            UploadResult result = ACMFacade.getInstance().uploadImageFile(fileName, fileData, isClientClose);
            long elapsed = System.currentTimeMillis() - startTime;
            if (result != null && result.getFileUuid() != null && !result.getFileUuid().isEmpty()) {
                LOGGER.info("FileServerApiImpl.uploadFileSync: success, fileName={}, fileUuid={}, elapsed={}ms",
                        fileName, result.getFileUuid(), elapsed);
                return result.getFileUuid();
            } else {
                LOGGER.error("FileServerApiImpl.uploadFileSync: failed, result is null or fileUuid is empty, "
                        + "fileName={}, elapsed={}ms", fileName, elapsed);
                return null;
            }
        } catch (AcmException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            LOGGER.error("FileServerApiImpl.uploadFileSync: AcmException, fileName={}, code={}, msg={}, elapsed={}ms",
                    fileName, e.getCode(), e.getMessage(), elapsed);
            return null;
        }
    }
}
