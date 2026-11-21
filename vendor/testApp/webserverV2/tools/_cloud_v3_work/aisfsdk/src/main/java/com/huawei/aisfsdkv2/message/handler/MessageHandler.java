/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler;

import org.json.JSONObject;

/**
 * 消息处理器接口
 *
 * <p>采用链式模式，按优先级顺序分发消息，消费即停止。处理器需要自行判断消息类型是否匹配，
 * 匹配则处理并返回 true 表示已消费，不匹配则返回 false 继续传递给下一个处理器。</p>
 */
public interface MessageHandler {

    /**
     * 获取处理器名称
     *
     * @return 处理器名称
     */
    String getName();

    /**
     * 获取处理器优先级
     *
     * <p>数值越小优先级越高，优先级高的处理器先收到消息。</p>
     *
     * @return 优先级数值，建议范围 0-100
     */
    int getPriority();

    /**
     * 处理接收到的消息
     *
     * <p>处理器需要自行判断消息类型是否匹配，匹配则处理并返回 true 表示已消费，
     * 不匹配则返回 false 继续传递给下一个处理器。</p>
     *
     * @param json JSON消息对象
     * @return true 表示消息已消费，停止传递；false 表示不处理，继续传递
     */
    boolean handleReceived(JSONObject json);
}
