/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.base;

import android.content.Context;

/**
 * 基础接口
 */
public interface BaseApi {
    /**
     * 获取SDK版本号
     *
     * @return SDK版本号
     */
    String getSdkVersion();

    /**
     * 获取SDK角色名称
     *
     * @return SDK角色名称
     */
    String getSdkRoleName();

    /**
     * 设置SDK角色名称
     *
     * @param sdkRoleName SDK角色名称
     */
    void setSdkRoleName(String sdkRoleName);

    /**
     * 设置android上下文
     *
     * @param appContext 设置android上下文
     */
    void setAppContext(Context appContext);

    /**
     * 获取android上下文
     *
     * @return android上下文
     */
    Context getAppContext();

    /**
     * 获取设备信息
     *
     * @param deviceInfo 设备信息
     */
    void setDeviceInfo(DeviceInfo deviceInfo);

    /**
     * 获取设备信息
     *
     * @return 设备信息
     */
    DeviceInfo getDeviceInfo();

    /**
     * 设置关键文件信息
     *
     * @param keyFileInfo 关键文件信息
     */
    void setKeyFileInfo(KeyFileInfo keyFileInfo);

    /**
     * 获取关键文件信息
     *
     * @return 关键文件信息
     */
    KeyFileInfo getKeyFileInfo();

    /**
     * 设置服务器信息
     *
     * @param serverInfo 服务器信息
     */
    void setServerInfo(ServerInfo serverInfo);

    /**
     * 获取服务器信息
     *
     * @return 服务器信息
     */
    ServerInfo getServerInfo();

    /**
     * 设置TLS配置
     *
     * @param tlsConfig TLS配置
     */
    void setTLSConfig(TLSConfig tlsConfig);

    /**
     * 获取TLS配置
     *
     * @return TLS配置
     */
    TLSConfig getTLSConfig();
}
