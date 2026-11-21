/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api;

import android.content.Context;

import com.huawei.aisfsdkv2.api.base.DeviceInfo;
import com.huawei.aisfsdkv2.api.base.KeyFileInfo;
import com.huawei.aisfsdkv2.api.base.ServerInfo;
import com.huawei.aisfsdkv2.api.base.TLSConfig;
import com.huawei.aisfsdkv2.api.log.SdkLogConfig;

/**
 * 客户端参数
 *
 * @author l00504561
 * @since 2026-02-08
 */
public class AISFClientParam {

    /**
     * SDK角色名称
     */
    private String sdkRoleName;

    /**
     * android上下文
     */
    private Context appContext;

    /**
     * 设备信息
     */
    private DeviceInfo deviceInfo;

    /**
     * 日志配置
     */
    private SdkLogConfig sdkLogConfig;

    /**
     * 服务端信息
     */
    private ServerInfo serverInfo = new ServerInfo();

    /**
     * 关键文件信息
     */
    private KeyFileInfo keyFileInfo;

    /**
     * TLS配置
     */
    private TLSConfig tlsConfig;

    /**
     * 获取SDK角色名称
     *
     * @return SDK角色名称
     */
    public String getSdkRoleName() {
        return sdkRoleName;
    }

    /**
     * 设置SDK角色名称
     *
     * @param sdkRoleName SDK角色名称
     */
    public void setSdkRoleName(String sdkRoleName) {
        this.sdkRoleName = sdkRoleName;
    }

    /**
     * 获取android上下文
     *
     * @return android上下文
     */
    public Context getAppContext() {
        return appContext;
    }

    /**
     * 设置android上下文
     *
     * @param appContext android上下文
     */
    public void setAppContext(Context appContext) {
        this.appContext = appContext;
    }

    /**
     * 获取设备信息
     *
     * @return 设备信息
     */
    public DeviceInfo getDeviceInfo() {
        return deviceInfo;
    }

    /**
     * 设置设备信息
     *
     * @param deviceInfo 设备信息
     */
    public void setDeviceInfo(DeviceInfo deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    /**
     * 获取日志配置
     *
     * @return 日志配置
     */
    public SdkLogConfig getSdkLogConfig() {
        return sdkLogConfig;
    }

    /**
     * 设置日志配置
     *
     * @param sdkLogConfig 日志配置
     */
    public void setSdkLogConfig(SdkLogConfig sdkLogConfig) {
        this.sdkLogConfig = sdkLogConfig;
    }

    /**
     * 获取服务端信息
     *
     * @return 服务端信息
     */
    public ServerInfo getServerInfo() {
        return serverInfo;
    }

    /**
     * 设置服务端信息
     *
     * @param serverInfo 服务端信息
     */
    public void setServerInfo(ServerInfo serverInfo) {
        this.serverInfo = serverInfo;
    }

    /**
     * 获取关键文件信息
     *
     * @return 关键文件信息
     */
    public KeyFileInfo getKeyFileInfo() {
        return keyFileInfo;
    }

    /**
     * 设置关键文件信息
     *
     * @param keyFileInfo 关键文件信息
     */
    public void setKeyFileInfo(KeyFileInfo keyFileInfo) {
        this.keyFileInfo = keyFileInfo;
    }

    /**
     * 获取TLS配置
     *
     * @return TLS配置
     */
    public TLSConfig getTlsConfig() {
        return tlsConfig;
    }

    /**
     * 设置TLS配置
     *
     * @param tlsConfig TLS配置
     */
    public void setTlsConfig(TLSConfig tlsConfig) {
        this.tlsConfig = tlsConfig;
    }

    @Override
    public String toString() {
        return "AISFClientParam{appContext=" + (appContext != null ? "set" : "null")
                + ", deviceInfo=" + deviceInfo
                + ", serverInfo=" + serverInfo
                + ", keyFileInfo=" + keyFileInfo
                + ", sdkLogConfig=" + sdkLogConfig
                + ", sdkRoleName=" + sdkRoleName
                + ", tlsConfig=" + tlsConfig + "}";
    }
}
