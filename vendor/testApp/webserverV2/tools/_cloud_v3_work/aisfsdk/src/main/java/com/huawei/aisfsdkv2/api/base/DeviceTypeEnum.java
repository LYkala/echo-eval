/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.base;

/**
 * 设备类型
 */
public enum DeviceTypeEnum {
    /**
     * 联通云机
     */
    CLOUD_PHONE("CLOUD_PHONE", "1"),
    /**
     * 物理机
     */
    PHY_PHONE("PHY_PHONE", "2"),
    /**
     * 算力主机
     */
    COMPUTING_HOST("COMPUTING_HOST", "3"),
    /**
     * 云POD
     */
    CLOUD_PAD("CLOUD_PAD", "4"),
    /**
     * 电信物理机
     */
    DX_PHY_PHONE("DX_PHY_PHONE", "5");

    /**
     * 明文类型
     */
    private final String name;
    /**
     * 值
     */
    private final String value;

    /**
     * 构造方法
     *
     * @param name  明文类型
     * @param value 值
     */
    DeviceTypeEnum(String name, String value) {
        this.name = name;
        this.value = value;
    }

    /**
     * 获取明文类型名称
     *
     * @return 明文类型名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取类型值
     *
     * @return 类型值
     */
    public String getValue() {
        return value;
    }
}
