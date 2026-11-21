/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.login.connection.msghandle;

/**
 * 消息发送结果码枚举
 */
public enum SendResultCodeEnum {

    /** 发送成功 */
    SUCCESS(0, "success"),

    /** 未连接 */
    NOT_CONNECTED(-1, "not connected"),

    /** 被过滤（beforeFilter 返回 null） */
    FILTERED(-2, "filtered by beforeFilter"),

    /** 队列已满 */
    QUEUE_FULL(-3, "message queue is full"),

    /** WebSocket 发送失败 */
    SEND_FAILED(-4, "websocket send failed"),

    /** 发送时连接断开 */
    DISCONNECTED(-5, "connection disconnected");

    private final int code;
    private final String description;

    SendResultCodeEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取结果码
     *
     * @return 结果码数值
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取结果描述
     *
     * @return 描述信息
     */
    public String getDescription() {
        return description;
    }

    /**
     * 判断是否成功
     *
     * @return true 表示成功，false 表示失败
     */
    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
