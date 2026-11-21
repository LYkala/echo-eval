/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.impl;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.base.DeviceInfo;
import com.huawei.aisfsdkv2.apiimpl.login.LoginApiImpl;
import com.huawei.aisfsdkv2.login.auth.TokenManager;
/**
 * Facade 层公共工具类
 *
 * <p>统一提供各 FacadeImpl 构造请求时所需的公共 Header 字段名常量、取值及 URL 格式化，
 * 避免各 Facade 实现类中重复定义相同的常量和 getter 方法。
 */
class FacadeHelper {

    static final String HEADER_APP_NAME = "AppName";
    static final String HEADER_DEVICE_ID = "DeviceId";
    static final String HEADER_DEVICE_CATEGORY = "DeviceCategory";
    static final String HEADER_UUID = "UUID";
    static final String HEADER_TOKEN = "Token";
    static final String HEADER_USER_ID = "userId";
    static final String HEADER_AUTH_VERSION = "AuthVersion";
    static final String HEADER_USER_NAME="UserName";
    private static final String AUTH_VERSION_VALUE = "1";
    private static final String HTTPS_PREFIX = "https://";
    private static final int HTTPS_TO_HTTP_DELETE_INDEX = 4;

    private FacadeHelper() {
    }

    /**
     * 获取应用名称，用于填充 HTTP 头 AppName
     *
     * @return AppName，设备信息为空时返回空字符串
     */
    static String getAppName() {
        DeviceInfo deviceInfo = AISFSDKClient.getBaseApi().getDeviceInfo();
        return deviceInfo != null ? deviceInfo.getAppName() : "";
    }

    /**
     * 获取设备 ID（登录标识），用于填充 HTTP 头 DeviceId
     *
     * @return 设备标识 ID，未登录时返回空字符串
     */
    static String getDeviceId() {
        String identifyId = LoginApiImpl.getInstance().getLoginInfo().getIdentifyId();
        return identifyId != null ? identifyId : "";
    }

    /**
     * 获取设备类别，用于填充 HTTP 头 DeviceCategory
     *
     * @return 设备类别值，信息缺失时默认返回 "1"
     */
    static String getDeviceCategory() {
        DeviceInfo deviceInfo = AISFSDKClient.getBaseApi().getDeviceInfo();
        if (deviceInfo != null && deviceInfo.getDeviceType() != null) {
            return deviceInfo.getDeviceType().getValue();
        }
        return "1";
    }

    /**
     * 获取设备类别，用于填充 HTTP 头 DeviceCategory
     *
     * @return 设备类别值，信息缺失时默认返回 "1"
     */
    static String getDeviceCategoryStr() {
        DeviceInfo deviceInfo = AISFSDKClient.getBaseApi().getDeviceInfo();
        if (deviceInfo != null && deviceInfo.getDeviceType() != null) {
            return deviceInfo.getDeviceType().getName();
        }
        return "";
    }

    /**
     * 获取设备 UUID，用于填充 HTTP 头 UUID
     *
     * @return 设备 UUID，设备信息为空时返回空字符串
     */
    static String getDeviceUuid() {
        DeviceInfo deviceInfo = AISFSDKClient.getBaseApi().getDeviceInfo();
        return deviceInfo != null ? deviceInfo.getDeviceUuid() : "";
    }

    /**
     * 获取 ACM Token，用于填充 HTTP 头 Token
     *
     * @return ACM Token，未获取到时返回空字符串
     */
    static String getAcmToken() {
        String token = TokenManager.getInstance().getCurrentToken();
        return token != null ? token : "";
    }

    /**
     * 获取当前登录用户名，用于填充 HTTP 头 userId
     *
     * @return 用户名，未登录时返回空字符串
     */
    static String getUserName() {
        String username = LoginApiImpl.getInstance().getLoginInfo().getUsername();
        return username != null ? username : "";
    }

    /**
     * 获取鉴权版本号，用于填充 HTTP 头 AuthVersion
     *
     * @return 鉴权版本号
     */
    static String getAuthVersion() {
        return AUTH_VERSION_VALUE;
    }

    /**
     * 格式化 URL，根据服务器认证模式将 https 降级为 http
     *
     * <p>对外协议约定使用 HTTPS，但当服务器认证模式为非 TLS 时，
     * 内部实际请求需要降级为 HTTP。</p>
     *
     * @param url 原始 URL（应为 https:// 开头）
     * @return 格式化后的 URL，非 TLS 模式下返回 http:// 开头的 URL
     */
    static String formatUrl(String url) {
        // 暂不处理
        return url;
    }
}
