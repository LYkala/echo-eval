/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 登录模块包
 *
 * <p>本包提供用户登录认证的核心功能，包括 Token 管理、连接管理和消息处理。</p>
 *
 * <h3>子包说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.login.auth}：Token 认证管理</li>
 *   <li>{@link com.huawei.aisfsdkv2.login.connection}：WebSocket 连接管理</li>
 * </ul>
 *
 * <h3>登录流程</h3>
 * <ol>
 *   <li>调用认证接口获取 Token</li>
 *   <li>使用 Token 建立 WebSocket 连接</li>
 *   <li>连接成功后进入已登录状态</li>
 *   <li>Token 过期前自动刷新</li>
 * </ol>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.login;
