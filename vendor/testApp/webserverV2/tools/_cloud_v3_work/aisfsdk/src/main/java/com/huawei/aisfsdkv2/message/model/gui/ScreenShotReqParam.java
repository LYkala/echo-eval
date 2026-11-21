/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 截图参数
 *
 * <p>用于控制截图行为的参数</p>
 */
public class ScreenShotReqParam extends GuiBaseParams {
    private int maxScreenshotTime = 2;

    /**
     * 默认构造方法
     */
    public ScreenShotReqParam() {
    }

    /**
     * 带最大截图时间的构造方法
     *
     * @param maxTime 最大截图时间（秒）
     */
    public ScreenShotReqParam(int maxTime) {
        this.maxScreenshotTime = maxTime;
    }

    /**
     * 获取最大截图时间
     *
     * @return 最大截图时间（秒）
     */
    public int getMaxScreenshotTime() {
        return maxScreenshotTime;
    }

    /**
     * 设置最大截图时间
     *
     * @param maxScreenshotTime 最大截图时间（秒）
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
    public ScreenShotReqParam parseJson(JSONObject json) {
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
        json.put("MaxScreenshotTime", maxScreenshotTime);
        return json;
    }
}
