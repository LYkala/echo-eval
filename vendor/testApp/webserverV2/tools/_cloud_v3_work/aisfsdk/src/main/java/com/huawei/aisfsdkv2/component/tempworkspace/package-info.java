/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 临时工作空间组件包
 *
 * <p>本包提供临时目录管理功能，用于文件解压、拷贝等临时操作的工作空间申请和自动清理。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.tempworkspace.TempWorkspaceManager}：临时工作空间管理器接口</li>
 * </ul>
 *
 * <h3>子包说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.tempworkspace.impl}：临时工作空间管理器实现</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取临时工作空间管理器
 *   TempWorkspaceManager tempManager = TempWorkspaceManager.getInstance();
 *
 *   // 申请临时工作空间（1小时后过期）
 *   String workspace = tempManager.applyTempWorkspace(60 * 60 * 1000);
 *
 *   // 在临时目录中执行文件操作
 *   File tempFile = new File(workspace, "temp.zip");
 *   // ... 解压、拷贝等操作
 *
 *   // 手动清理临时工作空间
 *   tempManager.cleanTempWorkspace(workspace);
 * </pre>
 *
 * <h3>功能特性</h3>
 * <ul>
 *   <li>自动创建临时目录</li>
 *   <li>支持设置过期时间</li>
 *   <li>自动定时清理过期目录</li>
 *   <li>手动清理接口</li>
 * </ul>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>申请时设置合理的过期时间</li>
 *   <li>操作完成后及时手动清理</li>
 *   <li>不要存储重要数据到临时目录</li>
 *   <li>临时目录位于应用私有存储，其他应用无法访问</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.tempworkspace;
