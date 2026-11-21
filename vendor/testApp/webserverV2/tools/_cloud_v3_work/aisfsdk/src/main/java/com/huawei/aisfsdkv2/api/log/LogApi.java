/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.log;

/**
 * 日志接口
 *
 * @author l00504561
 * @since 2026-02-06
 */
public interface LogApi {

    /**
     * 设置SDK日志配置
     *
     * @param sdkLogConfig 日志配置
     */
    void setSdkLogConfig(SdkLogConfig sdkLogConfig);

    /**
     * 触发上传日志事件
     */
    void uploadLogTrigger();

    /**
     * 获取本地日志目录路径
     *
     * <p>返回SDK本地日志文件存储的目录绝对路径。如果日志模块未初始化
     * （{@link #setSdkLogConfig} 未调用），则返回null。</p>
     *
     * @return 日志目录绝对路径，未初始化时返回null
     */
    String getLocalLogDirectory();

    /**
     * 注册日志采集回调，App 可借此提供自定义日志
     *
     * <p>SDK 在采集自身日志后，会调用回调的 {@link LogCollectCallback#onCollectLog()} 方法
     * 获取 App 侧日志压缩包路径，并将其与 SDK 日志合并打包上传。</p>
     *
     * <p>回调返回的 zip 文件大小上限为 10MB，超过将被丢弃。</p>
     *
     * @param callback 采集回调，传 null 取消注册
     */
    void setLogCollectCallback(LogCollectCallback callback);

    /**
     * 注册日志上传结果回调
     *
     * <p>SDK 在日志上传完成后（成功或失败），会调用回调的
     * {@link LogUploadCallback#onUploadResult(boolean)} 方法通知上传结果。</p>
     *
     * @param callback 上传结果回调，传 null 取消注册
     */
    void setLogUploadCallback(LogUploadCallback callback);
}
