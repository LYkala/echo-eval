/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

import org.json.JSONObject;

import java.util.Map;

/**
 * 消息发送前处理器
 *
 * <p>在消息发送前允许用户自定义处理逻辑。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * SendPreHandler handler = (json, params) -> {
 *     // 添加自定义字段
 *     json.put("customField", "customValue");
 *     return json;
 * };
 * </pre>
 *
 * @since 2026-04-13
 */
public interface SendPreHandler {
    /**
     * 发送前处理
     *
     * <p>在消息发送前允许用户修改 JSON 内容。</p>
     *
     * @param json 即将发送的消息 JSON 对象
     * @param params 从 SessionRequest 中获取的参数映射
     * @return 处理后的 JSON 对象，返回 null 则取消发送
     */
    JSONObject handle(JSONObject json, Map<String, String> params);
}
