/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model;

import com.huawei.aisfsdkv2.util.StringUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * 会话数据类
 *
 * <p>对应会话消息JSON中的 Data 部分</p>
 */
public class SessionData {
    private String messageType;
    private String userInputText;
    private String userSpecifiedAgent;
    private String userInputObject;
    private List<String> userIntent;
    private String voiceId;

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
     * 获取用户输入文本
     *
     * @return 用户输入文本
     */
    public String getUserInputText() {
        return userInputText;
    }

    /**
     * 设置用户输入文本
     *
     * @param userInputText 用户输入文本
     */
    public void setUserInputText(String userInputText) {
        this.userInputText = userInputText;
    }

    /**
     * 获取用户指定的Agent
     *
     * @return Agent标识
     */
    public String getUserSpecifiedAgent() {
        return userSpecifiedAgent;
    }

    /**
     * 设置用户指定的Agent
     *
     * @param userSpecifiedAgent Agent标识
     */
    public void setUserSpecifiedAgent(String userSpecifiedAgent) {
        this.userSpecifiedAgent = userSpecifiedAgent;
    }

    /**
     * 获取用户输入对象
     *
     * @return 用户输入对象JSON字符串
     */
    public String getUserInputObject() {
        return userInputObject;
    }

    /**
     * 设置用户输入对象
     *
     * @param userInputObject 用户输入对象JSON字符串
     */
    public void setUserInputObject(String userInputObject) {
        this.userInputObject = userInputObject;
    }

    /**
     * 获取用户意图列表
     *
     * @return 用户意图列表
     */
    public List<String> getUserIntent() {
        return userIntent;
    }

    /**
     * 设置用户意图列表
     *
     * @param userIntent 用户意图列表
     */
    public void setUserIntent(List<String> userIntent) {
        this.userIntent = userIntent;
    }

    /**
     * 获取语音ID
     *
     * @return 语音ID
     */
    public String getVoiceId() {
        return voiceId;
    }

    /**
     * 设置语音ID
     *
     * @param voiceId 语音ID
     */
    public void setVoiceId(String voiceId) {
        this.voiceId = voiceId;
    }

    /**
     * 转换为JSON对象
     *
     * <p>输出规则：</p>
     * <ul>
     *   <li>输出 MessageType</li>
     *   <li>输出 UserInputText（不为空时）</li>
     *   <li>输出 UserSpecifiedAgent（不为空时）</li>
     *   <li>输出 UserIntent（不为空时）</li>
     *   <li>输出 UserInputObject（不为空时）</li>
     * </ul>
     *
     * @return JSON对象
     * @throws JSONException JSON异常
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("MessageType", messageType != null ? messageType : "");
        if (userInputText != null && !userInputText.isEmpty()) {
            json.put("UserInputText", userInputText);
        }
        if (userSpecifiedAgent != null && !userSpecifiedAgent.isEmpty()) {
            json.put("UserSpecifiedAgent", userSpecifiedAgent);
        }
        if (userIntent != null && !userIntent.isEmpty()) {
            json.put("UserIntent", StringUtil.joiner(userIntent, "#"));
        }
        if (userInputObject != null && !userInputObject.isEmpty()) {
            json.put("UserInputObject", userInputObject);
        }
        return json;
    }
}
