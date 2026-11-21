/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.app.model;

/**
 * 应用信息模型类
 * <p>
 * 用于存储和管理Android应用的基本信息，包括包名、应用名称、版本信息以及系统应用标识。
 * </p>
 */
public class AppInfo {

    /** 应用包名 */
    private String packageName;

    /** 应用名称 */
    private String appName;

    /** 应用版本名称 */
    private String versionName;

    /** 是否为系统应用标识 */
    private boolean isSystemApp;

    /**
     * 构造方法，创建应用信息对象
     *
     * @param packageName 应用包名
     * @param appName 应用名称
     * @param versionName 应用版本名称
     * @param isSystemApp 是否为系统应用，true表示是系统应用，false表示非系统应用
     */
    public AppInfo(String packageName, String appName, String versionName,
                   boolean isSystemApp) {
        this.packageName = packageName;
        this.appName = appName;
        this.versionName = versionName;
        this.isSystemApp = isSystemApp;
    }

    /**
     * 获取应用包名
     *
     * @return 应用包名字符串
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * 获取应用名称
     *
     * @return 应用名称字符串
     */
    public String getAppName() {
        return appName;
    }

    /**
     * 获取应用版本名称
     *
     * @return 应用版本名字符串
     */
    public String getVersionName() {
        return versionName;
    }

    /**
     * 判断是否为系统应用
     *
     * @return 如果是系统应用返回true，否则返回false
     */
    public boolean isSystemApp() {
        return isSystemApp;
    }

    @Override
    public String toString() {
        return "AppInfo{"
                + "packageName='" + packageName + '\''
                + ", appName='" + appName + '\''
                + ", versionName='" + versionName + '\''
                + ", isSystemApp=" + isSystemApp
                + '}';
    }
}
