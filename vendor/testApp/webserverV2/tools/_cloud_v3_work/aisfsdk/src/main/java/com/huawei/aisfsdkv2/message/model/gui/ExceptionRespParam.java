/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 异常响应参数
 *
 * <p>封装GUI操作异常的响应信息，包含错误码和错误描述。</p>
 */
public class ExceptionRespParam extends GuiBaseParams {
    /** 错误码 */
    private int errorCode;

    /** 错误描述 */
    private String errorDesc;

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * 设置错误码
     *
     * @param errorCode 错误码
     */
    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * 获取错误描述
     *
     * @return 错误描述
     */
    public String getErrorDesc() {
        return errorDesc;
    }

    /**
     * 设置错误描述
     *
     * @param errorDesc 错误描述
     */
    public void setErrorDesc(String errorDesc) {
        this.errorDesc = errorDesc;
    }

    /**
     * 从JSON对象解析
     *
     * @param json JSON对象
     * @return 自身引用，用于链式调用
     */
    @Override
    public ExceptionRespParam parseJson(JSONObject json) {
        this.errorCode = json.optInt("ErrorCode", 0);
        this.errorDesc = json.optString("ErrorDesc", "");
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
        json.put("ErrorCode", errorCode);
        json.put("ErrorDesc", errorDesc);
        return json;
    }
}
