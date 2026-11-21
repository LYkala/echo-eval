/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 长按参数
 *
 * <p>用于在指定坐标位置执行长按操作</p>
 *
 * <p><b>参数说明：</b>
 * <ul>
 *   <li>StartX/StartY: 长按坐标位置</li>
 *   <li>Duration: 长按持续时间（毫秒）</li>
 *   <li>NeedScreenShot: 是否需要截图上传</li>
 *   <li>MaxScreenshotTime: 等待截图的最大时间（秒）</li>
 * </ul>
 * </p>
 */
public class LongClickReqParam extends GuiBaseParams {
    /** X坐标（像素），默认-1表示未设置 */
    private int startX = -1;
    /** Y坐标（像素），默认-1表示未设置 */
    private int startY = -1;
    /** 长按持续时间（毫秒），默认1000ms */
    private int duration = 1000;
    /** 是否需要截图，默认true */
    private boolean needScreenShot = true;
    /** 最大截图等待时间（秒），默认2秒 */
    private int maxScreenshotTime = 2;

    /**
     * 默认构造方法
     */
    public LongClickReqParam() {
    }

    /**
     * 带参数的构造方法
     *
     * @param x        X坐标
     * @param y        Y坐标
     * @param duration 长按持续时间（毫秒）
     */
    public LongClickReqParam(int x, int y, int duration) {
        this.startX = x;
        this.startY = y;
        this.duration = duration;
    }

    /**
     * 获取X坐标
     *
     * @return X坐标
     */
    public int getStartX() {
        return startX;
    }

    /**
     * 设置X坐标
     *
     * @param startX X坐标
     */
    public void setStartX(int startX) {
        this.startX = startX;
    }

    /**
     * 获取Y坐标
     *
     * @return Y坐标
     */
    public int getStartY() {
        return startY;
    }

    /**
     * 设置Y坐标
     *
     * @param startY Y坐标
     */
    public void setStartY(int startY) {
        this.startY = startY;
    }

    /**
     * 获取长按持续时间
     *
     * @return 长按持续时间（毫秒）
     */
    public int getDuration() {
        return duration;
    }

    /**
     * 设置长按持续时间
     *
     * @param duration 长按持续时间（毫秒）
     */
    public void setDuration(int duration) {
        this.duration = duration;
    }

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
    public LongClickReqParam parseJson(JSONObject json) {
        this.startX = json.optInt("StartX", -1);
        this.startY = json.optInt("StartY", -1);
        this.duration = json.optInt("Duration", 1000);
        this.needScreenShot = json.optBoolean("NeedScreenShot", true);
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
        json.put("StartX", startX);
        json.put("StartY", startY);
        json.put("Duration", duration);
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
        return "{\"StartX\":" + startX + ",\"StartY\":" + startY
            + ",\"Duration\":" + duration
            + ",\"NeedScreenShot\":" + needScreenShot
            + ",\"MaxScreenshotTime\":" + maxScreenshotTime + "}";
    }
}
