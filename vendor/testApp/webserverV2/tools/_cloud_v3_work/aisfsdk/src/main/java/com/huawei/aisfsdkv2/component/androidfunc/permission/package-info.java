/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 权限管理组件包
 *
 * <p>本包提供 Android 运行时权限的申请和管理功能，用于处理敏感权限的动态申请。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.permission.PermissionRequestActivity}：权限申请透明 Activity</li>
 * </ul>
 *
 * <h3>功能特性</h3>
 * <ul>
 *   <li>透明 Activity 方式申请权限，不干扰用户体验</li>
 *   <li>支持单个或多个权限批量申请</li>
 *   <li>自动处理权限申请结果回调</li>
 *   <li>适配不同 Android 版本的权限模型</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <p>权限申请通过透明 Activity 实现，SDK 内部自动处理，业务代码无需直接调用本包类。</p>
 *
 * <h3>注意事项</h3>
 * <ul>
 *   <li>需要在 AndroidManifest.xml 中声明 PermissionRequestActivity</li>
 *   <li>权限申请是异步操作，结果通过回调返回</li>
 *   <li>用户拒绝权限后应提供降级方案</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.androidfunc.permission;
