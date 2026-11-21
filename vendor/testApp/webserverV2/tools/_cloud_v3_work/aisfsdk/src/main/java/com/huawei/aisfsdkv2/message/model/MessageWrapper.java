/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model;

/**
 * 消息包装类，记录入队时间用于调度延迟统计
 */
public class MessageWrapper {
    /** 消息内容 */
    public final String message;
    /** 入队时间（毫秒） */
    public final long enqueueTimeMs;

    /**
     * 构造方法
     *
     * @param message 消息内容
     */
    public MessageWrapper(String message) {
        this.message = message;
        this.enqueueTimeMs = System.currentTimeMillis();
    }
}
