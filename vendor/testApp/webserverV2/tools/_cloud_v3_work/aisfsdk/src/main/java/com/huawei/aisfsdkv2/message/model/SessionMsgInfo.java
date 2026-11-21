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
 * 会话消息信息类
 *
 * <p>用于封装会话消息。</p>
 */
public class SessionMsgInfo {
    private SessionHeaderMsgInfo header;
    private SessionData data;

    /**
     * 默认构造方法，初始化消息头和消息体的默认字段值。
     *
     * <p>Header 默认值：
     * <ul>
     *   <li>MessageType = "UserTaskManagement"</li>
     *   <li>Sender = SDK角色名</li>
     *   <li>EventTimeStamp = 当前时间戳</li>
     *   <li>UserName = 当前登录用户名</li>
     *   <li>DeviceId / IdentifyId = 当前会话标识ID</li>
     *   <li>DeviceCategory / DeviceCategoryStr = 设备类型（DeviceInfo不为null时设置）</li>
     *   <li>Receiver = "SystemAgent"</li>
     *   <li>ClientSessionId / SeqNum = 随机生成的UUID（无连字符）</li>
     *   <li>Uuid / DeviceUUID = 设备UUID</li>
     * </ul>
     *
     * <p>Data 默认值：
     * <ul>
     *   <li>MessageType = "TaskAssignment"</li>
     *   <li>UserSpecifiedAgent = "-1"</li>
     * </ul>
     */
    public SessionMsgInfo() {
        this.header = new SessionHeaderMsgInfo();
        this.data = new SessionData();
        // Header默认值
        this.header.setMessageType("UserTaskManagement");
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
        this.header.setSeqNum(RandomUtil.getUUID());
        // Data默认值
        this.data.setMessageType("TaskAssignment");
        this.data.setUserSpecifiedAgent("-1");
    }

    /**
     * 获取当前登录用户名
     *
     * <p>通过 LoginApi 获取登录信息中的用户名。
     * 防御性捕获 Throwable，确保 SDK 未初始化等极端情况下不影响消息构建。</p>
     *
     * @return 当前登录用户名，未登录或获取失败返回空字符串
     */
    private String getUsername() {
        try {
            if (AISFSDKClient.getLoginApi() != null && AISFSDKClient.getLoginApi().getLoginInfo() != null) {
                return AISFSDKClient.getLoginApi().getLoginInfo().getUsername();
            }
        } catch (Throwable ignored) {}
        return "";
    }

    /**
     * 获取当前会话标识ID（IdentifyId）
     *
     * <p>通过 LoginApi 获取登录信息中的 IdentifyId，用于标识设备与用户的绑定关系。
     * 防御性捕获 Throwable，确保 SDK 未初始化等极端情况下不影响消息构建。</p>
     *
     * @return 当前会话标识ID，未登录或获取失败返回空字符串
     */
    private String getIdentifyId() {
        try {
            if (AISFSDKClient.getLoginApi() != null && AISFSDKClient.getLoginApi().getLoginInfo() != null) {
                return AISFSDKClient.getLoginApi().getLoginInfo().getIdentifyId();
            }
        } catch (Throwable ignored) {}
        return "";
    }

    /**
     * 获取设备信息
     *
     * <p>通过 BaseApi 获取已配置的 DeviceInfo 对象，用于填充消息头中的设备类型字段。
     * 防御性捕获 Throwable，确保 SDK 未初始化等极端情况下不影响消息构建。</p>
     *
     * @return DeviceInfo 对象，BaseApi 为 null 或获取失败返回 null
     */
    private DeviceInfo getDeviceInfo() {
        try {
            if (AISFSDKClient.getBaseApi() != null) {
                return AISFSDKClient.getBaseApi().getDeviceInfo();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * 从 BaseApi 获取设备 UUID
     *
     * <p>依次检查 BaseApi 和 DeviceInfo 是否为 null，均不为 null 时返回设备 UUID，
     * 否则返回空字符串。</p>
     *
     * <p>使用 try-catch(Throwable) 进行防御性保护：
     * 虽然内部调用均为普通 getter 且已做 null 检查，但 SessionMsgInfo 构造发生在消息发送前，
     * 为防止 SDK 未完全初始化等极端情况导致意外异常影响消息流程，此处捕获所有 Throwable
     * 并静默返回空字符串，与同类方法（getUsername、getIdentifyId、getDeviceInfo）风格保持一致。
     * </p>
     *
     * @return 设备 UUID 字符串，获取失败返回空字符串
     */
    private String getDeviceUuidFromBaseApi() {
        try {
            if (AISFSDKClient.getBaseApi() != null && AISFSDKClient.getBaseApi().getDeviceInfo() != null) {
                return AISFSDKClient.getBaseApi().getDeviceInfo().getDeviceUuid();
            }
        } catch (Throwable ignored) {}
        return "";
    }

    /**
     * 获取消息头
     *
     * @return 消息头
     */
    public SessionHeaderMsgInfo getHeader() {
        return header;
    }

    /**
     * 设置消息头
     *
     * @param header 消息头
     */
    public void setHeader(SessionHeaderMsgInfo header) {
        this.header = header;
    }

    /**
     * 获取数据
     *
     * @return 数据
     */
    public SessionData getData() {
        return data;
    }

    /**
     * 设置数据
     *
     * @param data 数据
     */
    public void setData(SessionData data) {
        this.data = data;
    }

    /**
     * 将消息序列化为 JSON 字符串
     *
     * <p>生成包含 Header、Data 节点的 JSON 对象字符串：</p>
     * <ul>
     *   <li>voiceId 不为空时，添加 Payload 字段</li>
     *   <li>userInputObjectCode != -1 且 userInputObjectData 不为空时，在 Data 中添加 UserInputObject 字段</li>
     * </ul>
     * <p>序列化失败时返回空 JSON 对象 "{}"。</p>
     *
     * @return JSON 格式的消息字符串，序列化失败返回 "{}"
     */
    @Override
    public String toString() {
        try {
            JSONObject root = new JSONObject();
            root.put("Header", header.toJson());
            JSONObject dataJson = data.toJson();
            root.put("Data", dataJson);
            // 添加 Payload
            String voiceId = data != null ? data.getVoiceId() : null;
            if (voiceId != null && !voiceId.isEmpty()) {
                JSONObject payloadData = new JSONObject();
                payloadData.put("SubUserId", voiceId);
                JSONObject payload = new JSONObject();
                payload.put("Function", "10002");
                payload.put("Data", payloadData);
                root.put("Payload", payload);
            }
            return root.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }
}
