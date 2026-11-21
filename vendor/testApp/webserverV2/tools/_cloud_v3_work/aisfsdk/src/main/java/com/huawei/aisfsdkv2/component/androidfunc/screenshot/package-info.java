/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 截图组件包
 *
 * <p>本包提供 Android 屏幕截图功能，支持无障碍服务截图和虚拟屏截图两种方式，
 * 自动选择最佳方式并处理重试逻辑。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.screenshot.ScreenshotManager}：截图管理器接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.screenshot.MediaScreenshotService}：MediaProjection 截图服务</li>
 * </ul>
 *
 * <h3>功能特性</h3>
 * <ul>
 *   <li>优先使用无障碍服务截图（Android 11+）</li>
 *   <li>无障碍截图失败时回退到虚拟屏截图</li>
 *   <li>自动重试和纯色图片过滤</li>
 *   <li>截图结果缓存，支持获取最近截图</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取截图管理器
 *   ScreenshotManager screenshotManager = AndroidFuncManager.getInstance().getScreenshotManager();
 *
 *   // 启动截图服务
 *   screenshotManager.startScreenshotService();
 *
 *   // 获取有效截图（带重试和过滤）
 *   Bitmap screenshot = screenshotManager.takeValidScreenshot();
 *
 *   // 或快速截图（单次）
 *   Bitmap screenshot = screenshotManager.takeScreenshot();
 *
 *   // 获取屏幕分辨率
 *   int[] resolution = screenshotManager.getScreenResolution();
 *   int width = resolution[0];
 *   int height = resolution[1];
 *
 *   // 停止截图服务
 *   screenshotManager.stopScreenshotService();
 * </pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>使用前调用 startScreenshotService() 初始化截图服务</li>
 *   <li>使用 takeValidScreenshot() 获取有效截图，自动处理失败情况</li>
 *   <li>截图后及时回收 Bitmap 避免内存泄漏</li>
 *   <li>页面销毁时调用 stopScreenshotService() 释放资源</li>
 *   <li>虚拟屏截图需要用户授权屏幕录制权限</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.androidfunc.screenshot;
