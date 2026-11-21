/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.network;

import com.huawei.aisfsdkv2.component.network.impl.NetworkMonitorImpl;

/**
 * 网络监控组件接口
 * 用于实时监控 Android 设备的网络连接状态
 */
public interface NetworkMonitor {

    /**
     * 获取网络监控组件实例（单例）
     *
     * @return NetworkMonitor 实例
     */
    static NetworkMonitor getInstance() {
        return NetworkMonitorImpl.getInstance();
    }

    /**
     * 初始化网络监控
     * 内部自动获取 Context 并注册系统网络回调
     */
    void init();

    /**
     * 订阅网络变更
     * 一经订阅，持续到应用生命周期结束，无法取消
     *
     * @param callback 网络变更回调
     */
    void subscribe(NetworkChangeCallback callback);

    /**
     * 获取当前网络状态
     *
     * @return 网络状态信息
     */
    NetworkStatusInfo getNetworkStatus();
}
