/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.network;

/**
 * 网络变更回调接口
 */
public interface NetworkChangeCallback {

    /**
     * 网络状态发生变化时回调
     *
     * @param oldStatus 变化前的网络状态
     * @param newStatus 变化后的网络状态
     */
    void onNetworkChanged(NetworkStatusInfo oldStatus, NetworkStatusInfo newStatus);
}
