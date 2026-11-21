/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.base;

import com.huawei.aisfsdkv2.api.AISFSDKClient;

import android.content.Context;

/**
 * 设备信息
 *
 * @author l00504561
 * @since 2026-02-08
 */
public class DeviceInfo {

    /**
     * 设备唯一标识
     */
    private String deviceUuid;

    /**
     * 设备类型：物理机、云手机、算力主机、电信物理机
     * 默认为物理手机
     */
    private DeviceTypeEnum deviceType = DeviceTypeEnum.CLOUD_PHONE;

    /**
     * APK应用名
     */
    private String appName;

    /**
     * 云手机实例ID
     */
    private String cloudPhoneInstanceId;

    /**
     * 获取设备唯一标识
     *
     * @return 设备唯一标识字符串
     */
    public String getDeviceUuid() {
        return deviceUuid;
    }

    /**
     * 设置设备唯一标识
     *
     * @param deviceUuid 设备唯一标识
     */
    public void setDeviceUuid(String deviceUuid) {
        this.deviceUuid = deviceUuid;
    }

    /**
     * 获取设备类型
     *
     * @return 设备类型枚举
     */
    public DeviceTypeEnum getDeviceType() {
        return deviceType;
    }

    /**
     * 设置设备类型
     *
     * @param deviceType 设备类型枚举
     */
    public void setDeviceType(DeviceTypeEnum deviceType) {
        this.deviceType = deviceType;
    }

    /**
     * 获取应用名称
     * 如果未设置，自动返回当前应用的包名
     *
     * @return 应用名称或包名
     */
    public String getAppName() {
        if (appName == null || appName.isEmpty()) {
            Context context = AISFSDKClient.getBaseApi().getAppContext();
            if (context != null) {
                return context.getPackageName();
            }
            return "";
        }
        return appName;
    }

    /**
     * 设置应用名称
     *
     * @param appName 应用名称
     */
    public void setAppName(String appName) {
        this.appName = appName;
    }

    /**
     * 获取云手机实例ID
     *
     * @return 云手机实例ID
     */
    public String getCloudPhoneInstanceId() {
        return cloudPhoneInstanceId;
    }

    /**
     * 设置云手机实例ID
     *
     * @param cloudPhoneInstanceId 云手机实例ID
     */
    public void setCloudPhoneInstanceId(String cloudPhoneInstanceId) {
        this.cloudPhoneInstanceId = cloudPhoneInstanceId;
    }

    @Override
    public String toString() {
        return "DeviceInfo{deviceUuid=" + deviceUuid
                + ", deviceType=" + deviceType
                + ", appName=" + appName
                + ", cloudPhoneInstanceId=" + cloudPhoneInstanceId + "}";
    }
}
