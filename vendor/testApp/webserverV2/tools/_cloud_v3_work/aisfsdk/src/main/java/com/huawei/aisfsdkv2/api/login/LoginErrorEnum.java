/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.login;

/**
 * 登录错误码枚举
 * <p>错误码从 0 开始自增：
 * - 0: NO_ERROR (成功)
 * - 1~N: 各类错误
 */
public enum LoginErrorEnum {

    /** 成功 */
    NO_ERROR(0, "Success"),

    /** 参数错误（username/password 为空） */
    ERR_PARAM(1, "Username and password cannot be empty"),

    /** 登录认证失败（正在连接中或认证失败） */
    ERR_LOGIN_AUTH_FAILED(2, "Login authentication failed"),

    /** 登录失败，需要重试（异常断开） */
    LOGIN_FAILURE_NEED_RETRY(3, "Login failed, need retry"),

    /** 登录失败，无需重试（正常登出） */
    LOGIN_FAILURE_NO_RETRY(4, "Login failed, no retry needed"),

    /** 重复登录（已处于 CONNECTED/DISCONNECTED 状态） */
    ERR_ALREADY_LOGGED_IN(5, "Already logged in"),

    /** 登录进行中（已处于 AUTHING/CONNECTING 状态，或重登录任务冲突） */
    ERR_LOGIN_IN_PROGRESS(6, "Login already in progress");

    private final int code;
    private final String defaultMessage;

    LoginErrorEnum(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取错误码
     *
     * @return 错误码数值
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取默认错误信息
     *
     * @return 默认错误描述
     */
    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * 根据错误码查找枚举
     *
     * @param code 错误码
     * @return 对应的枚举值，找不到返回 null
     */
    public static LoginErrorEnum fromCode(int code) {
        for (LoginErrorEnum error : values()) {
            if (error.code == code) {
                return error;
            }
        }
        return null;
    }
}
