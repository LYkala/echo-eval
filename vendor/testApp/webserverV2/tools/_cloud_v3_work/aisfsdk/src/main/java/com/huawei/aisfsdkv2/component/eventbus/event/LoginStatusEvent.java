/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.eventbus.event;

import com.huawei.aisfsdkv2.api.login.LoginStatus;
import com.huawei.aisfsdkv2.component.eventbus.Event;

/**
 * 登录状态事件
 *
 * <p>当登录状态发生变化时触发，用于通知订阅者执行相应业务逻辑。</p>
 *
 * <p>典型使用场景：
 * <ul>
 *   <li>登录成功后启动定时任务管理</li>
 *   <li>登出后停止重试逻辑</li>
 *   <li>状态同步通知</li>
 * </ul>
 *
 * @since 2026-04-15
 */
public class LoginStatusEvent implements Event {

    /** 事件类型常量 */
    public static final String EVENT_TYPE = "LOGIN_STATUS";

    /** 登录状态 */
    private final LoginStatus status;

    /**
     * 构造函数
     *
     * @param status 登录状态
     */
    public LoginStatusEvent(LoginStatus status) {
        this.status = status;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    /**
     * 获取登录状态
     *
     * @return 登录状态
     */
    public LoginStatus getStatus() {
        return status;
    }

    /**
     * 是否为登录成功事件
     *
     * @return true 表示登录成功
     */
    public boolean isLoggedIn() {
        return status == LoginStatus.CONNECTED;
    }

    /**
     * 是否为登出事件
     *
     * @return true 表示已登出
     */
    public boolean isLoggedOut() {
        return status == LoginStatus.IDLE;
    }
}
