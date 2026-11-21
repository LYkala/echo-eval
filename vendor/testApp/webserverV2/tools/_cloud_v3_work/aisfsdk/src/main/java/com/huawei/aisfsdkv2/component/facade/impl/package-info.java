/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * ACM Facade 实现包
 *
 * <p>本包提供 {@link com.huawei.aisfsdkv2.component.facade.AcmFacade} 接口的实现，
 * 使用 HTTP 客户端与 ACM 服务通信。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.facade.impl.ACMFacadeImpl}：ACM Facade 实现</li>
 * </ul>
 *
 * <h3>实现细节</h3>
 * <ul>
 *   <li>使用 OkHttp 进行 HTTP 通信</li>
 *   <li>自动处理请求签名和 Token 附加</li>
 *   <li>支持文件上传的进度监控</li>
 *   <li>统一的错误码和异常处理</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <p>本包类仅供 SDK 内部使用，业务代码应通过 {@code AcmFacade} 接口访问。</p>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.facade.impl;
