/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.log;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.log.LogApi;
import com.huawei.aisfsdkv2.api.log.LogCollectCallback;
import com.huawei.aisfsdkv2.api.log.LogUploadCallback;
import com.huawei.aisfsdkv2.api.log.SdkLogConfig;
import com.huawei.aisfsdkv2.api.login.LoginStatus;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerImpl;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.logger.local.LocalFileLogger;
import com.huawei.aisfsdkv2.component.logger.manager.CrashCatcher;

/**
 * 日志接口实现
 *
 * @author l00504561
 * @since 2026-02-07
 */
public class LogApiImpl implements LogApi {

    private static final Logger LOGGER = LoggerFactory.getLogger("LogApiImpl");

    private static final LogApiImpl logApi = new LogApiImpl();

    private volatile LogCollectCallback logCollectCallback;

    private volatile LogUploadCallback logUploadCallback;

    private LogApiImpl() {
        CrashCatcher.getInstance().init();
    }

    /**
     * 获取单例
     *
     * @return 获取单例
     */
    public static LogApiImpl getInstance() {
        return logApi;
    }

    /**
     * 设置SDK日志配置
     *
     * @param sdkLogConfig 日志配置
     */
    @Override
    public void setSdkLogConfig(SdkLogConfig sdkLogConfig) {
        if (sdkLogConfig == null) {
            return;
        }
        LoggerImpl.updateConfig(sdkLogConfig.isStdoutEnable(), sdkLogConfig.isSecurityFilterEnable(),
                sdkLogConfig.isLogEnable());
        LocalFileLogger.getInstance().start(sdkLogConfig.getSdkLocalLogConfig());
    }

    /**
     * 触发上传日志事件
     * <p>
     * 仅在登录成功（{@link LoginStatus#CONNECTED}）后才触发上传，否则打印警告日志并忽略。
     */
    @Override
    public void uploadLogTrigger() {
        LoginStatus loginStatus = AISFSDKClient.getLoginApi().getLoginInfo().getStatus();
        if (loginStatus != LoginStatus.CONNECTED) {
            LOGGER.warn("LogApiImpl.uploadLogTrigger: Not logged in, skip upload, currentState={}", loginStatus);
            return;
        }
        LocalFileLogger.getInstance().triggerLogUploadEvent();
    }

    /**
     * 获取本地日志目录路径
     *
     * @return 日志目录绝对路径，未初始化时返回null
     */
    @Override
    public String getLocalLogDirectory() {
        return LocalFileLogger.getInstance().getLogDirectory();
    }

    /**
     * 注册日志采集回调，App 可借此提供自定义日志
     *
     * @param callback 采集回调，传 null 取消注册
     */
    @Override
    public void setLogCollectCallback(LogCollectCallback callback) {
        this.logCollectCallback = callback;
        LOGGER.info("LogApiImpl.setLogCollectCallback: callback={}",
                callback != null ? callback.getClass().getSimpleName() : "null");
    }

    /**
     * 获取已注册的日志采集回调
     *
     * @return 日志采集回调，未注册时返回 null
     */
    public LogCollectCallback getLogCollectCallback() {
        return logCollectCallback;
    }

    /**
     * 注册日志上传结果回调
     *
     * @param callback 上传结果回调，传 null 取消注册
     */
    @Override
    public void setLogUploadCallback(LogUploadCallback callback) {
        this.logUploadCallback = callback;
        LocalFileLogger.getInstance().setLogUploadCallback(callback);
        LOGGER.info("LogApiImpl.setLogUploadCallback: callback={}",
                callback != null ? callback.getClass().getSimpleName() : "null");
    }

    /**
     * 获取已注册的日志上传结果回调
     *
     * @return 上传结果回调，未注册时返回 null
     */
    public LogUploadCallback getLogUploadCallback() {
        return logUploadCallback;
    }
}
