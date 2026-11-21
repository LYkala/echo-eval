/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logtrack;

/**
 * 日志追踪类型枚举
 *
 * <p>定义消息追踪的方向类型。</p>
 */
public enum LogTrackType {
    /**
     * SDK 发送消息到服务端
     */
    SEND("SDK", "SERVER"),

    /**
     * SDK 接收服务端消息
     */
    RECEIVE("SERVER", "SDK");

    private final String from;
    private final String to;

    LogTrackType(String from, String to) {
        this.from = from;
        this.to = to;
    }

    /**
     * 获取消息来源
     *
     * @return 来源标识
     */
    public String getFrom() {
        return from;
    }

    /**
     * 获取消息目标
     *
     * @return 目标标识
     */
    public String getTo() {
        return to;
    }
}
