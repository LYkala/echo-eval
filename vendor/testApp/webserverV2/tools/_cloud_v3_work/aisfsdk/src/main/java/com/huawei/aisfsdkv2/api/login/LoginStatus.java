/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.login;

/**
 * 登录状态枚举
 */
public enum LoginStatus {
    /**
     * 初始状态
     */
    IDLE("idle"),

    /**
     * 认证中（获取Token）
     */
    AUTHING("authing"),

    /**
     * 连接中（WebSocket连接）
     */
    CONNECTING("connecting"),

    /**
     * 已连接
     */
    CONNECTED("connected"),

    /**
     * 已断开
     */
    DISCONNECTED("disconnected");

    private final String value;

    LoginStatus(String value) {
        this.value = value;
    }

    /**
     * 获取字符串值
     *
     * @return 状态的字符串表示
     */
    public String getValue() {
        return value;
    }

    /**
     * 根据字符串值获取对应枚举，匹配不区分大小写
     *
     * @param value 状态字符串
     * @return 对应的 LoginStatus，未匹配返回 null
     */
    public static LoginStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (LoginStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return null;
    }
}
