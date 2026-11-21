/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 文件服务 API 接口包
 *
 * <p>本包提供文件下载等文件服务能力。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.api.fileserver.FileServerApi}：文件服务接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.fileserver.FileServerCallback}：文件下载回调接口</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取文件服务 API 实例
 *   FileServerApi fileServerApi = SdkManager.getInstance().getFileServerApi();
 *
 *   // 异步下载文件
 *   fileServerApi.downloadFileAsync(
 *       "https://example.com/file.pdf",
 *       "/sdcard/Download/file.pdf",
 *       new FileServerCallback() {
 *           &#64;Override
 *           public void onSuccess(String fileUrl, String savePath) {
 *               // 下载成功处理
 *           }
 *
 *           &#64;Override
 *           public void onFailure(String fileUrl, String savePath, int errorCode, String errorMessage) {
 *               // 下载失败处理
 *           }
 *       }
 *   );
 * </pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>确保文件保存路径有写入权限</li>
 *   <li>回调在后台线程执行，UI操作需切换到主线程</li>
 *   <li>建议在回调中释放相关资源</li>
 * </ul>
 *
 * @since 2026-04-11
 */
package com.huawei.aisfsdkv2.api.fileserver;
