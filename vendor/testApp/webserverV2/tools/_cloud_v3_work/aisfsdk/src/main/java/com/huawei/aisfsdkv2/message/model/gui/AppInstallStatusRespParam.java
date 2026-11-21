/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 应用安装状态响应参数
 *
 * <p>用于响应应用安装状态查询，包含安装状态信息</p>
 */
public class AppInstallStatusRespParam extends GuiBaseParams {
    /** 已安装状态值 */
    public static final int INSTALLED_VALUE = 1;
    /** 未安装状态值 */
    public static final int NOT_INSTALLED_VALUE = 2;
    /** 安装状态：1=已安装，2=未安装，默认为未安装 */
    private int isInstall = NOT_INSTALLED_VALUE;

    /**
     * 获取安装状态
     *
     * @return 安装状态：{@link #INSTALLED_VALUE}=已安装，{@link #NOT_INSTALLED_VALUE}=未安装
     */
    public int getIsInstall() {
        return isInstall;
    }

    /**
     * 设置安装状态
     *
     * @param isInstall 安装状态：{@link #INSTALLED_VALUE}=已安装，{@link #NOT_INSTALLED_VALUE}=未安装
     */
    public void setIsInstall(int isInstall) {
        this.isInstall = isInstall;
    }

    /**
     * 获取安装状态描述
     *
     * <p>根据 isInstall 值实时计算返回对应的字符串描述</p>
     *
     * @return 安装状态描述：installed/uninstall
     */
    public String getInstallStatus() {
        return isInstall == INSTALLED_VALUE ? "installed" : "uninstall";
    }

    /**
     * 从JSON对象解析
     *
     * @param json JSON对象
     * @return 自身引用，用于链式调用
     */
    @Override
    public AppInstallStatusRespParam parseJson(JSONObject json) {
        this.isInstall = json.optInt("IsInstall", NOT_INSTALLED_VALUE);
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
        json.put("IsInstall", isInstall);
        json.put("InstallStatus", getInstallStatus());
        return json;
    }
}
