/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 基础 API 实现包
 *
 * <p>本包提供 {@link com.huawei.aisfsdkv2.api.base.BaseApi} 接口的实现，
 * 管理 SDK 基础配置信息，包括版本、设备信息、服务器配置等。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.apiimpl.base.BaseApiImpl}：基础 API 实现类</li>
 * </ul>
 *
 * <h3>实现细节</h3>
 * <ul>
 *   <li>使用静态内部类实现单例模式</li>
 *   <li>配置信息存储在内存中，应用重启后需重新设置</li>
 *   <li>线程安全：配置读写操作已同步</li>
 *   <li>设备信息用于构建 identifyId，影响登录认证</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <p>本包类仅供 SDK 内部使用，业务代码应通过 {@link com.huawei.aisfsdkv2.SdkManager#getBaseApi()} 访问。</p>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.apiimpl.base;
