/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.threadpool;

import com.huawei.aisfsdkv2.component.threadpool.impl.ThreadPoolManagerImpl;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 线程池管理器接口
 */
public interface ThreadPoolManager {

    /**
     * 获取线程池管理器实例（单例）
     *
     * @return ThreadPoolManager 实例
     */
    static ThreadPoolManager getInstance() {
        return ThreadPoolManagerImpl.getInstance();
    }

    /**
     * 获取共享线程池
     *
     * @return Executor 共享线程池实例
     */
    Executor getShareThreadPool();

    /**
     * 提交任务到共享线程池异步执行
     *
     * @param runnable 任务
     */
    void executeSharedThreadPool(Runnable runnable);

    /**
     * 提交任务到独立线程池异步执行
     *
     * @param runnable 任务
     */
    void executeIndependentThreadPool(Runnable runnable);

    /**
     * 提交任务到临时线程池异步执行
     *
     * @param runnable 任务
     */
    void executeTempThreadPool(Runnable runnable);

    /**
     * 添加定时任务（任务执行在共享线程池中执行）
     *
     * @param runnable     任务
     * @param initialDelay 初始化延迟
     * @param delay        间隔
     * @param unit         间隔单位
     * @return ScheduledFuture 用于取消任务
     */
    ScheduledFuture<?> addScheduleTaskSharedThreadPool(Runnable runnable, long initialDelay, long delay, TimeUnit unit);

    /**
     * 添加定时任务（任务执行在独立线程池中执行）
     *
     * @param runnable     任务
     * @param initialDelay 初始化延迟
     * @param delay        间隔
     * @param unit         间隔单位
     * @return ScheduledFuture 用于取消任务
     */
    ScheduledFuture<?> addScheduleTaskIndependentThreadPool(Runnable runnable, long initialDelay, long delay, TimeUnit unit);

    /**
     * 添加一次性延迟任务（任务执行在共享线程池中执行）
     *
     * <p>与 {@link #addScheduleTaskSharedThreadPool} 不同，此方法只执行一次，
     * 不周期重复。</p>
     *
     * @param runnable 任务
     * @param delay    延迟时间
     * @param unit     时间单位
     * @return ScheduledFuture 用于取消任务
     */
    ScheduledFuture<?> scheduleOnceSharedThreadPool(Runnable runnable, long delay, TimeUnit unit);

    /**
     * 关闭所有线程池
     *
     * <p>在应用退出时调用，释放所有线程资源，防止内存泄漏。
     */
    void shutdown();
}
