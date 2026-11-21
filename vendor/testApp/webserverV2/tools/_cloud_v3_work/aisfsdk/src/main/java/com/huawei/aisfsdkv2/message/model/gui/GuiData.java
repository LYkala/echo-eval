/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * GUI消息数据域
 *
 * <p>对应JSON中的 Data 部分</p>
 */
public class GuiData {
    private String messageType = "DataExchange";
    private String userOutputText = "";
    private Map<String, Object> extParam = new HashMap<>();

    /**
     * 获取消息类型
     *
     * @return 消息类型字符串
     */
    public String getMessageType() {
        return messageType;
    }

    /**
     * 设置消息类型
     *
     * @param messageType 消息类型
     */
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    /**
     * 获取用户输出文本
     *
     * @return 用户输出文本
     */
    public String getUserOutputText() {
        return userOutputText;
    }

    /**
     * 设置用户输出文本
     *
     * @param userOutputText 用户输出文本
     */
    public void setUserOutputText(String userOutputText) {
        this.userOutputText = userOutputText;
    }

    /**
     * 获取扩展参数Map
     *
     * @return 扩展参数Map
     */
    public Map<String, Object> getExtParam() {
        return extParam;
    }

    /**
     * 设置扩展参数Map
     *
     * @param extParam 扩展参数Map
     */
    public void setExtParam(Map<String, Object> extParam) {
        this.extParam = extParam != null ? extParam : new HashMap<>();
    }

    /**
     * 转换为JSON对象
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("MessageType", messageType != null ? messageType : "");
        if (userOutputText != null && !userOutputText.isEmpty()) {
            json.put("UserOutputText", userOutputText);
        }
        for (Map.Entry<String, Object> entry : extParam.entrySet()) {
            json.put(entry.getKey(), entry.getValue());
        }
        return json;
    }

    /**
     * 从JSON对象解析
     */
    public static GuiData fromJson(JSONObject json) throws JSONException {
        GuiData data = new GuiData();
        data.setMessageType(json.optString("MessageType", "DataExchange"));
        if (json.has("UserOutputText")) {
            data.setUserOutputText(json.optString("UserOutputText"));
        }
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            data.getExtParam().put(key, json.get(key));
        }
        return data;
    }
}
