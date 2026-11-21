/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 登录认证 API 实现包
 *
 * <p>本包提供 {@link com.huawei.aisfsdkv2.api.login.LoginApi} 接口的实现，
 * 管理用户登录、登出流程和登录状态。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.apiimpl.login.LoginApiImpl}：登录 API 实现类</li>
 *   <li>{@link com.huawei.aisfsdkv2.apiimpl.login.LoginFlowCallback}：登录流程回调</li>
 *   <li>{@link com.huawei.aisfsdkv2.apiimpl.login.LoginStateMachine}：登录状态机</li>
 * </ul>
 *
 * <h3>实现细节</h3>
 * <ul>
 *   <li>登录流程：认证 -&gt; 获取 Token -&gt; 建立 WebSocket 连接</li>
 *   <li>支持多回调监听，登录结果统一分发</li>
 *   <li>状态机管理登录各阶段状态，避免重复操作</li>
 *   <li>登出时自动清理 Token、关闭连接、释放资源</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <p>本包类仅供 SDK 内部使用，业务代码应通过 {@link com.huawei.aisfsdkv2.SdkManager#getLoginApi()} 访问。</p>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.apiimpl.login;
