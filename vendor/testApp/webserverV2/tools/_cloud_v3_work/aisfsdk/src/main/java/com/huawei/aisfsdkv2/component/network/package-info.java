/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 网络监控组件包
 *
 * <p>本包提供 Android 设备网络状态监控功能，实时检测网络连接状态和类型变化。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.network.NetworkMonitor}：网络监控器接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.network.NetworkChangeCallback}：网络变更回调接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.network.NetworkStatusInfo}：网络状态信息</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.network.NetworkType}：网络类型枚举</li>
 * </ul>
 *
 * <h3>子包说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.network.impl}：网络监控器实现</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取网络监控器实例
 *   NetworkMonitor networkMonitor = NetworkMonitor.getInstance();
 *
 *   // 初始化网络监控
 *   networkMonitor.init();
 *
 *   // 订阅网络变更
 *   networkMonitor.subscribe(new NetworkChangeCallback() {
 *       &#64;Override
 *       public void onNetworkChanged(NetworkStatusInfo status) {
 *           NetworkType type = status.getNetworkType();
 *           boolean connected = status.isConnected();
 *           // 处理网络变化
 *       }
 *   });
 *
 *   // 获取当前网络状态
 *   NetworkStatusInfo status = networkMonitor.getNetworkStatus();
 * </pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>应用启动时初始化网络监控</li>
 *   <li>订阅网络变更以响应网络变化</li>
 *   <li>网络请求前检查网络状态</li>
 *   <li>WiFi 和移动数据切换时注意流量消耗</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.network;
