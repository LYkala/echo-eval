/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 拉起APP参数
 *
 * <p>用于启动指定的应用</p>
 *
 * <h3>字段映射说明（解决历史命名歧义）</h3>
 * <ul>
 *   <li><b>appName</b> (Java) ↔ <b>AppShowName</b> (JSON)：应用显示名称</li>
 *   <li><b>appPkgName</b> (Java) ↔ <b>AppName</b> (JSON)：应用包名</li>
 * </ul>
 */
public class LaunchAppReqParam extends GuiBaseParams {
    /** 应用显示名称，JSON序列化为 AppShowName */
    private String appName;
    /** 应用包名，JSON序列化为 AppName */
    private String appPkgName;
    private int launchFlag;
    private int screenShotMaxTime;

    /**
     * 默认构造方法
     */
    public LaunchAppReqParam() {
        this.launchFlag = 0;
        this.screenShotMaxTime = 8;
    }

    /**
     * 获取应用显示名称
     *
     * <p>对应JSON字段：AppShowName</p>
     *
     * @return 应用显示名称
     */
    public String getAppName() {
        return appName;
    }

    /**
     * 设置应用显示名称
     *
     * <p>对应JSON字段：AppShowName</p>
     *
     * @param appName 应用显示名称
     */
    public void setAppName(String appName) {
        this.appName = appName;
    }

    /**
     * 获取应用包名
     *
     * <p>对应JSON字段：AppName（历史遗留命名）</p>
     *
     * @return 应用包名
     */
    public String getAppPkgName() {
        return appPkgName;
    }

    /**
     * 设置应用包名
     *
     * <p>对应JSON字段：AppName（历史遗留命名）</p>
     *
     * @param appPkgName 应用包名
     */
    public void setAppPkgName(String appPkgName) {
        this.appPkgName = appPkgName;
    }

    /**
     * 获取启动标志
     *
     * @return 启动标志
     */
    public int getLaunchFlag() {
        return launchFlag;
    }

    /**
     * 设置启动标志
     *
     * @param launchFlag 启动标志
     */
    public void setLaunchFlag(int launchFlag) {
        this.launchFlag = launchFlag;
    }

    /**
     * 获取最大截图等待秒数
     *
     * @return 最大截图等待秒数
     */
    public int getScreenShotMaxTime() {
        return screenShotMaxTime;
    }

    /**
     * 设置最大截图等待秒数
     *
     * @param screenShotMaxTime 最大截图等待秒数
     */
    public void setScreenShotMaxTime(int screenShotMaxTime) {
        this.screenShotMaxTime = screenShotMaxTime;
    }

    /**
     * 从JSON对象解析
     *
     * <p>字段映射：</p>
     * <ul>
     *   <li>AppShowName → appName（应用显示名称）</li>
     *   <li>AppName → appPkgName（应用包名，历史遗留命名）</li>
     * </ul>
     *
     * @param json JSON对象
     * @return 自身引用，用于链式调用
     */
    @Override
    public LaunchAppReqParam parseJson(JSONObject json) {
        this.appName = json.optString("AppShowName", "");
        this.appPkgName = json.optString("AppName", "");
        this.launchFlag = json.optInt("LaunchFlag", 0);
        this.screenShotMaxTime = json.optInt("MaxScreenshotTime", 8);
        return this;
    }

    /**
     * 转换为JSON对象
     *
     * <p>字段映射：</p>
     * <ul>
     *   <li>appName → AppShowName（应用显示名称）</li>
     *   <li>appPkgName → AppName（应用包名，历史遗留命名）</li>
     * </ul>
     *
     * @return JSON对象
     * @throws JSONException JSON异常
     */
    @Override
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("AppShowName", appName != null ? appName : "");
        json.put("AppName", appPkgName != null ? appPkgName : "");
        json.put("LaunchFlag", launchFlag);
        json.put("MaxScreenshotTime", screenShotMaxTime);
        return json;
    }

    @Override
    public String toString() {
        return "LaunchAppReqParam{appName=" + appName + ", appPkgName=" + appPkgName
                + ", launchFlag=" + launchFlag + ", screenShotMaxTime=" + screenShotMaxTime + "}";
    }
}