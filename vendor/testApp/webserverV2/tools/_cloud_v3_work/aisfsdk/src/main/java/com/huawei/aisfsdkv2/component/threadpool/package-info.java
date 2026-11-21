/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 线程池组件包
 *
 * <p>本包提供 SDK 内部使用的线程池管理功能，支持多种线程池类型和定时任务调度。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager}：线程池管理器接口</li>
 * </ul>
 *
 * <h3>子包说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.threadpool.impl}：线程池管理器实现</li>
 * </ul>
 *
 * <h3>线程池类型</h3>
 * <ul>
 *   <li><b>共享线程池</b>：适用于普通异步任务，线程复用</li>
 *   <li><b>独立线程池</b>：适用于重要任务，避免被其他任务阻塞</li>
 *   <li><b>临时线程池</b>：适用于一次性任务，用完即弃</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取线程池管理器
 *   ThreadPoolManager poolManager = ThreadPoolManager.getInstance();
 *
 *   // 提交任务到共享线程池
 *   poolManager.executeSharedThreadPool(() -&gt; {
 *       // 异步执行任务
 *   });
 *
 *   // 提交定时任务
 *   ScheduledFuture&lt;?&gt; future = poolManager.addScheduleTaskSharedThreadPool(
 *       () -&gt; { // 定时任务 },
 *       0,      // 初始延迟
 *       60,     // 间隔
 *       TimeUnit.SECONDS
 *   );
 *
 *   // 取消定时任务
 *   future.cancel(false);
 *
 *   // 应用退出时关闭所有线程池
 *   poolManager.shutdown();
 * </pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>普通异步任务使用共享线程池</li>
 *   <li>重要或耗时任务使用独立线程池</li>
 *   <li>定时任务使用带返回值的提交方法，便于取消</li>
 *   <li>应用退出时调用 shutdown 释放资源</li>
 *   <li>避免在任务中执行阻塞操作</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.threadpool;
