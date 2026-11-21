/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 滑动参数
 *
 * <p>用于在屏幕上执行滑动操作</p>
 *
 * <p><b>参数说明：</b>
 * <ul>
 *   <li>StartX/StartY: 起始坐标</li>
 *   <li>EndX/EndY: 结束坐标</li>
 *   <li>Duration: 滑动持续时间（毫秒）</li>
 *   <li>SleepTime: 滑动后延迟时间（毫秒）</li>
 *   <li>NeedScreenShot: 是否需要截图上传</li>
 *   <li>MaxScreenshotTime: 等待截图的最大时间（秒）</li>
 * </ul>
 * </p>
 */
public class SwipeReqParam extends GuiBaseParams {
    /** 起始X坐标，默认-1 */
    private int startX = -1;
    /** 起始Y坐标，默认-1 */
    private int startY = -1;
    /** 结束X坐标，默认-1 */
    private int endX = -1;
    /** 结束Y坐标，默认-1 */
    private int endY = -1;
    /** 滑动持续时间（毫秒），默认300ms */
    private int duration = 300;
    /** 滑动后延迟时间（毫秒），默认0 */
    private int sleepTime = 0;
    /** 是否需要截图，默认true */
    private boolean needScreenShot = true;
    /** 最大截图等待时间（秒），默认2秒 */
    private int maxScreenshotTime = 2;

    /**
     * 默认构造方法
     */
    public SwipeReqParam() {
    }

    /**
     * 带参数的构造方法
     *
     * @param startX   起始X坐标
     * @param startY   起始Y坐标
     * @param endX     结束X坐标
     * @param endY     结束Y坐标
     * @param duration 滑动持续时间（毫秒）
     */
    public SwipeReqParam(int startX, int startY, int endX, int endY, int duration) {
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.duration = duration;
    }

    /**
     * 获取起始X坐标
     *
     * @return 起始X坐标
     */
    public int getStartX() {
        return startX;
    }

    /**
     * 设置起始X坐标
     *
     * @param startX 起始X坐标
     */
    public void setStartX(int startX) {
        this.startX = startX;
    }

    /**
     * 获取起始Y坐标
     *
     * @return 起始Y坐标
     */
    public int getStartY() {
        return startY;
    }

    /**
     * 设置起始Y坐标
     *
     * @param startY 起始Y坐标
     */
    public void setStartY(int startY) {
        this.startY = startY;
    }

    /**
     * 获取结束X坐标
     *
     * @return 结束X坐标
     */
    public int getEndX() {
        return endX;
    }

    /**
     * 设置结束X坐标
     *
     * @param endX 结束X坐标
     */
    public void setEndX(int endX) {
        this.endX = endX;
    }

    /**
     * 获取结束Y坐标
     *
     * @return 结束Y坐标
     */
    public int getEndY() {
        return endY;
    }

    /**
     * 设置结束Y坐标
     *
     * @param endY 结束Y坐标
     */
    public void setEndY(int endY) {
        this.endY = endY;
    }

    /**
     * 获取滑动持续时间
     *
     * @return 滑动持续时间（毫秒）
     */
    public int getDuration() {
        return duration;
    }

    /**
     * 设置滑动持续时间
     *
     * @param duration 滑动持续时间（毫秒）
     */
    public void setDuration(int duration) {
        this.duration = duration;
    }

    /**
     * 获取滑动后延迟时间
     *
     * @return 延迟时间（毫秒）
     */
    public int getSleepTime() {
        return sleepTime;
    }

    /**
     * 设置滑动后延迟时间
     *
     * @param sleepTime 延迟时间（毫秒）
     */
    public void setSleepTime(int sleepTime) {
        this.sleepTime = sleepTime;
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
    public SwipeReqParam parseJson(JSONObject json) {
        this.startX = json.optInt("StartX", -1);
        this.startY = json.optInt("StartY", -1);
        this.endX = json.optInt("EndX", -1);
        this.endY = json.optInt("EndY", -1);
        this.duration = json.optInt("Duration", 300);
        this.sleepTime = json.optInt("SleepTime", 0);
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
        json.put("EndX", endX);
        json.put("EndY", endY);
        json.put("Duration", duration);
        json.put("SleepTime", sleepTime);
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
            + ",\"EndX\":" + endX + ",\"EndY\":" + endY
            + ",\"Duration\":" + duration
            + ",\"SleepTime\":" + sleepTime
            + ",\"NeedScreenShot\":" + needScreenShot
            + ",\"MaxScreenshotTime\":" + maxScreenshotTime + "}";
    }
}
