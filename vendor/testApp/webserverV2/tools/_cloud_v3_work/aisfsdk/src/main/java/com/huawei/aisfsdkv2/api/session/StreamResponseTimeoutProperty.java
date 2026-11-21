/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

/**
 * 流式响应超时属性
 *
 * <p>定义会话响应超时策略。当会话在两条响应消息之间的间隔
 * 超过配置的超时时间时，触发超时通知回调。</p>
 *
 * <h3>配置规则</h3>
 * <ul>
 *   <li>timeoutMillis = -1 — 不启用超时检查（默认）</li>
 *   <li>timeoutMillis > 0 — 启用超时，单位毫秒</li>
 *   <li>超时仅在两条响应消息（receive）之间计算</li>
 *   <li>仅在有活跃任务时判定</li>
 *   <li>已销毁的会话不参与检查</li>
 * </ul>
 *
 * <h3>时间精度</h3>
 * <p>支持秒级和毫秒级两种设置方式：</p>
 * <ul>
 *   <li>{@link #setTimeoutSeconds(int)} — 秒级便捷接口，内部转换为毫秒</li>
 *   <li>{@link #setTimeoutMillis(int)} — 毫秒级精确接口，适合测试等精细场景</li>
 *   <li>两种方式设置同一个内部字段，后设置的生效</li>
 * </ul>
 *
 * @since 2026-06-18
 */
public class StreamResponseTimeoutProperty {

    /** 超时时间（毫秒），-1表示不启用超时检查 */
    private int timeoutMillis = -1;

    /**
     * 获取超时时间（毫秒）
     *
     * @return 超时时间（毫秒），-1表示不启用
     */
    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    /**
     * 设置超时时间（毫秒）
     *
     * <p>毫秒级精确接口，适合需要精细控制的场景（如测试）。</p>
     * <p>-1表示不启用超时检查，正值表示超时毫秒数。</p>
     *
     * @param timeoutMillis 超时时间（毫秒），-1不启用，>0启用
     * @return 当前属性对象，支持链式调用
     */
    public StreamResponseTimeoutProperty setTimeoutMillis(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
        return this;
    }

    /**
     * 获取超时时间（秒）
     *
     * @return 超时时间（秒），-1表示不启用，向下取整
     */
    public int getTimeoutSeconds() {
        return timeoutMillis / 1000;
    }

    /**
     * 设置超时时间（秒）
     *
     * <p>秒级便捷接口，内部转换为毫秒存储。</p>
     * <p>-1表示不启用超时检查，正值表示超时秒数。</p>
     *
     * @param timeoutSeconds 超时时间（秒），-1不启用，>0启用
     * @return 当前属性对象，支持链式调用
     */
    public StreamResponseTimeoutProperty setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutMillis = timeoutSeconds * 1000;
        return this;
    }

    /**
     * 是否启用超时检查
     *
     * @return true 启用，false 不启用
     */
    public boolean isTimeoutEnabled() {
        return timeoutMillis > 0;
    }

    @Override
    public String toString() {
        return "StreamResponseTimeoutProperty{timeoutMillis=" + timeoutMillis
            + (timeoutMillis > 0 ? "(" + (timeoutMillis / 1000) + "s)" : "(disabled)")
            + "}";
    }
}
