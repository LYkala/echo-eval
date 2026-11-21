/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * Android 功能组件包
 *
 * <p>本包提供 Android 平台特有功能的统一访问入口，包括无障碍服务、应用管理、权限管理和截图等功能。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.AndroidFuncManager}：Android 功能管理器，统一获取各功能模块</li>
 * </ul>
 *
 * <h3>子包说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.accessibility}：无障碍服务功能</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.app}：应用管理功能</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.permission}：权限管理功能</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.screenshot}：截图功能</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取 Android 功能管理器
 *   AndroidFuncManager funcManager = AndroidFuncManager.getInstance();
 *
 *   // 获取无障碍管理器
 *   AccessibilityManager accessibilityManager = funcManager.getAccessibilityManager();
 *
 *   // 获取应用管理器
 *   AppManager appManager = funcManager.getAppManager();
 *
 *   // 获取截图管理器
 *   ScreenshotManager screenshotManager = funcManager.getScreenshotManager();
 * </pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>所有功能模块均通过 AndroidFuncManager 获取，避免直接实例化</li>
 *   <li>无障碍功能需要用户在系统设置中开启服务权限</li>
 *   <li>敏感操作（如截图）需要相应权限</li>
 *   <li>应用生命周期方法中及时释放资源</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.androidfunc;
