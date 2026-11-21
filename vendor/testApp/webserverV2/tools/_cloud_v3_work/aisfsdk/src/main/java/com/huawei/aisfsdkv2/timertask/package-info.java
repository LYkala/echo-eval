/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 定时任务管理模块
 *
 * <p>本包提供完整的定时任务调度能力，支持一次性任务和周期性（cron）任务两种类型。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.timertask.TimerTaskManager}：
 *       总入口，负责从服务端拉取任务列表并按类型分发</li>
 *   <li>{@link com.huawei.aisfsdkv2.timertask.SingleTaskManager}：
 *       一次性任务管理器，到达指定时间戳后触发一次</li>
 *   <li>{@link com.huawei.aisfsdkv2.timertask.CrontabTaskManager}：
 *       周期性任务管理器，按 cron 表达式每分钟整点检查并触发</li>
 *   <li>{@link com.huawei.aisfsdkv2.timertask.OnTaskTriggerListener}：
 *       任务触发统一回调接口</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 1. 初始化，注册全局回调
 *   TimerTaskManager.getInstance().start(task -&gt; {
 *       // 所有任务触发都来这里
 *   });
 *
 *   // 2. 动态添加任务（无需再传回调）
 *   TimerTaskManager.getInstance().addTask(task);
 *
 *   // 3. 动态删除任务
 *   TimerTaskManager.getInstance().removeTask("task_001");
 *
 *   // 4. 停止所有任务
 *   TimerTaskManager.getInstance().stop();
 * </pre>
 *
 * <h3>任务分发规则</h3>
 * <ul>
 *   <li>{@code enable=false} 的任务将被自动过滤，不参与调度</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.timertask.ScheduleTypeEnum#ONCE ONCE}
 *       类型任务通过 {@code triggerTime}（毫秒时间戳）调度，触发一次后自动移除</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.timertask.ScheduleTypeEnum#RECURRING RECURRING}
 *       类型任务通过 cron 表达式调度，持续周期触发</li>
 * </ul>
 *
 * <h3>子包说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.timertask.cron}：Cron 表达式解析</li>
 * </ul>
 *
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>在应用启动时初始化定时任务管理器</li>
 *   <li>任务触发回调中执行耗时操作应使用异步线程</li>
 *   <li>动态添加的任务优先级高于服务端拉取的任务</li>
 *   <li>应用退出时调用 stop() 停止所有任务调度</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.timertask;
