/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 定时任务 API 实现包
 *
 * <p>本包提供 {@link com.huawei.aisfsdkv2.api.timertask.TimerTaskApi} 接口的实现，
 * 通过 ACM 服务 HTTP 接口实现定时任务的管理。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.apiimpl.timertask.TimerTaskApiImpl}：定时任务 API 实现类</li>
 * </ul>
 *
 * <h3>实现细节</h3>
 * <ul>
 *   <li>所有操作通过 ACM Facade 调用服务端接口</li>
 *   <li>任务能力列表包含 Skill、Agent、API 三类</li>
 *   <li>创建/修改/删除操作返回统一的数据模型</li>
 *   <li>任务调度由服务端管理，SDK 负责拉取和执行</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <p>本包类仅供 SDK 内部使用，业务代码应通过 {@link com.huawei.aisfsdkv2.SdkManager#getTimerTaskApi()} 访问。</p>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.apiimpl.timertask;
