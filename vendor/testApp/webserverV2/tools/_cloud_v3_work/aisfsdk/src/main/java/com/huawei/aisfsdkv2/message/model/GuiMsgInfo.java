/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model;

import com.huawei.aisfsdkv2.message.model.gui.AppInstallStatusRespParam;
import com.huawei.aisfsdkv2.message.model.gui.CheckAppInstalledReqParam;
import com.huawei.aisfsdkv2.message.model.gui.ClickReqParam;
import com.huawei.aisfsdkv2.message.model.gui.CompletedReqParam;
import com.huawei.aisfsdkv2.message.model.gui.GuiBaseParams;
import com.huawei.aisfsdkv2.message.model.gui.GuiData;
import com.huawei.aisfsdkv2.message.model.gui.GuiPayload;
import com.huawei.aisfsdkv2.message.model.gui.InputTextReqParam;
import com.huawei.aisfsdkv2.message.model.gui.LaunchAppReqParam;
import com.huawei.aisfsdkv2.message.model.gui.LongClickReqParam;
import com.huawei.aisfsdkv2.message.model.gui.ScreenShotReqParam;
import com.huawei.aisfsdkv2.message.model.gui.SwipeReqParam;
import com.huawei.aisfsdkv2.message.model.gui.SystemKeyReqParam;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * GUI消息信息类
 *
 * <p>完整的GUI消息模型，包含Header、Data和Payload三层结构。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 构建点击消息
 * GuiMsgInfo clickMsg = new GuiMsgInfo();
 * clickMsg.setCmdType(GuiCmdType.CLICK);
 * clickMsg.getPayload().setParams(new ClickParams(100, 200));
 *
 * // 从JSON解析
 * GuiMsgInfo parsed = GuiMsgInfo.fromJson(jsonObject);
 * </pre>
 */
public class GuiMsgInfo {
    /** 消息头 */
    private HeaderMsgInfo header;

    /** 数据域 */
    private GuiData data;

    /** 载荷域 */
    private GuiPayload payload;

    /**
     * 默认构造方法
     */
    public GuiMsgInfo() {
        this.header = new HeaderMsgInfo();
        this.data = new GuiData();
        this.payload = new GuiPayload();
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
     * 获取数据域
     *
     * @return 数据域
     */
    public GuiData getData() {
        return data;
    }

    /**
     * 设置数据域
     *
     * @param data 数据域
     */
    public void setData(GuiData data) {
        this.data = data;
    }

    /**
     * 获取载荷域
     *
     * @return 载荷域
     */
    public GuiPayload getPayload() {
        return payload;
    }

    /**
     * 设置载荷域
     *
     * @param payload 载荷域
     */
    public void setPayload(GuiPayload payload) {
        this.payload = payload;
    }

    /**
     * 获取命令类型
     */
    public GuiCmdType getCmdType() {
        return payload != null ? payload.getCmdType() : GuiCmdType.UNKNOWN;
    }

    /**
     * 设置命令类型
     */
    public void setCmdType(GuiCmdType cmdType) {
        if (payload != null) {
            payload.setCmdType(cmdType);
        }
    }

    /**
     * 获取任务ID
     */
    public String getTaskId() {
        return header != null ? header.getTaskId() : null;
    }

    /**
     * 获取消息ID
     */
    public String getMessageId() {
        return header != null ? header.getUuid() : null;
    }

    /**
     * 从JSON对象解析
     */
    public static GuiMsgInfo fromJson(JSONObject json) throws JSONException {
        GuiMsgInfo info = new GuiMsgInfo();
        // 解析 Header
        if (json.has("Header")) {
            JSONObject headerJson = json.getJSONObject("Header");
            HeaderMsgInfo header = new HeaderMsgInfo();
            header.setMessageType(headerJson.optString("MessageType"));
            header.setSender(headerJson.optString("Sender"));
            header.setReceiver(headerJson.optString("Receiver"));
            header.setTaskId(headerJson.optString("TaskID"));
            header.setUuid(headerJson.optString("UUID"));
            header.setClientSessionId(headerJson.optString("ClientSessionId"));
            header.setUserName(headerJson.optString("UserName"));
            header.setDeviceId(headerJson.optString("DeviceId"));
            header.setDeviceCategory(headerJson.optString("DeviceCategory"));
            header.setDeviceCategoryStr(headerJson.optString("DeviceCategoryStr"));
            header.setIdentifyId(headerJson.optString("IdentifyId"));
            info.setHeader(header);
        }
        // 解析 Data
        if (json.has("Data")) {
            JSONObject dataJson = json.getJSONObject("Data");
            GuiData data = new GuiData();
            data.setMessageType(dataJson.optString("MessageType", "DataExchange"));
            data.setUserOutputText(dataJson.optString("UserOutputText", ""));
            info.setData(data);
        }
        // 解析 Payload
        if (json.has("Payload")) {
            JSONObject payloadJson = json.getJSONObject("Payload");
            GuiPayload payload = new GuiPayload();
            payload.setFunction(payloadJson.optString("Function", "GUI"));
            // 解析命令类型
            int cmdCode = payloadJson.optInt("CmdType", -1);
            GuiCmdType cmdType = GuiCmdType.fromCode(cmdCode);
            payload.setCmdType(cmdType);
            // 根据命令类型解析参数
            if (payloadJson.has("Params")) {
                JSONObject paramsJson = payloadJson.getJSONObject("Params");
                payload.setParams(parseParamsByCmdType(cmdType, paramsJson));
            }
            info.setPayload(payload);
        }
        return info;
    }

    /**
     * 根据命令类型解析参数
     *
     * @param cmdType 命令类型
     * @param json JSON对象
     * @return 解析后的参数对象
     */
    private static GuiBaseParams parseParamsByCmdType(GuiCmdType cmdType, JSONObject json) {
        switch (cmdType) {
            case CLICK:
                return new ClickReqParam().parseJson(json);
            case SWIPE:
                return new SwipeReqParam().parseJson(json);
            case LONG_CLICK:
                return new LongClickReqParam().parseJson(json);
            case INPUT_TEXT:
                return new InputTextReqParam().parseJson(json);
            case SCREEN_SHOT:
                return new ScreenShotReqParam().parseJson(json);
            case CHECK_APP_INSTALLED:
                return new CheckAppInstalledReqParam().parseJson(json);
            case APP_INSTALL_STATUS:
                return new AppInstallStatusRespParam().parseJson(json);
            case LAUNCH_APP:
                return new LaunchAppReqParam().parseJson(json);
            case COMPLETED:
                return new CompletedReqParam().parseJson(json);
            case HOME:
            case BACK:
            case ENTER:
                return new SystemKeyReqParam().parseJson(json);
            default:
                return null;
        }
    }

    @Override
    public String toString() {
        try {
            JSONObject root = new JSONObject();
            root.put("Header", header != null ? header.toJson() : new JSONObject());
            root.put("Data", data != null ? data.toJson() : new JSONObject());
            root.put("Payload", payload != null ? payload.toJson() : new JSONObject());
            return root.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

}
