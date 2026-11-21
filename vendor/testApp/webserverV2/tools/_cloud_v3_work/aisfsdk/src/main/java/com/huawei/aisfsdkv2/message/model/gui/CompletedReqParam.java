/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 操控完成参数
 *
 * <p>用于标记GUI操控任务完成</p>
 */
public class CompletedReqParam extends GuiBaseParams {

    /**
     * 默认构造方法
     */
    public CompletedReqParam() {
    }

    /**
     * 从JSON对象解析
     *
     * @param json JSON对象
     * @return 自身引用，用于链式调用
     */
    @Override
    public CompletedReqParam parseJson(JSONObject json) {
    
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
        return new JSONObject();
    }
}
