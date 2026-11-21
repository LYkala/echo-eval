/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.base;

import android.content.Context;

import com.huawei.aisfsdkv2.api.base.BaseApi;
import com.huawei.aisfsdkv2.api.base.DeviceInfo;
import com.huawei.aisfsdkv2.api.base.KeyFileInfo;
import com.huawei.aisfsdkv2.api.base.ServerInfo;
import com.huawei.aisfsdkv2.api.base.TLSConfig;

/**
 * base接口
 *
 * @author l00504561
 * @since 2026-02-06
 */
public class BaseApiImpl implements BaseApi {
    private static final BaseApiImpl baseApi = new BaseApiImpl();

    /**
     * SDK版本号
     */
    private static final String SDK_VERSION = "1.0.0";

    /**
     * app上下文
     */
    private volatile Context appContext;

    /**
     * 设备信息
     */
    private volatile DeviceInfo deviceInfo;

    /**
     * 关键文件信息
     */
    private volatile KeyFileInfo keyFileInfo;

    /**
     * 服务器信息
     */
    private volatile ServerInfo serverInfo;

    /**
     * SDK角色名称
     */
    private volatile String sdkRoleName;

    /**
     * TLS配置
     */
    private volatile TLSConfig tlsConfig;

    private BaseApiImpl() {
    }

    /**
     * 获取单例
     *
     * @return 获取单例
     */
    public static BaseApiImpl getInstance() {
        return baseApi;
    }

    /**
     * 获取SDK版本号
     *
     * @return SDK版本号
     */
    @Override
    public String getSdkVersion() {
        return SDK_VERSION;
    }

    @Override
    public void setAppContext(Context appContext) {
        this.appContext = appContext;
    }

    /**
     * 获取android上下文
     *
     * @return android上下文
     */
    @Override
    public Context getAppContext() {
        return this.appContext;
    }

    /**
     * 获取设备信息
     *
     * @param deviceInfo 设备信息
     */
    @Override
    public void setDeviceInfo(DeviceInfo deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    /**
     * 获取设备信息
     *
     * @return 设备信息
     */
    @Override
    public DeviceInfo getDeviceInfo() {
        return this.deviceInfo;
    }

    /**
     * 设置关键文件信息
     *
     * @param keyFileInfo 关键文件信息
     */
    @Override
    public void setKeyFileInfo(KeyFileInfo keyFileInfo) {
        this.keyFileInfo = keyFileInfo;
    }

    /**
     * 获取关键文件信息
     *
     * @return 关键文件信息
     */
    @Override
    public KeyFileInfo getKeyFileInfo() {
        return this.keyFileInfo;
    }

    /**
     * 设置服务器信息
     *
     * @param serverInfo 服务器信息
     */
    @Override
    public void setServerInfo(ServerInfo serverInfo) {
        this.serverInfo = serverInfo;
    }

    /**
     * 获取服务器信息
     *
     * @return 服务器信息
     */
    @Override
    public ServerInfo getServerInfo() {
        return this.serverInfo;
    }

    /**
     * 获取SDK角色名称
     *
     * @return SDK角色名称
     */
    @Override
    public String getSdkRoleName() {
        return sdkRoleName;
    }

    /**
     * 设置SDK角色名称
     *
     * @param sdkRoleName SDK角色名称
     */
    @Override
    public void setSdkRoleName(String sdkRoleName) {
        this.sdkRoleName = sdkRoleName;
    }

    /**
     * 设置TLS配置
     *
     * @param tlsConfig TLS配置
     */
    @Override
    public void setTLSConfig(TLSConfig tlsConfig) {
        this.tlsConfig = tlsConfig;
    }

    /**
     * 获取TLS配置
     *
     * @return TLS配置
     */
    @Override
    public TLSConfig getTLSConfig() {
        return this.tlsConfig;
    }
}
