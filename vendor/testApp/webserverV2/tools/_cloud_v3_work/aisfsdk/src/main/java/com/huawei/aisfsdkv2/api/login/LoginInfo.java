/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.login;

/**
 * 登录信息
 * 封装当前登录状态及已登录用户的基本信息
 */
public class LoginInfo {

    private final LoginStatus status;

    private final String username;

    private final String identifyId;

    private final String password;

    /**
     * 构造方法
     *
     * @param status          登录状态
     * @param username        用户名
     * @param identifyId      设备标识
     * @param password        密码
     */
    public LoginInfo(LoginStatus status, String username, String identifyId, String password) {
        this.status = status;
        this.username = username != null ? username : "";
        this.identifyId = identifyId != null ? identifyId : "";
        this.password = password;
    }

    /**
     * 获取当前登录状态
     *
     * @return 登录状态
     */
    public LoginStatus getStatus() {
        return status;
    }

    /**
     * 获取当前登录用户名，未登录时返回空字符串
     *
     * @return 用户名
     */
    public String getUsername() {
        return username;
    }

    /**
     * 获取设备标识，未登录时返回空字符串
     *
     * @return 设备标识
     */
    public String getIdentifyId() {
        return identifyId;
    }

    /**
     * 获取用户密码，未登录时返回 null
     *
     * @return 密码，用于 Token 刷新
     */
    public String getPassword() {
        return password;
    }

    @Override
    public String toString() {
        return "LoginInfo{status=" + status
                + ", username='" + username + '\''
                + ", identifyId='" + identifyId + '\''
                + ", password='" + (password != null ? "***" : null) + '\''
                + '}';
    }
}