/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 会话管理模块包
 *
 * <p>本包提供会话的生命周期管理功能，包括创建、销毁、消息收发和状态管理。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.session.SessionManager}：会话管理器接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.session.Session}：会话接口</li>
 * </ul>
 *
 * <h3>子包说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.session.impl}：会话管理器实现</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <p>会话管理通过 {@code SessionManager} 接口进行，业务代码不应直接访问本包实现类。</p>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.session;
