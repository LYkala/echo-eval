/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.base.DeviceInfo;
import com.huawei.aisfsdkv2.util.RandomUtil;
import com.huawei.aisfsdkv2.util.TimeUtil;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 登出消息信息类
 *
 * <p>用于封装登出消息。</p>
 */
public class LogoutMsgInfo {
    private HeaderMsgInfo header;
    private LogoutData data;

    /**
     * 默认构造方法
     */
    public LogoutMsgInfo() {
        this.header = new HeaderMsgInfo();
        this.data = new LogoutData();
        // Header默认值（与LocationMsgInfo类似，但可自定义）
        this.header.setMessageType("LifecycleManagement");
        this.header.setSender(AISFSDKClient.getBaseApi().getSdkRoleName());
        this.header.setEventTimeStamp(TimeUtil.getEventTimestamp());
        this.header.setUserName(getUsername());
        this.header.setDeviceId(getIdentifyId());
        DeviceInfo deviceInfo = getDeviceInfo();
        if (deviceInfo != null) {
            this.header.setDeviceCategory(deviceInfo.getDeviceType().getValue());
            this.header.setDeviceCategoryStr(deviceInfo.getDeviceType().getName());
        }
        this.header.setReceiver("SystemAgent");
        this.header.setIdentifyId(getIdentifyId());
        this.header.setClientSessionId(RandomUtil.getSessionId());
        this.header.setUuid(getDeviceUuidFromBaseApi());
        this.header.setDeviceUUID(getDeviceUuidFromBaseApi());
        // Data默认值
        this.data.setMessageType("Logout");
    }

    private String getUsername() {
        try {
            if (AISFSDKClient.getLoginApi() != null && AISFSDKClient.getLoginApi().getLoginInfo() != null) {
                return AISFSDKClient.getLoginApi().getLoginInfo().getUsername();
            }
        } catch (Exception e) {}
        return "";
    }

    private String getIdentifyId() {
        try {
            if (AISFSDKClient.getLoginApi() != null && AISFSDKClient.getLoginApi().getLoginInfo() != null) {
                return AISFSDKClient.getLoginApi().getLoginInfo().getIdentifyId();
            }
        } catch (Exception e) {}
        return "";
    }

    private DeviceInfo getDeviceInfo() {
        try {
            if (AISFSDKClient.getBaseApi() != null) {
                return AISFSDKClient.getBaseApi().getDeviceInfo();
            }
        } catch (Exception e) {}
        return null;
    }

    private String getDeviceUuidFromBaseApi() {
        try {
            if (AISFSDKClient.getBaseApi() != null && AISFSDKClient.getBaseApi().getDeviceInfo() != null) {
                return AISFSDKClient.getBaseApi().getDeviceInfo().getDeviceUuid();
            }
        } catch (Exception e) {}
        return "";
    }

    /**
     * 获取消息头
     *
     * @return 消息头
     */
    public HeaderMsgInfo getHeader() {
        return header;
    }

    /**
     * 设置消息头
     *
     * @param header 消息头
     */
    public void setHeader(HeaderMsgInfo header) {
        this.header = header;
    }

    /**
     * 获取数据
     *
     * @return 数据
     */
    public LogoutData getData() {
        return data;
    }

    /**
     * 设置数据
     *
     * @param data 数据
     */
    public void setData(LogoutData data) {
        this.data = data;
    }

    @Override
    public String toString() {
        try {
            JSONObject root = new JSONObject();
            root.put("Header", header.toJson());
            root.put("Data", data.toJson());
            return root.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }
}
