/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.login.auth.tokenrefresher;

import com.huawei.aisfsdkv2.api.login.TicketProvider;

/**
 * Token刷新策略接口
 */
public interface TokenRefresher {

    /**
     * 获取默认实现实例
     *
     * @return TokenRefresher 实例
     */
    static TokenRefresher getInstance() {
        return AcmTokenRefresherImpl.getInstance();
    }

    /**
     * 获取Token
     *
     * @param identifyId 身份标识
     * @param username   用户名
     * @param password   密码
     * @return Token结果
     */
    TokenResult fetch(String identifyId, String username, String password);

    /**
     * 刷新Token
     *
     * @param identifyId 设备标识ID
     * @return 新Token结果
     */
    TokenResult refresh(String identifyId);

    /**
     * 设置 Ticket 提供器（可选）
     *
     * <p>设置后，fetch() 方法将优先使用 Ticket 换取 Token。</p>
     *
     * @param ticketProvider Ticket 提供器
     */
    void setTicketProvider(TicketProvider ticketProvider);
}
