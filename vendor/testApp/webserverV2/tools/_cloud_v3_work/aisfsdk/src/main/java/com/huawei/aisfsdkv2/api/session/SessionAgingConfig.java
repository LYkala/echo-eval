/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

/**
 * 会话老化配置
 *
 * <p>定义会话老化策略，当会话空闲时间超过阈值时自动销毁。</p>
 *
 * <h3>老化判定规则</h3>
 * <ul>
 *   <li>空闲时间 = 当前时间 - 最后一次消息收发时间</li>
 *   <li>maxIdleTimeMinutes = -1 表示永不老化</li>
 *   <li>maxIdleTimeMinutes = 0 表示立即老化（不推荐）</li>
 *   <li>maxIdleTimeMinutes &gt; 0 表示超过指定分钟数后老化</li>
 * </ul>
 *
 * @since 2026-04-22
 */
public class SessionAgingConfig {

    /** 永不老化标记 */
    public static final int NEVER_AGING = -1;

    /** 最大空闲时间（分钟），-1表示永不老化 */
    private int maxIdleTimeMinutes = NEVER_AGING;

    /**
     * 获取最大空闲时间
     *
     * @return 最大空闲时间（分钟），-1表示永不老化
     */
    public int getMaxIdleTimeMinutes() {
        return maxIdleTimeMinutes;
    }

    /**
     * 设置最大空闲时间
     *
     * @param maxIdleTimeMinutes 最大空闲时间（分钟），-1表示永不老化
     * @return 当前配置对象，支持链式调用
     */
    public SessionAgingConfig setMaxIdleTimeMinutes(int maxIdleTimeMinutes) {
        this.maxIdleTimeMinutes = maxIdleTimeMinutes;
        return this;
    }

    /**
     * 是否启用老化
     *
     * @return true 启用老化，false 永不老化
     */
    public boolean isAgingEnabled() {
        return maxIdleTimeMinutes != NEVER_AGING;
    }

    @Override
    public String toString() {
        return "SessionAgingConfig{maxIdleTimeMinutes=" + maxIdleTimeMinutes + "}";
    }
}
