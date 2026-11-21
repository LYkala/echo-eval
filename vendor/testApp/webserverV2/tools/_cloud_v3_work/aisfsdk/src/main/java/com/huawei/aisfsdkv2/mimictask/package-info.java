/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 模仿任务模块包
 *
 * <p>本包处理特定类型的 UserOutput 消息，用于技能开始/停止时的无障碍事件监听控制。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.mimictask.MimicTaskMsgHandler}：模仿任务消息处理器</li>
 *   <li>{@link com.huawei.aisfsdkv2.mimictask.MimicAccessibilityEventHandler}：模仿无障碍事件处理器</li>
 *   <li>{@link com.huawei.aisfsdkv2.mimictask.ScheduleScreenshotManager}：定时截图管理器接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.mimictask.ScheduleScreenshotManagerImpl}：定时截图管理器实现</li>
 *   <li>{@link com.huawei.aisfsdkv2.mimictask.ScheduleScreenshotRecord}：定时截图记录</li>
 * </ul>
 *
 * <h3>处理的消息类型</h3>
 * <ul>
 *   <li>210001 - START_SKILL：开始技能，注册无障碍事件监听</li>
 *   <li>210003 - STOP_SKILL：停止技能，注销无障碍事件监听</li>
 *   <li>210006 - STOP_ERROR_SKILL：异常停止技能，注销无障碍事件监听</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.mimictask;
