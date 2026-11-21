/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 应用管理组件包
 *
 * <p>本包提供 Android 应用生命周期管理能力，包括应用安装检查、已安装应用列表获取和应用启动等功能。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.app.AppManager}：应用管理器接口</li>
 * </ul>
 *
 * <h3>子包说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.app.impl}：应用管理器实现</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.app.model}：应用信息数据模型</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取应用管理器
 *   AppManager appManager = AndroidFuncManager.getInstance().getAppManager();
 *
 *   // 检查应用是否已安装
 *   boolean installed = appManager.isAppInstalled("com.example.app");
 *
 *   // 获取已安装应用列表
 *   List&lt;AppInfo&gt; apps = appManager.getInstalledApps(false); // 不包含系统应用
 *
 *   // 启动应用
 *   boolean launched = appManager.launchApp("com.example.app");
 *
 *   // 启动应用（带启动标志）
 *   appManager.launchApp("com.example.app", 1);
 *
 *   // 通过 URL 打开外部应用或网页
 *   appManager.openUrl("https://www.example.com");
 * </pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>启动应用前建议先检查是否已安装</li>
 *   <li>使用带重试机制的启动方法提高成功率</li>
 *   <li>获取应用列表时注意性能，避免主线程调用</li>
 *   <li>打开 URL 时检查是否有应用可以处理</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.androidfunc.app;
