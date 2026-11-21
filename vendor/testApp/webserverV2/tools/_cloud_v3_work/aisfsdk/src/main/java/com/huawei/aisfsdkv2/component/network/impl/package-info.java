/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 网络监控实现包
 *
 * <p>本包提供 {@link com.huawei.aisfsdkv2.component.network.NetworkMonitor} 接口的实现，
 * 使用 Android ConnectivityManager 监控网络状态。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.network.impl.NetworkMonitorImpl}：网络监控器实现</li>
 * </ul>
 *
 * <h3>实现细节</h3>
 * <ul>
 *   <li>使用 ConnectivityManager.NetworkCallback 监听网络变化</li>
 *   <li>支持检测 WiFi、移动数据、以太网等网络类型</li>
 *   <li>自动处理网络可用性变化通知</li>
 *   <li>多订阅者模式，支持多个回调同时监听</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <p>本包类仅供 SDK 内部使用，业务代码应通过 {@code NetworkMonitor} 接口访问。</p>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.network.impl;
