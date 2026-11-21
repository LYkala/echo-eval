/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.network;

import java.util.Objects;

/**
 * 网络状态信息封装类
 * 包含网络是否可用和网络类型信息
 * 不可变对象，线程安全
 */
public class NetworkStatusInfo {

    /**
     * 无网络状态常量
     */
    public static final NetworkStatusInfo DISCONNECTED =
            new NetworkStatusInfo(false, NetworkType.NONE);

    private final boolean networkAvailable;
    private final NetworkType networkType;

    /**
     * 构造方法
     *
     * @param networkAvailable 网络是否可用
     * @param networkType 网络类型
     */
    public NetworkStatusInfo(boolean networkAvailable, NetworkType networkType) {
        this.networkAvailable = networkAvailable;
        this.networkType = networkType != null ? networkType : NetworkType.NONE;
    }

    /**
     * 判断网络是否可用
     *
     * @return true 可用，false 不可用
     */
    public boolean isNetworkAvailable() {
        return networkAvailable;
    }

    /**
     * 获取网络类型
     *
     * @return 网络类型枚举
     */
    public NetworkType getNetworkType() {
        return networkType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NetworkStatusInfo that = (NetworkStatusInfo) o;
        return networkAvailable == that.networkAvailable &&
                networkType == that.networkType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(networkAvailable, networkType);
    }

    @Override
    public String toString() {
        return "NetworkStatusInfo{" +
                "networkAvailable=" + networkAvailable +
                ", networkType=" + networkType +
                '}';
    }
}
