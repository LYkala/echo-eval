/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import com.huawei.aisfsdkv2.message.model.GuiCmdType;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * GUI消息载荷域
 *
 * <p>对应JSON中的 Payload 部分</p>
 */
public class GuiPayload {
    private String function = "GUI";
    private GuiCmdType cmdType = GuiCmdType.UNKNOWN;
    private GuiBaseParams params;

    /**
     * 获取功能类型
     *
     * @return 功能类型
     */
    public String getFunction() {
        return function;
    }

    /**
     * 设置功能类型
     *
     * @param function 功能类型
     */
    public void setFunction(String function) {
        this.function = function;
    }

    /**
     * 获取命令类型
     *
     * @return 命令类型
     */
    public GuiCmdType getCmdType() {
        return cmdType;
    }

    /**
     * 设置命令类型
     *
     * @param cmdType 命令类型
     */
    public void setCmdType(GuiCmdType cmdType) {
        this.cmdType = cmdType;
    }

    /**
     * 获取参数
     *
     * @return 参数
     */
    public GuiBaseParams getParams() {
        return params;
    }

    /**
     * 设置参数
     *
     * @param params 参数
     */
    public void setParams(GuiBaseParams params) {
        this.params = params;
    }

    /**
     * 转换为JSON对象
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("Function", function != null ? function : "GUI");
        json.put("CmdType", cmdType != null ? cmdType.getCode() : -1);
        if (params != null) {
            json.put("Params", params.toJson());
        }
        return json;
    }
}
