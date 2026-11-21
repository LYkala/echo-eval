/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * 上传截图响应参数
 *
 * <p>封装截图上传成功后的响应信息。
 * 包含图片URL、分辨率、屏幕状态等信息。</p>
 */
public class UploadScreenshotRespParam extends GuiBaseParams {
    /** 屏幕宽度 */
    private int resolutionW;

    /** 屏幕高度 */
    private int resolutionH;

    /** 是否重试截图 */
    private boolean isReScreenShot;

    /** 屏幕状态计数 */
    private long changeCnt;

    /** 截图时刻屏幕最上层应用包名 */
    private String foregroundAppPackageName;

    /** 点击事件黑名单拦截标识，true表示点击被黑名单拦截 */
    private boolean isBlackAction = false;

    /** 点击事件黑名单拦截关键词 */
    private List<String> matchedKeywords;

    /**
     * 获取屏幕宽度
     *
     * @return 屏幕宽度
     */
    public int getResolutionW() {
        return resolutionW;
    }

    /**
     * 设置屏幕宽度
     *
     * @param resolutionW 屏幕宽度
     */
    public void setResolutionW(int resolutionW) {
        this.resolutionW = resolutionW;
    }

    /**
     * 获取屏幕高度
     *
     * @return 屏幕高度
     */
    public int getResolutionH() {
        return resolutionH;
    }

    /**
     * 设置屏幕高度
     *
     * @param resolutionH 屏幕高度
     */
    public void setResolutionH(int resolutionH) {
        this.resolutionH = resolutionH;
    }

    /**
     * 是否重试截图
     *
     * @return true表示需要重试，false表示不需要
     */
    public boolean isReScreenShot() {
        return isReScreenShot;
    }

    /**
     * 设置是否重试截图
     *
     * @param reScreenShot true表示需要重试
     */
    public void setReScreenShot(boolean reScreenShot) {
        isReScreenShot = reScreenShot;
    }

    /**
     * 获取屏幕状态计数
     *
     * @return 屏幕状态计数
     */
    public long getChangeCnt() {
        return changeCnt;
    }

    /**
     * 设置屏幕状态计数
     *
     * @param changeCnt 屏幕状态计数
     */
    public void setChangeCnt(long changeCnt) {
        this.changeCnt = changeCnt;
    }

    /**
     * 获取截图时刻屏幕最上层应用包名
     *
     * @return 应用包名，获取失败为 null
     */
    public String getForegroundAppPackageName() {
        return foregroundAppPackageName;
    }

    /**
     * 设置截图时刻屏幕最上层应用包名
     *
     * @param foregroundAppPackageName 应用包名
     */
    public void setForegroundAppPackageName(String foregroundAppPackageName) {
        this.foregroundAppPackageName = foregroundAppPackageName;
    }

    /**
     * 设置黑名单拦截关键词
     *
     * @param matchedKeywords 黑名单拦截关键词
     */
    public void setMatchedKeywords(List<String> matchedKeywords) {
        this.matchedKeywords = matchedKeywords;
    }

    /**
     * 获取黑名单拦截关键词
     *
     * @return 屏幕状态计数
     */
    public List<String> getMatchedKeywords() {
        return matchedKeywords;
    }

    /**
     * 是否被黑名单拦截的点击事件
     *
     * @return true表示点击被黑名单拦截，false表示正常点击
     */
    public boolean isBlackAction() {
        return isBlackAction;
    }

    /**
     * 设置点击事件黑名单拦截标识
     *
     * @param blackAction true表示点击被黑名单拦截
     */
    public void setBlackAction(boolean blackAction) {
        isBlackAction = blackAction;
    }

    /**
     * 从JSON对象解析
     *
     * @param json JSON对象
     * @return 自身引用，用于链式调用
     */
    @Override
    public UploadScreenshotRespParam parseJson(JSONObject json) {
        this.resolutionW = json.optInt("ResolutionW", 0);
        this.resolutionH = json.optInt("ResolutionH", 0);
        this.isReScreenShot = json.optBoolean("IsReScreenShot", false);
        this.changeCnt = json.optLong("ChangeCnt", 0);
        this.foregroundAppPackageName = json.optString("ForegroundAppPackageName", null);
        this.isBlackAction = json.optBoolean("IsBlackAction", false);
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
        json.put("ResolutionW", resolutionW);
        json.put("ResolutionH", resolutionH);
        json.put("IsReScreenShot", isReScreenShot);
        json.put("ChangeCnt", changeCnt);
        json.put("IsBlackAction", isBlackAction);
        json.put("MATCHED_KEYWORD", matchedKeywords);
        if (foregroundAppPackageName != null) {
            json.put("ForegroundAppPackageName", foregroundAppPackageName);
            json.put("AppName", foregroundAppPackageName);
        }
        return json;
    }
}
