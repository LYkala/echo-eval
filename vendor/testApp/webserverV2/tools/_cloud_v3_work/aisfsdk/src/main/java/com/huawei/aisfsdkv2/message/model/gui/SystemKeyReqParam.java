/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 系统按键参数
 *
 * <p>用于Home/Back/Enter/Tasks等系统按键操作。</p>
 *
 * <p><b>参数说明：</b>
 * <ul>
 *   <li>NeedScreenShot: 是否需要截图上传</li>
 *   <li>MaxScreenshotTime: 等待截图的最大时间（秒）</li>
 * </ul>
 * </p>
 */
public class SystemKeyReqParam extends GuiBaseParams {
    /** 是否需要截图，默认false */
    private boolean needScreenShot = false;
    /** 最大截图等待时间（秒），默认2秒 */
    private int maxScreenshotTime = 2;

    /**
     * 是否需要截图
     *
     * @return true 需要截图并上传
     */
    public boolean isNeedScreenShot() {
        return needScreenShot;
    }

    /**
     * 设置是否需要截图
     *
     * @param needScreenShot true表示需要截图并上传
     */
    public void setNeedScreenShot(boolean needScreenShot) {
        this.needScreenShot = needScreenShot;
    }

    /**
     * 获取最大截图等待时间
     *
     * @return 最大等待时间（秒）
     */
    public int getMaxScreenshotTime() {
        return maxScreenshotTime;
    }

    /**
     * 设置最大截图等待时间
     *
     * @param maxScreenshotTime 最大等待时间（秒）
     */
    public void setMaxScreenshotTime(int maxScreenshotTime) {
        this.maxScreenshotTime = maxScreenshotTime;
    }

    /**
     * 从JSON对象解析
     *
     * @param json JSON对象
     * @return 自身引用，用于链式调用
     */
    @Override
    public SystemKeyReqParam parseJson(JSONObject json) {
        this.needScreenShot = json.optBoolean("NeedScreenShot", false);
        this.maxScreenshotTime = json.optInt("MaxScreenshotTime", 2);
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
        json.put("NeedScreenShot", needScreenShot);
        json.put("MaxScreenshotTime", maxScreenshotTime);
        return json;
    }

    /**
     * 转换为JSON格式字符串
     *
     * @return JSON格式字符串
     */
    @Override
    public String toString() {
        return "{\"NeedScreenShot\":" + needScreenShot
            + ",\"MaxScreenshotTime\":" + maxScreenshotTime + "}";
    }
}
