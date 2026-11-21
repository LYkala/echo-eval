/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.session;

/**
 * 会话类型枚举
 *
 * <p>区分会话的创建方式：</p>
 * <ul>
 *   <li>{@link #ACTIVE} - 主动会话：由用户通过API主动创建</li>
 *   <li>{@link #PASSIVE} - 被动会话：由SDK内部自动创建（如默认会话）</li>
 * </ul>
 *
 * @since 2026-04-15
 */
public enum SessionType {
    /**
     * 主动会话
     *
     * <p>由用户通过 {@link SessionManager#createSession} 主动创建，
     * 生命周期由用户管理。</p>
     */
    ACTIVE,

    /**
     * 被动会话
     *
     * <p>由SDK内部自动创建（如默认会话），
     * 受 {@link com.huawei.aisfsdkv2.session.passive.PassiveSessionManager} 管理，
     * 长时间无任务会自动清理。</p>
     */
    PASSIVE
}
