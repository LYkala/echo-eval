/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 点击参数
 *
 * <p>用于在指定坐标位置执行点击操作，支持截图对比防抖机制</p>
 *
 * <p><b>参数说明：</b>
 * <ul>
 *   <li>StartX/StartY: 点击坐标位置</li>
 *   <li>NeedScreenShot: 是否需要截图上传</li>
 *   <li>MaxScreenshotTime: 等待截图的最大时间（秒）</li>
 * </ul>
 * </p>
 *
 * @author AI Assistant
 * @version 1.0
 * @since 2026-04-03
 */
public class ClickReqParam extends GuiBaseParams {
    /** X坐标（像素），默认-1表示未设置 */
    private int startX = -1;
    /** Y坐标（像素），默认-1表示未设置 */
    private int startY = -1;
    /** 是否需要截图，默认true */
    private boolean needScreenShot = true;
    /** 最大截图等待时间（秒），默认2秒 */
    private int maxScreenshotTime = 2;
    /** 控件黑名单列表 */
    private List<String> controlBlackList;

    /**
     * 默认构造方法
     */
    public ClickReqParam() {
    }

    /**
     * 带坐标的构造方法
     *
     * @param x X坐标
     * @param y Y坐标
     */
    public ClickReqParam(int x, int y) {
        this.startX = x;
        this.startY = y;
    }

    /**
     * 获取X坐标
     *
     * @return X坐标（像素）
     */
    public int getStartX() {
        return startX;
    }

    /**
     * 设置X坐标
     *
     * @param startX X坐标（像素）
     */
    public void setStartX(int startX) {
        this.startX = startX;
    }

    /**
     * 获取Y坐标
     *
     * @return Y坐标（像素）
     */
    public int getStartY() {
        return startY;
    }

    /**
     * 设置Y坐标
     *
     * @param startY Y坐标（像素）
     */
    public void setStartY(int startY) {
        this.startY = startY;
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
     * 获取控件黑名单列表
     *
     * @return 控件黑名单列表
     */
    public List<String> getControlBlackList() {
        return controlBlackList;
    }

    /**
     * 设置控件黑名单列表
     *
     * @param controlBlackList 控件黑名单列表
     */
    public void setControlBlackList(List<String> controlBlackList) {
        this.controlBlackList = controlBlackList;
    }

    /**
     * 从JSON对象解析
     *
     * <p>解析以下JSON字段：
     * <ol>
     *   <li>StartX: X坐标，默认-1</li>
     *   <li>StartY: Y坐标，默认-1</li>
     *   <li>NeedScreenShot: 是否需要截图，默认true</li>
     *   <li>MaxScreenshotTime: 最大等待时间（秒），默认2</li>
     * </ol>
     * </p>
     *
     * @param json JSON对象
     * @return 自身引用，用于链式调用
     */
    @Override
    public ClickReqParam parseJson(JSONObject json) {
        this.startX = json.optInt("StartX", -1);
        this.startY = json.optInt("StartY", -1);
        this.needScreenShot = json.optBoolean("NeedScreenShot", true);
        this.maxScreenshotTime = json.optInt("MaxScreenshotTime", 2);
        JSONArray blackListArray = json.optJSONArray("ControlBlackList");
        if (blackListArray != null) {
            this.controlBlackList = new ArrayList<>();
            for (int i = 0; i < blackListArray.length(); i++) {
                String item = blackListArray.optString(i, null);
                if (item != null) {
                    this.controlBlackList.add(item);
                }
            }
        }
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
        json.put("NeedScreenShot", needScreenShot);
        json.put("MaxScreenshotTime", maxScreenshotTime);
        if (controlBlackList != null && !controlBlackList.isEmpty()) {
            JSONArray blackListArray = new JSONArray();
            for (String item : controlBlackList) {
                blackListArray.put(item);
            }
            json.put("ControlBlackList", blackListArray);
        }
        return json;
    }

    /**
     * 转换为JSON格式字符串
     *
     * @return JSON格式字符串
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"StartX\":").append(startX)
            .append(",\"StartY\":").append(startY)
            .append(",\"NeedScreenShot\":").append(needScreenShot)
            .append(",\"MaxScreenshotTime\":").append(maxScreenshotTime);
        if (controlBlackList != null && !controlBlackList.isEmpty()) {
            sb.append(",\"ControlBlackList\":").append(controlBlackList);
        }
        sb.append("}");
        return sb.toString();
    }
}
