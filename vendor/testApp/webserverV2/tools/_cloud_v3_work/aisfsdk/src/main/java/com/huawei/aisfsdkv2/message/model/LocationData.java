/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 位置数据类
 *
 * <p>对应位置消息JSON中的 Data 部分</p>
 */
public class LocationData {
    private String messageType;
    private String latitude;
    private String longitude;
    private String cloudPhoneInstanceId;
    private boolean deleteLocationInfo;

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
     * 获取纬度
     *
     * @return 纬度
     */
    public String getLatitude() {
        return latitude;
    }

    /**
     * 设置纬度
     *
     * @param latitude 纬度
     */
    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }

    /**
     * 获取经度
     *
     * @return 经度
     */
    public String getLongitude() {
        return longitude;
    }

    /**
     * 设置经度
     *
     * @param longitude 经度
     */
    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }

    /**
     * 获取云手机实例ID
     *
     * @return 云手机实例ID
     */
    public String getCloudPhoneInstanceId() {
        return cloudPhoneInstanceId;
    }

    /**
     * 设置云手机实例ID
     *
     * @param cloudPhoneInstanceId 云手机实例ID
     */
    public void setCloudPhoneInstanceId(String cloudPhoneInstanceId) {
        this.cloudPhoneInstanceId = cloudPhoneInstanceId;
    }

    /**
     * 是否删除位置信息
     *
     * @return true表示删除位置信息
     */
    public boolean isDeleteLocationInfo() {
        return deleteLocationInfo;
    }

    /**
     * 设置是否删除位置信息
     *
     * @param deleteLocationInfo true表示删除位置信息
     */
    public void setDeleteLocationInfo(boolean deleteLocationInfo) {
        this.deleteLocationInfo = deleteLocationInfo;
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
        json.put("Latitude", latitude != null ? latitude : "");
        json.put("Longitude", longitude != null ? longitude : "");
        if (cloudPhoneInstanceId != null && !cloudPhoneInstanceId.isEmpty()) {
            json.put("instanceNum", cloudPhoneInstanceId);
            json.put("cloudPhoneInstanceId", cloudPhoneInstanceId);
        }
        json.put("deleteLocationInfo", deleteLocationInfo);
        return json;
    }

    @Override
    public String toString() {
        return "LocationData{"
            + "messageType='" + messageType + '\''
            + ", latitude='" + latitude + '\''
            + ", longitude='" + longitude + '\''
            + ", cloudPhoneInstanceId='" + cloudPhoneInstanceId + '\''
            + ", deleteLocationInfo=" + deleteLocationInfo
            + '}';
    }
}
