/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.location.model;

/**
 * 位置信息类
 * 用于存储设备的经纬度坐标
 */
public class Location {
    private double latitude;
    private double longitude;

    /**
     * 获取纬度
     *
     * @return 纬度值
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * 设置纬度
     *
     * @param latitude 纬度值
     */
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    /**
     * 获取经度
     *
     * @return 经度值
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * 设置经度
     *
     * @param longitude 经度值
     */
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
}
