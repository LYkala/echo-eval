/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 应用管理实现包
 *
 * <p>本包提供 {@link com.huawei.aisfsdkv2.component.androidfunc.app.AppManager} 接口的实现，
 * 使用 Android PackageManager 实现应用管理功能。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.app.impl.AppManagerImpl}：应用管理器实现</li>
 * </ul>
 *
 * <h3>实现细节</h3>
 * <ul>
 *   <li>使用 PackageManager 查询应用信息</li>
 *   <li>使用 Intent 启动应用</li>
 *   <li>支持多种启动标志组合</li>
 *   <li>启动后检查应用是否真正显示在屏幕上</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <p>本包类仅供 SDK 内部使用，业务代码应通过 {@code AppManager} 接口访问。</p>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.androidfunc.app.impl;
