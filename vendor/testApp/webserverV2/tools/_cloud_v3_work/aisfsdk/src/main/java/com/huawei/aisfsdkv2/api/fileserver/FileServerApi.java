/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.fileserver;

/**
 * 文件服务接口
 *
 * <p>提供文件下载等文件服务能力。</p>
 *
 * @since 2026-04-11
 */
public interface FileServerApi {

    /**
     * 异步下载文件
     *
     * <p>从指定URL下载文件并保存到本地路径，下载结果通过回调通知。</p>
     *
     * @param fileUrl 文件下载URL，不能为null或空
     * @param savePath 文件保存的本地路径，不能为null或空
     * @param callback 下载结果回调，不能为null
     */
    void downloadFileAsync(String fileUrl, String savePath, FileDownloadCallback callback);

    /**
     * 同步下载文件
     *
     * <p>从指定URL下载文件并保存到本地路径，阻塞直到下载完成。</p>
     *
     * @param fileUrl 文件下载URL，不能为null或空
     * @param savePath 文件保存的本地路径，不能为null或空
     * @return true 下载成功，false 下载失败
     */
    boolean downloadFileSync(String fileUrl, String savePath);

    /**
     * 异步上传文件
     *
     * <p>上传文件数据到服务器，上传结果通过回调通知。</p>
     *
     * @param fileName 文件名，不能为null或空
     * @param fileData 文件字节数据，不能为null或空
     * @param callback 上传结果回调，不能为null
     */
    void uploadFileAsync(String fileName, byte[] fileData, FileUploadCallback callback);

    /**
     * 同步上传文件
     *
     * <p>上传文件数据到服务器，阻塞直到上传完成。</p>
     *
     * @param fileName 文件名，不能为null或空
     * @param fileData 文件字节数据，不能为null或空
     * @return 上传成功返回文件UUID，失败返回null
     */
    String uploadFileSync(String fileName, byte[] fileData);

    /**
     * 同步上传文件（支持客户端主动关闭连接）
     *
     * <p>上传文件数据到服务器，阻塞直到上传完成。</p>
     *
     * <p>当 {@code isClientClose=true} 时，请求头设置为 {@code Connection: keep-alive}，
     * 但客户端在请求完成后主动关闭连接，避免服务端先关闭连接导致 channelInactive
     * 覆盖成功响应的竞态问题。当 {@code isClientClose=false} 时，行为与
     * {@link #uploadFileSync(String, byte[])} 一致。</p>
     *
     * @param fileName 文件名，不能为null或空
     * @param fileData 文件字节数据，不能为null或空
     * @param isClientClose true 表示客户端主动关闭连接，false 表示遵循HTTP协议（默认行为）
     * @return 上传成功返回文件UUID，失败返回null
     */
    String uploadFileSync(String fileName, byte[] fileData, boolean isClientClose);

}
