/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 日志 API 实现包
 *
 * <p>本包提供 {@link com.huawei.aisfsdkv2.api.log.LogApi} 接口的实现，
 * 管理 SDK 日志配置、本地日志存储和日志上传功能。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.apiimpl.log.LogApiImpl}：日志 API 实现类</li>
 * </ul>
 *
 * <h3>实现细节</h3>
 * <ul>
 *   <li>日志级别动态可调整，即时生效</li>
 *   <li>本地日志文件自动轮转，避免占用过多存储</li>
 *   <li>日志上传使用独立 HTTP 客户端，避免阻塞业务</li>
 *   <li>支持崩溃日志自动捕获和上报</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <p>本包类仅供 SDK 内部使用，业务代码应通过 {@link com.huawei.aisfsdkv2.SdkManager#getLogApi()} 访问。</p>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.apiimpl.log;
