/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 事件总线实现包
 *
 * <p>本包提供 {@link com.huawei.aisfsdkv2.component.eventbus.EventBusManager} 接口的实现。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.eventbus.impl.EventBusManagerImpl}：事件总线管理器实现</li>
 * </ul>
 *
 * <h3>实现细节</h3>
 * <ul>
 *   <li>使用线程安全的集合存储订阅者</li>
 *   <li>支持按事件类型分发</li>
 *   <li>同步处理在调用线程执行</li>
 *   <li>异步处理使用线程池执行</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <p>本包类仅供 SDK 内部使用，业务代码应通过 {@code EventBusManager} 接口访问。</p>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.eventbus.impl;
