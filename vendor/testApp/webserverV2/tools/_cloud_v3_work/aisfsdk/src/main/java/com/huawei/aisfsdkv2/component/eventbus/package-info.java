/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 事件总线组件包
 *
 * <p>本包提供组件间通信的事件总线功能，支持事件的订阅、发布和多种处理策略。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.eventbus.EventBusManager}：事件总线管理器接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.eventbus.Event}：事件基类</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.eventbus.EventSubscriber}：事件订阅者接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.eventbus.EventHandleStrategyEnum}：事件处理策略枚举</li>
 * </ul>
 *
 * <h3>子包说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.eventbus.event}：预定义事件类型</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.eventbus.impl}：事件总线实现</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取事件总线管理器
 *   EventBusManager eventBus = EventBusManager.getInstance();
 *
 *   // 订阅事件
 *   eventBus.subscribeEvent(new EventSubscriber() {
 *       &#64;Override
 *       public void onEvent(Event event) {
 *           // 处理事件
 *       }
 *   }, new ConnectionOpenedEvent());
 *
 *   // 发布事件（同步处理）
 *   eventBus.publish(new ConnectionOpenedEvent(),
 *       EventHandleStrategyEnum.SYNC);
 *
 *   // 发布事件（异步单线程）
 *   eventBus.publish(new ConnectionOpenedEvent(),
 *       EventHandleStrategyEnum.ASYNC_SINGLE_THREAD);
 *
 *   // 发布事件（异步并行）
 *   eventBus.publish(new ConnectionOpenedEvent(),
 *       EventHandleStrategyEnum.ASYNC_PARALLEL);
 * </pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>根据事件处理耗时选择合适的处理策略</li>
 *   <li>同步策略适用于轻量级、快速完成的处理</li>
 *   <li>异步策略适用于耗时操作，避免阻塞发布线程</li>
 *   <li>事件类应包含必要的数据字段</li>
 *   <li>订阅者在不需要时及时取消订阅，避免内存泄漏</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.eventbus;
