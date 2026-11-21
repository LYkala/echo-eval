/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * GUI参数基类
 *
 * <p>所有GUI命令参数的抽象基类。
 * 定义参数对象的通用行为，具体的参数字段由各子类自行实现。</p>
 */
public abstract class GuiBaseParams {

    /**
     * 从JSON对象解析
     *
     * <p>子类必须实现此方法，从JSON格式解析自身字段。
     * 这是消息反序列化的关键方法。</p>
     *
     * @param json JSON对象
     * @return 自身引用，用于链式调用
     */
    public abstract GuiBaseParams parseJson(JSONObject json);

    /**
     * 转换为JSON对象
     *
     * <p>子类必须实现此方法，将自身字段转换为JSON格式。
     * 这是消息序列化的关键方法。</p>
     *
     * @return 包含所有参数字段的JSONObject
     * @throws JSONException JSON转换异常
     */
    public abstract JSONObject toJson() throws JSONException;
}
