/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.login;

/**
 * Ticket 提供器接口
 *
 * <p>用于外部提供 Ticket 以换取 Token。</p>
 *
 * <p>当 LoginParam 设置了 TicketProvider 时，SDK 将使用 Ticket 方式获取 Token，
 * 而不是传统的 username/password 方式。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * LoginParam param = new LoginParam.Builder()
 *     .username("user123")
 *     .ticketProvider(() -> "your-ticket-string")
 *     .build();
 * </pre>
 */
public interface TicketProvider {

    /**
     * 获取 Ticket
     *
     * <p>SDK 在需要获取 Token 时会调用此方法获取 Ticket。</p>
     *
     * @return Ticket 字符串，返回 null 或空字符串表示获取失败
     */
    String getTicket();
}
