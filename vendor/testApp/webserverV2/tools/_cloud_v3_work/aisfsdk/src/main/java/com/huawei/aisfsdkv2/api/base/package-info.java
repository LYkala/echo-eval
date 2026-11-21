/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 基础 API 接口包
 *
 * <p>本包提供 SDK 基础配置和管理能力，包括版本信息、设备信息、服务器配置等。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.api.base.BaseApi}：基础接口，定义 SDK 通用操作</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.base.DeviceInfo}：设备信息数据类</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.base.DeviceTypeEnum}：设备类型枚举</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.base.KeyFileInfo}：关键文件信息</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.base.ServerInfo}：服务器信息配置</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取基础 API 实例
 *   BaseApi baseApi = SdkManager.getInstance().getBaseApi();
 *
 *   // 设置 Android 上下文（必需，应在 Application.onCreate 中调用）
 *   baseApi.setAppContext(applicationContext);
 *
 *   // 配置设备信息
 *   DeviceInfo deviceInfo = new DeviceInfo();
 *   deviceInfo.setDeviceType(DeviceTypeEnum.PHONE);
 *   baseApi.setDeviceInfo(deviceInfo);
 *
 *   // 配置服务器信息
 *   ServerInfo serverInfo = new ServerInfo("https://api.example.com");
 *   baseApi.setServerInfo(serverInfo);
 *
 *   // 获取 SDK 版本
 *   String sdkVersion = baseApi.getSdkVersion();
 * </pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>在 Application.onCreate 中尽早设置 AppContext 和服务器信息</li>
 *   <li>设备信息应在登录前配置完成</li>
 *   <li>关键文件信息用于证书等敏感数据的安全存储配置</li>
 *   <li>SDK 角色名称用于区分不同业务场景</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.api.base;
