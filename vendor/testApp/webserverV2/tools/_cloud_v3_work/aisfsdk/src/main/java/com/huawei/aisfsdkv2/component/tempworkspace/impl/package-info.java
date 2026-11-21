/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 临时工作空间实现包
 *
 * <p>本包提供 {@link com.huawei.aisfsdkv2.component.tempworkspace.TempWorkspaceManager} 接口的实现。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.tempworkspace.impl.TempWorkspaceManagerImpl}：临时工作空间管理器实现</li>
 * </ul>
 *
 * <h3>实现细节</h3>
 * <ul>
 *   <li>临时目录创建于应用缓存目录下</li>
 *   <li>使用定时任务清理过期目录</li>
 *   <li>目录名包含时间戳避免冲突</li>
 *   <li>清理时递归删除目录内容</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <p>本包类仅供 SDK 内部使用，业务代码应通过 {@code TempWorkspaceManager} 接口访问。</p>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.tempworkspace.impl;
