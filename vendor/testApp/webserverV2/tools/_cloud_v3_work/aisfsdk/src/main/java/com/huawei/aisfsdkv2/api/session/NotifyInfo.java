/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

/**
 * 会话通知信息类
 *
 * <p>封装会话通知的详细信息。</p>
 */
public class NotifyInfo {
    private int code;
    private String userOutputObjectRawString;

    /**
     * 获取通知代码
     *
     * @return 通知代码
     */
    public int getCode() {
        return code;
    }

    /**
     * 设置通知代码
     *
     * @param code 通知代码
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * 获取用户输出对象原始字符串
     *
     * @return 用户输出对象原始字符串
     */
    public String getUserOutputObjectRawString() {
        return userOutputObjectRawString;
    }

    /**
     * 设置用户输出对象原始字符串
     *
     * @param userOutputObjectRawString 用户输出对象原始字符串
     */
    public void setUserOutputObjectRawString(String userOutputObjectRawString) {
        this.userOutputObjectRawString = userOutputObjectRawString;
    }

    /**
     * 返回对象的JSON格式字符串表示
     *
     * @return JSON格式的字符串
     */
    @Override
    public String toString() {
        return "{\"code\":" + code + "}";
    }
}
