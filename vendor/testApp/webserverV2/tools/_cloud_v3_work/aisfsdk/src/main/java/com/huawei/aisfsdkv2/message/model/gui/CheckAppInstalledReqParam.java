/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 检查应用安装状态参数
 *
 * <p>用于检查指定包名的应用是否已安装</p>
 */
public class CheckAppInstalledReqParam extends GuiBaseParams {
    private String packageName = "";

    /**
     * 默认构造方法
     */
    public CheckAppInstalledReqParam() {
    }

    /**
     * 带包名的构造方法
     *
     * @param packageName 应用包名
     */
    public CheckAppInstalledReqParam(String packageName) {
        this.packageName = packageName;
    }

    /**
     * 获取应用包名
     *
     * @return 应用包名
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * 设置应用包名
     *
     * @param packageName 应用包名
     */
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    /**
     * 从JSON对象解析
     *
     * @param json JSON对象
     * @return 自身引用，用于链式调用
     */
    @Override
    public CheckAppInstalledReqParam parseJson(JSONObject json) {
        this.packageName = json.optString("AppName", "");
        return this;
    }

    /**
     * 转换为JSON对象
     *
     * @return JSON对象
     * @throws JSONException JSON异常
     */
    @Override
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("PackageName", packageName != null ? packageName : "");
        return json;
    }
}
