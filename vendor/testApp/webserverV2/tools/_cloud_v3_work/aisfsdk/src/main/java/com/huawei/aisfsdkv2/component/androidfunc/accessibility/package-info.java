/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 无障碍服务组件包
 *
 * <p>本包基于 Android AccessibilityService 实现自动化操作能力，
 * 提供点击、滑动、输入、系统按键等 UI 自动化功能。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.accessibility.AccessibilityManager}：无障碍管理器接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.accessibility.AccessibilityOperationHandler}：无障碍事件处理器接口</li>
 * </ul>
 *
 * <h3>子包说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.accessibility.animation}：点击动画效果</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.accessibility.handler}：事件处理器实现</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.accessibility.impl}：无障碍管理器实现</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.accessibility.model}：数据模型</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取无障碍管理器
 *   AccessibilityManager manager = AndroidFuncManager.getInstance().getAccessibilityManager();
 *
 *   // 检查服务是否启用
 *   if (manager.isAccessibilityEnabled()) {
 *       // 执行点击操作
 *       manager.click(500, 800);
 *
 *       // 执行滑动操作
 *       manager.swipe(500, 1000, 500, 500, 300);
 *
 *       // 输入文本
 *       manager.inputText(500, 800, "Hello World");
 *
 *       // 系统按键操作
 *       manager.goHome();
 *       manager.goBack();
 *   }
 * </pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>使用前必须检查无障碍服务是否已启用</li>
 *   <li>引导用户在系统设置中开启无障碍服务</li>
 *   <li>操作失败时检查目标应用是否在前台</li>
 *   <li>避免高频操作，防止被系统限制</li>
 *   <li>注册事件处理器监听屏幕变化</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.androidfunc.accessibility;
