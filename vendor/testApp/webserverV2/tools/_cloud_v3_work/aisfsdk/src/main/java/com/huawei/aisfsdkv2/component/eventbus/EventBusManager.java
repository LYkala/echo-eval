/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.eventbus;

import com.huawei.aisfsdkv2.component.eventbus.impl.EventBusManagerImpl;

/**
 * 事件总线管理器接口
 * 提供事件的订阅和发布功能
 *
 * @author l00504561
 * @since 2026-02-06
 */
public interface EventBusManager {

    /**
     * 获取事件总线管理器实例（单例）
     *
     * @return EventBusManager 实例
     */
    static EventBusManager getInstance() {
        return EventBusManagerImpl.getInstance();
    }

    /**
     * 订阅事件
     *
     * @param subscriber 订阅者
     * @param event      事件（用于获取事件类型）
     */
    void subscribeEvent(EventSubscriber subscriber, Event event);

    /**
     * 取消订阅事件
     *
     * <p>将订阅者从所有已订阅的事件类型中移除。</p>
     *
     * @param subscriber 要取消的订阅者
     */
    void unsubscribeEvent(EventSubscriber subscriber);

    /**
     * 发布事件
     *
     * @param event              事件
     * @param handleStrategyEnum 处理策略（同步/异步单线程/异步并行）
     */
    void publish(Event event, EventHandleStrategyEnum handleStrategyEnum);
}
