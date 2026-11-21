/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 登出数据类
 *
 * <p>对应登出消息JSON中的 Data 部分</p>
 */
public class LogoutData {
    private String messageType;

    /**
     * 获取消息类型
     *
     * @return 消息类型
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
     * 转换为JSON对象
     *
     * @return JSON对象
     * @throws JSONException JSON异常
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("MessageType", messageType != null ? messageType : "");
        return json;
    }
}
