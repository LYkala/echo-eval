/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 输入文本参数
 *
 * <p>用于向当前焦点的输入框输入文本</p>
 *
 * <p><b>参数说明：</b>
 * <ul>
 *   <li>StartX/StartY: 输入位置坐标（预留，当前未使用）</li>
 *   <li>InputText: 要输入的文本内容</li>
 *   <li>NeedSearch: 是否需要自动执行搜索（按回车）</li>
 *   <li>NeedScreenShot: 是否需要截图上传</li>
 *   <li>MaxScreenshotTime: 等待截图的最大时间（秒）</li>
 * </ul>
 * </p>
 */
public class InputTextReqParam extends GuiBaseParams {
    /** X坐标（像素），默认0，预留字段当前未使用 */
    private int startX = 0;
    /** Y坐标（像素），默认0，预留字段当前未使用 */
    private int startY = 0;
    /** 输入文本内容，默认空字符串 */
    private String inputText = "";
    /** 是否需要自动执行搜索（按回车），默认false */
    private boolean needSearch = false;
    /** 是否需要截图，默认true */
    private boolean needScreenShot = true;
    /** 最大截图等待时间（秒），默认2秒 */
    private int maxScreenshotTime = 2;

    /**
     * 默认构造方法
     */
    public InputTextReqParam() {
    }

    /**
     * 带参数的构造方法
     *
     * @param text 输入文本
     */
    public InputTextReqParam(String text) {
        this.inputText = text;
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
     * 获取输入文本
     *
     * @return 输入文本
     */
    public String getInputText() {
        return inputText;
    }

    /**
     * 设置输入文本
     *
     * @param inputText 输入文本
     */
    public void setInputText(String inputText) {
        this.inputText = inputText;
    }

    /**
     * 是否需要自动执行搜索（按回车）
     *
     * @return true 需要执行搜索
     */
    public boolean isNeedSearch() {
        return needSearch;
    }

    /**
     * 设置是否需要自动执行搜索
     *
     * @param needSearch true表示需要执行搜索
     */
    public void setNeedSearch(boolean needSearch) {
        this.needSearch = needSearch;
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
     * <p>解析以下JSON字段：
     * <ol>
     *   <li>StartX: X坐标，默认0</li>
     *   <li>StartY: Y坐标，默认0</li>
     *   <li>InputText: 输入文本，默认空</li>
     *   <li>NeedSearch: 是否需要搜索，默认false</li>
     *   <li>NeedScreenShot: 是否需要截图，默认true</li>
     *   <li>MaxScreenshotTime: 最大等待时间（秒），默认2</li>
     * </ol>
     * </p>
     *
     * @param json JSON对象
     * @return 自身引用，用于链式调用
     */
    @Override
    public InputTextReqParam parseJson(JSONObject json) {
        this.startX = json.optInt("StartX", 0);
        this.startY = json.optInt("StartY", 0);
        this.inputText = json.optString("InputText", "");
        this.needSearch = json.optBoolean("NeedSearch", false);
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
        json.put("InputText", inputText != null ? inputText : "");
        json.put("NeedSearch", needSearch);
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
            + ",\"InputText\":\"" + inputText + "\""
            + ",\"NeedSearch\":" + needSearch
            + ",\"NeedScreenShot\":" + needScreenShot
            + ",\"MaxScreenshotTime\":" + maxScreenshotTime + "}";
    }
}
