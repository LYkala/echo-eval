/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 位置上报实现包
 *
 * <p>本包提供 {@link com.huawei.aisfsdkv2.component.location.LocationReporter} 接口的实现，
 * 使用 Android 定位服务获取位置并上报。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.location.impl.LocationReporterImpl}：位置上报器实现</li>
 * </ul>
 *
 * <h3>实现细节</h3>
 * <ul>
 *   <li>使用 LocationManager 获取位置</li>
 *   <li>支持 GPS 和网络定位提供者</li>
 *   <li>位置变化时通过 ACM 接口上报</li>
 *   <li>定时任务确保位置定期更新</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <p>本包类仅供 SDK 内部使用，业务代码应通过 {@code LocationReporter} 接口访问。</p>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.location.impl;
