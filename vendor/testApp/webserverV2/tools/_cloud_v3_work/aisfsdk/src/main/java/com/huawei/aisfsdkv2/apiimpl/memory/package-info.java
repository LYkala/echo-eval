/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 记忆管理 API 实现包
 *
 * <p>本包提供 {@link com.huawei.aisfsdkv2.api.memory.MemoryApi} 接口的实现，
 * 通过 ACM 服务 HTTP 接口实现记忆数据的查询和管理。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.apiimpl.memory.MemoryApiImpl}：记忆 API 实现类</li>
 * </ul>
 *
 * <h3>实现细节</h3>
 * <ul>
 *   <li>所有操作通过 ACM Facade 调用服务端接口</li>
 *   <li>支持记忆功能开关状态本地缓存</li>
 *   <li>分页查询自动处理分页参数和结果组装</li>
 *   <li>个人分类记忆按 topic 分组返回</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <p>本包类仅供 SDK 内部使用，业务代码应通过 {@link com.huawei.aisfsdkv2.SdkManager#getMemoryApi()} 访问。</p>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.apiimpl.memory;
