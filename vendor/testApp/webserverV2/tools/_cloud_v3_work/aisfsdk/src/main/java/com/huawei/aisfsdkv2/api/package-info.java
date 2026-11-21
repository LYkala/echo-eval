/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * AISF SDK V2 公共 API 层
 *
 * <p>本包定义 SDK 对外暴露的所有公共接口，采用门面模式（Facade Pattern）设计，
 * 为开发者提供统一、简洁的 SDK 功能访问入口。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.api.base.BaseApi}：基础接口，提供 SDK 版本、设备信息、服务器配置等通用能力</li>
 * </ul>
 *
 * <h3>子包说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.api.base}：基础 API 接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.log}：日志配置接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.login}：登录认证接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.memory}：记忆管理接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.session}：会话管理接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.skill}：技能管理接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.timertask}：定时任务接口</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取基础 API 实例
 *   BaseApi baseApi = SdkManager.getInstance().getBaseApi();
 *
 *   // 设置服务器信息
 *   ServerInfo serverInfo = new ServerInfo("https://api.example.com");
 *   baseApi.setServerInfo(serverInfo);
 *
 *   // 获取 SDK 版本
 *   String version = baseApi.getSdkVersion();
 * </pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>所有 API 接口均通过 {@code SdkManager} 统一获取，避免直接实例化实现类</li>
 *   <li>API 调用前确保已完成 SDK 初始化（设置 Context、服务器地址等）</li>
 *   <li>异步操作的回调均在主线程执行，可直接更新 UI</li>
 *   <li>遵循接口隔离原则，只依赖需要的 API 接口</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.api;
