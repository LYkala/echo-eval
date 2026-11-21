/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 位置上报组件包
 *
 * <p>本包提供设备位置获取和定时上报功能，用于向服务端上报设备地理位置。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.location.LocationReporter}：位置上报器接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.location.model.Location}：位置信息数据类</li>
 * </ul>
 *
 * <h3>子包说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.location.impl}：位置上报器实现</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.location.model}：位置数据模型</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取位置上报器实例（自动启动定时上报）
 *   LocationReporter reporter = LocationReporter.getInstance();
 *
 *   // 初始化位置上报
 *   reporter.init();
 * </pre>
 *
 * <h3>功能特性</h3>
 * <ul>
 *   <li>自动获取设备当前位置</li>
 *   <li>定时上报位置到服务端</li>
 *   <li>支持 GPS、网络等多种定位方式</li>
 *   <li>位置变化时触发上报</li>
 * </ul>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>获取实例后自动启动定时上报任务</li>
 *   <li>需要定位权限（ACCESS_FINE_LOCATION 或 ACCESS_COARSE_LOCATION）</li>
 *   <li>应用在后台时位置上报可能受限</li>
 *   <li>注意用户隐私，明确告知位置使用目的</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.location;
