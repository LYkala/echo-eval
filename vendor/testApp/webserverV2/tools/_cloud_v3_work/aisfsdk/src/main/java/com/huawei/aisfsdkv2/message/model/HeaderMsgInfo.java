/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 消息头部信息类
 *
 * <p>通用消息头部，包含14个标准字段。</p>
 */
public class HeaderMsgInfo {
    private String messageType;
    private String sender;
    private String receiver;
    private String eventTimeStamp;
    private String clientExField;
    private String clientSessionId;
    private String userName;
    private String deviceId;
    private String deviceCategory;
    private String deviceCategoryStr;
    private String uuid;
    private String deviceUUID;
    private String identifyId;
    private String taskId;

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
     * 获取发送者
     *
     * @return 发送者
     */
    public String getSender() {
        return sender;
    }

    /**
     * 设置发送者
     *
     * @param sender 发送者
     */
    public void setSender(String sender) {
        this.sender = sender;
    }

    /**
     * 获取接收者
     *
     * @return 接收者
     */
    public String getReceiver() {
        return receiver;
    }

    /**
     * 设置接收者
     *
     * @param receiver 接收者
     */
    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    /**
     * 获取事件时间戳
     *
     * @return 事件时间戳
     */
    public String getEventTimeStamp() {
        return eventTimeStamp;
    }

    /**
     * 设置事件时间戳
     *
     * @param eventTimeStamp 事件时间戳
     */
    public void setEventTimeStamp(String eventTimeStamp) {
        this.eventTimeStamp = eventTimeStamp;
    }

    /**
     * 获取客户端扩展字段
     *
     * @return 客户端扩展字段
     */
    public String getClientExField() {
        return clientExField;
    }

    /**
     * 设置客户端扩展字段
     *
     * @param clientExField 客户端扩展字段
     */
    public void setClientExField(String clientExField) {
        this.clientExField = clientExField;
    }

    /**
     * 获取客户端会话ID
     *
     * @return 客户端会话ID
     */
    public String getClientSessionId() {
        return clientSessionId;
    }

    /**
     * 设置客户端会话ID
     *
     * @param clientSessionId 客户端会话ID
     */
    public void setClientSessionId(String clientSessionId) {
        this.clientSessionId = clientSessionId;
    }

    /**
     * 获取用户名
     *
     * @return 用户名
     */
    public String getUserName() {
        return userName;
    }

    /**
     * 设置用户名
     *
     * @param userName 用户名
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * 获取设备ID
     *
     * @return 设备ID
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * 设置设备ID
     *
     * @param deviceId 设备ID
     */
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    /**
     * 获取设备类别
     *
     * @return 设备类别
     */
    public String getDeviceCategory() {
        return deviceCategory;
    }

    /**
     * 设置设备类别
     *
     * @param deviceCategory 设备类别
     */
    public void setDeviceCategory(String deviceCategory) {
        this.deviceCategory = deviceCategory;
    }

    /**
     * 获取设备类别字符串
     *
     * @return 设备类别字符串
     */
    public String getDeviceCategoryStr() {
        return deviceCategoryStr;
    }

    /**
     * 设置设备类别字符串
     *
     * @param deviceCategoryStr 设备类别字符串
     */
    public void setDeviceCategoryStr(String deviceCategoryStr) {
        this.deviceCategoryStr = deviceCategoryStr;
    }

    /**
     * 获取UUID
     *
     * @return UUID
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * 设置UUID
     *
     * @param uuid UUID
     */
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    /**
     * 获取设备UUID
     *
     * @return 设备UUID
     */
    public String getDeviceUUID() {
        return deviceUUID;
    }

    /**
     * 设置设备UUID
     *
     * @param deviceUUID 设备UUID
     */
    public void setDeviceUUID(String deviceUUID) {
        this.deviceUUID = deviceUUID;
    }

    /**
     * 获取识别ID
     *
     * @return 识别ID
     */
    public String getIdentifyId() {
        return identifyId;
    }

    /**
     * 设置识别ID
     *
     * @param identifyId 识别ID
     */
    public void setIdentifyId(String identifyId) {
        this.identifyId = identifyId;
    }

    /**
     * 获取任务ID
     *
     * @return 任务ID
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * 设置任务ID
     *
     * @param taskId 任务ID
     */
    public void setTaskId(String taskId) {
        this.taskId = taskId;
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
        json.put("Sender", sender != null ? sender : "");
        json.put("Receiver", receiver != null ? receiver : "");
        json.put("EventTimeStamp", eventTimeStamp != null ? eventTimeStamp : "");
        json.put("ClientExField", clientExField != null ? clientExField : "");
        json.put("ClientSessionId", clientSessionId != null ? clientSessionId : "");
        json.put("UserName", userName != null ? userName : "");
        json.put("DeviceId", deviceId != null ? deviceId : "");
        json.put("DeviceCategory", deviceCategory != null ? deviceCategory : "");
        json.put("DeviceCategoryStr", deviceCategoryStr != null ? deviceCategoryStr : "");
        json.put("UUID", uuid != null ? uuid : "");
        json.put("DeviceUUID", deviceUUID != null ? deviceUUID : "");
        json.put("IdentifyId", identifyId != null ? identifyId : "");
        json.put("TaskID", taskId != null ? taskId : "");
        return json;
    }
}
