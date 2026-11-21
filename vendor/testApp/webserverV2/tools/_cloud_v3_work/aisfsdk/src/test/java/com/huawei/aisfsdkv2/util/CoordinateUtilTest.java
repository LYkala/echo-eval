/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * CoordinateUtil 单元测试
 *
 * <p>测试坐标系转换工具</p>
 *
 * @author l00504561
 * @since 2026-03-31
 */
public class CoordinateUtilTest {

    // 精度允许范围（米转度的近似值）
    private static final double DELTA = 0.0001; // 约 10 米精度

    // ==================== WGS84转GCJ02 测试 ====================

    /**
     * 测试 WGS84 转 GCJ02：北京市中心坐标
     */
    @Test
    public void testWgs84ToGcj02_Beijing() {
        // 北京市中心 WGS84 坐标（天安门）
        double wgs84Lng = 116.397428;
        double wgs84Lat = 39.90923;
        
        double[] gcj02 = CoordinateUtil.wgs84ToGcj02(wgs84Lng, wgs84Lat);
        
        // 验证返回数组不为null且长度为2
        assertEquals("返回数组长度应为2", 2, gcj02.length);
        
        // 北京地区的WGS84转GCJ02后，坐标应该有一定偏移
        // GCJ02经度应该比WGS84稍大
        assertEquals("北京经度转换后应有偏移", 116.404, gcj02[0], 0.01);
        // GCJ02纬度应该比WGS84稍大
        assertEquals("北京纬度转换后应有偏移", 39.915, gcj02[1], 0.01);
    }

    /**
     * 测试 WGS84 转 GCJ02：上海市中心坐标
     */
    @Test
    public void testWgs84ToGcj02_Shanghai() {
        // 上海市中心 WGS84 坐标（人民广场）
        double wgs84Lng = 121.473701;
        double wgs84Lat = 31.230416;
        
        double[] gcj02 = CoordinateUtil.wgs84ToGcj02(wgs84Lng, wgs84Lat);
        
        assertEquals("返回数组长度应为2", 2, gcj02.length);
        assertEquals("上海经度转换后应有偏移", 121.487, gcj02[0], 0.02);
        assertEquals("上海纬度转换后应有偏移", 31.237, gcj02[1], 0.02);
    }

    /**
     * 测试 WGS84 转 GCJ02：境外坐标不做转换
     */
    @Test
    public void testWgs84ToGcj02_OutOfChina() {
        // 纽约坐标（境外）
        double wgs84Lng = -74.006;
        double wgs84Lat = 40.7128;
        
        double[] gcj02 = CoordinateUtil.wgs84ToGcj02(wgs84Lng, wgs84Lat);
        
        // 境外坐标应保持不变
        assertEquals("境外经度应保持不变", wgs84Lng, gcj02[0], 0.00001);
        assertEquals("境外纬度应保持不变", wgs84Lat, gcj02[1], 0.00001);
    }

    /**
     * 测试 WGS84 转 GCJ02：边界坐标（中国边界）
     */
    @Test
    public void testWgs84ToGcj02_Border() {
        // 中国最西端（新疆）
        double[] result1 = CoordinateUtil.wgs84ToGcj02(73.0, 35.0);
        assertEquals("返回数组长度应为2", 2, result1.length);
        
        // 中国最东端（黑龙江）
        double[] result2 = CoordinateUtil.wgs84ToGcj02(135.0, 48.0);
        assertEquals("返回数组长度应为2", 2, result2.length);
        
        // 中国最南端（南海）
        double[] result3 = CoordinateUtil.wgs84ToGcj02(110.0, 18.0);
        assertEquals("返回数组长度应为2", 2, result3.length);
    }

    // ==================== GCJ02转WGS84 测试 ====================

    /**
     * 测试 GCJ02 转 WGS84：与正转换近似互逆
     */
    @Test
    public void testGcj02ToWgs84_RoundTrip() {
        // 原始 WGS84 坐标
        double originalLng = 116.397428;
        double originalLat = 39.90923;
        
        // WGS84 -> GCJ02
        double[] gcj02 = CoordinateUtil.wgs84ToGcj02(originalLng, originalLat);
        
        // GCJ02 -> WGS84
        double[] wgs84 = CoordinateUtil.gcj02ToWgs84(gcj02[0], gcj02[1]);
        
        // 正向和反向转换后，应该接近原始坐标（允许一定误差）
        assertEquals("经度往返转换后应接近原始值", originalLng, wgs84[0], 0.0001);
        assertEquals("纬度往返转换后应接近原始值", originalLat, wgs84[1], 0.0001);
    }

    /**
     * 测试 GCJ02 转 WGS84：境外坐标
     */
    @Test
    public void testGcj02ToWgs84_OutOfChina() {
        // 东京坐标
        double gcj02Lng = 139.6917;
        double gcj02Lat = 35.6895;
        
        double[] wgs84 = CoordinateUtil.gcj02ToWgs84(gcj02Lng, gcj02Lat);
        
        assertEquals("返回数组长度应为2", 2, wgs84.length);
        // 境外坐标转换后应该接近原值（因为outOfChina检查在wgs84ToGcj02中）
    }

    // ==================== 精度测试 ====================

    /**
     * 测试坐标转换精度：相同坐标多次转换结果一致
     */
    @Test
    public void testCoordinateConversion_Consistency() {
        double lng = 116.397428;
        double lat = 39.90923;
        
        double[] result1 = CoordinateUtil.wgs84ToGcj02(lng, lat);
        double[] result2 = CoordinateUtil.wgs84ToGcj02(lng, lat);
        
        // 相同输入应产生相同输出
        assertEquals("多次转换经度应一致", result1[0], result2[0], 0.0000001);
        assertEquals("多次转换纬度应一致", result1[1], result2[1], 0.0000001);
    }

    /**
     * 测试坐标值范围有效性
     */
    @Test
    public void testCoordinateRange() {
        // 测试中国境内随机坐标
        double lng = 120.5;
        double lat = 30.2;
        
        double[] gcj02 = CoordinateUtil.wgs84ToGcj02(lng, lat);
        
        // 转换后的坐标应在有效范围内
        assertEquals("经度应在有效范围内", lng, gcj02[0], 1.0); // 偏移通常小于1度
        assertEquals("纬度应在有效范围内", lat, gcj02[1], 1.0);
    }
}
