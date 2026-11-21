/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 会话管理 API 实现包
 *
 * <p>本包提供 {@link com.huawei.aisfsdkv2.api.session.SessionApi} 接口的实现，
 * 管理会话生命周期、消息收发和流式响应处理。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.apiimpl.session.SessionApiImpl}：会话 API 实现类</li>
 * </ul>
 *
 * <h3>实现细节</h3>
 * <ul>
 *   <li>会话使用 UUID 唯一标识，支持多会话并行</li>
 *   <li>消息通过 WebSocket 通道发送，支持断线重连</li>
 *   <li>流式响应通过回调逐段返回，支持中断控制</li>
 *   <li>会话结束自动清理资源，避免内存泄漏</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <p>本包类仅供 SDK 内部使用，业务代码应通过 {@link com.huawei.aisfsdkv2.SdkManager#getSessionApi()} 访问。</p>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.apiimpl.session;
