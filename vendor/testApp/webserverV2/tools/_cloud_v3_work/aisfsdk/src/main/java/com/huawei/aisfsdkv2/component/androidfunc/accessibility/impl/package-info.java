/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 无障碍服务实现包
 *
 * <p>本包提供 {@link com.huawei.aisfsdkv2.component.androidfunc.accessibility.AccessibilityManager} 接口的实现，
 * 基于 Android AccessibilityService 实现具体的自动化操作功能。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.accessibility.impl.AccessibilityManagerImpl}：无障碍管理器实现</li>
 * </ul>
 *
 * <h3>实现细节</h3>
 * <ul>
 *   <li>继承 Android AccessibilityService 实现后台服务</li>
 *   <li>使用 AccessibilityNodeInfo 查找和操作界面元素</li>
 *   <li>手势操作使用 GestureDescription 实现</li>
 *   <li>支持屏幕截图（需 Android 11+）</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <p>本包类仅供 SDK 内部使用，业务代码应通过 {@code AccessibilityManager} 接口访问。</p>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.androidfunc.accessibility.impl;
