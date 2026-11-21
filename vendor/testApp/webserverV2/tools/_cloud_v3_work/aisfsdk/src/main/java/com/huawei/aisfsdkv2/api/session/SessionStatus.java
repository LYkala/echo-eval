/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

/**
 * 会话状态枚举
 *
 * <ol>
 *   <li>ACTIVE: 会话处于活跃状态，可以接收和处理消息</li>
 *   <li>PAUSED: 会话处于暂停状态，消息处理被暂停</li>
 *   <li>DESTROYED: 会话已被销毁，不再有效</li>
 * </ol>
 */
public enum SessionStatus {
    ACTIVE,
    PAUSED,
    DESTROYED
}
