/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.app.model;

/**
 * 启动应用选项
 *
 * <p>封装启动应用所需的全部参数，替代多参数方法，提高代码可读性和可扩展性。</p>
 *
 * <h3>使用示例</h3>
 * <pre>
 * LaunchAppOptions options = new LaunchAppOptions("com.example.app")
 *     .setLaunchFlags(1)
 *     .setMaxLaunchRetry(3)
 *     .setMaxReadyCheckRetry(10)
 *     .setReadyCheckIntervalMs(500);
 * boolean success = appManager.launchApp(options);
 * </pre>
 *
 * @since 2026-04-19
 */
public class LaunchAppOptions {

    /** 应用包名或 packageName/ActivityName 格式 */
    private String appPkgName;

    /** 应用显示名称（非包名，用于展示） */
    private String appName;

    /** 启动标志（0/1/2/3） */
    private int launchFlags;

    /** 拉起最大重试次数 */
    private int maxLaunchRetry;

    /** 就绪检查最大重试次数 */
    private int maxReadyCheckRetry;

    /** 就绪检查间隔（毫秒） */
    private long readyCheckIntervalMs;

    /**
     * 构造方法（使用默认值）
     *
     * <p>默认值：</p>
     * <ul>
     *   <li>launchFlags: 0</li>
     *   <li>maxLaunchRetry: 2</li>
     *   <li>maxReadyCheckRetry: 8</li>
     *   <li>readyCheckIntervalMs: 1000</li>
     * </ul>
     *
     * @param appPkgName 应用包名或 packageName/ActivityName 格式
     */
    public LaunchAppOptions(String appPkgName) {
        this.appPkgName = appPkgName;
        this.launchFlags = 0;
        this.maxLaunchRetry = 2;
        this.maxReadyCheckRetry = 8;
        this.readyCheckIntervalMs = 1000L;
    }

    /**
     * 获取应用包名
     *
     * @return 应用包名
     */
    public String getAppPkgName() {
        return appPkgName;
    }

    /**
     * 设置应用包名
     *
     * @param appPkgName 应用包名
     * @return 自身引用，用于链式调用
     */
    public LaunchAppOptions setAppPkgName(String appPkgName) {
        this.appPkgName = appPkgName;
        return this;
    }

    /**
     * 获取应用显示名称
     *
     * <p>用于展示的应用名称，非包名。</p>
     *
     * @return 应用显示名称
     */
    public String getAppName() {
        return appName;
    }

    /**
     * 设置应用显示名称
     *
     * <p>用于展示的应用名称，非包名。</p>
     *
     * @param appName 应用显示名称
     * @return 自身引用，用于链式调用
     */
    public LaunchAppOptions setAppName(String appName) {
        this.appName = appName;
        return this;
    }

    /**
     * 获取启动标志
     *
     * @return 启动标志
     */
    public int getLaunchFlags() {
        return launchFlags;
    }

    /**
     * 设置启动标志
     *
     * <p>启动标志映射关系：</p>
     * <ul>
     *   <li>0 -> FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK</li>
     *   <li>1 -> FLAG_ACTIVITY_NEW_TASK</li>
     *   <li>2 -> FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_MULTIPLE_TASK</li>
     *   <li>3 -> FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP</li>
     *   <li>其他 -> 透传原值</li>
     * </ul>
     *
     * @param launchFlags 启动标志
     * @return 自身引用，用于链式调用
     */
    public LaunchAppOptions setLaunchFlags(int launchFlags) {
        this.launchFlags = launchFlags;
        return this;
    }

    /**
     * 获取拉起最大重试次数
     *
     * @return 拉起最大重试次数
     */
    public int getMaxLaunchRetry() {
        return maxLaunchRetry;
    }

    /**
     * 设置拉起最大重试次数
     *
     * @param maxLaunchRetry 拉起最大重试次数
     * @return 自身引用，用于链式调用
     */
    public LaunchAppOptions setMaxLaunchRetry(int maxLaunchRetry) {
        this.maxLaunchRetry = maxLaunchRetry;
        return this;
    }

    /**
     * 获取就绪检查最大重试次数
     *
     * @return 就绪检查最大重试次数
     */
    public int getMaxReadyCheckRetry() {
        return maxReadyCheckRetry;
    }

    /**
     * 设置就绪检查最大重试次数
     *
     * @param maxReadyCheckRetry 就绪检查最大重试次数
     * @return 自身引用，用于链式调用
     */
    public LaunchAppOptions setMaxReadyCheckRetry(int maxReadyCheckRetry) {
        this.maxReadyCheckRetry = maxReadyCheckRetry;
        return this;
    }

    /**
     * 获取就绪检查间隔（毫秒）
     *
     * @return 就绪检查间隔（毫秒）
     */
    public long getReadyCheckIntervalMs() {
        return readyCheckIntervalMs;
    }

    /**
     * 设置就绪检查间隔（毫秒）
     *
     * @param readyCheckIntervalMs 就绪检查间隔（毫秒）
     * @return 自身引用，用于链式调用
     */
    public LaunchAppOptions setReadyCheckIntervalMs(long readyCheckIntervalMs) {
        this.readyCheckIntervalMs = readyCheckIntervalMs;
        return this;
    }

    @Override
    public String toString() {
        return "LaunchAppOptions{appPkgName=" + appPkgName + ", appName=" + appName
                + ", launchFlags=" + launchFlags + ", maxLaunchRetry=" + maxLaunchRetry
                + ", maxReadyCheckRetry=" + maxReadyCheckRetry + ", readyCheckIntervalMs=" + readyCheckIntervalMs + "}";
    }
}
