/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.threadpool.impl;

import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.component.trace.TraceRunnable;

import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池管理器实现
 *
 * <p>管理三种类型的线程池：
 * <ul>
 *   <li>共享线程池：用于普通任务执行，核心线程30，最大线程30，支持核心线程老化（5秒）</li>
 *   <li>独立线程池：用于独立任务执行，核心线程30，最大线程30，支持核心线程老化（5秒）</li>
 *   <li>定时任务执行器：单线程，用于定时任务调度</li>
 * </ul>
 *
 * <p>线程池特性：
 * <ul>
 *   <li>使用无界队列（LinkedBlockingDeque），任务不会拒绝但可能排队</li>
 *   <li>核心线程允许超时销毁，空闲5秒后自动释放</li>
 *   <li>所有线程均为守护线程（daemon），不阻止JVM退出</li>
 * </ul>
 */
public class ThreadPoolManagerImpl implements ThreadPoolManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("ThreadPoolManagerImpl");

    private static final ThreadPoolManagerImpl INSTANCE = new ThreadPoolManagerImpl();

    /**
     * 共享线程池：核心线程数30，确保任务快速执行
     */
    private static final int SHARED_CORE_POOL_SIZE = 30;

    /**
     * 共享线程池：最大线程数（与核心线程数相同，避免线程频繁创建销毁）
     */
    private static final int SHARED_MAX_POOL_SIZE = 30;

    /**
     * 共享线程池：线程保活时间（5秒）
     */
    private static final int SHARED_KEEP_ALIVE_TIME_SECOND = 5;

    /**
     * 独立线程池：核心线程数30，确保任务快速执行
     */
    private static final int INDEPENDENT_CORE_POOL_SIZE = 30;

    /**
     * 独立线程池：最大线程数（与核心线程数相同，避免线程频繁创建销毁）
     */
    private static final int INDEPENDENT_MAX_POOL_SIZE = 30;

    /**
     * 独立线程池：线程保活时间（5秒）
     */
    private static final int INDEPENDENT_KEEP_ALIVE_TIME_SECOND = 5;

    /**
     * 共享线程池
     */
    private final ThreadPoolExecutor sharedThreadPool;

    /**
     * 独立线程池
     */
    private final ThreadPoolExecutor independentThreadPool;

    /**
     * 定时任务执行器
     */
    private final ScheduledExecutorService scheduledExecutorService;

    /**
     * 获取单例实例（供接口调用）
     *
     * @return ThreadPoolManagerImpl 实例
     */
    public static ThreadPoolManagerImpl getInstance() {
        return INSTANCE;
    }

    private ThreadPoolManagerImpl() {
        this.sharedThreadPool = new ThreadPoolExecutor(
                SHARED_CORE_POOL_SIZE,
                SHARED_MAX_POOL_SIZE,
                SHARED_KEEP_ALIVE_TIME_SECOND,
                TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(),
                new ThreadFactory() {
                    private static final String THREAD_NAME = "AISF_SHARED";
                    private final AtomicInteger counter = new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable runnable) {
                        Thread thread = new Thread(runnable, String.format(Locale.ENGLISH, "%s-%d", THREAD_NAME, counter.getAndAdd(1)));
                        thread.setDaemon(true);
                        return thread;
                    }
                }
        );
        this.sharedThreadPool.allowCoreThreadTimeOut(Boolean.TRUE);

        this.independentThreadPool = new ThreadPoolExecutor(
                INDEPENDENT_CORE_POOL_SIZE,
                INDEPENDENT_MAX_POOL_SIZE,
                INDEPENDENT_KEEP_ALIVE_TIME_SECOND,
                TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(),
                new ThreadFactory() {
                    private static final String THREAD_NAME = "AISF_INDEPENDENT";
                    private final AtomicInteger counter = new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable runnable) {
                        Thread thread = new Thread(runnable, String.format(Locale.ENGLISH, "%s-%d", THREAD_NAME, counter.getAndAdd(1)));
                        thread.setDaemon(true);
                        return thread;
                    }
                }
        );
        this.independentThreadPool.allowCoreThreadTimeOut(Boolean.TRUE);

        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * 获取共享线程池
     *
     * @return Executor 共享线程池实例
     */
    @Override
    public Executor getShareThreadPool() {
        return this.sharedThreadPool;
    }

    /**
     * 在共享线程池中执行任务
     *
     * <p>共享线程池用于执行普通任务，如消息发送等。
     *
     * @param runnable 要执行的任务
     */
    @Override
    public void executeSharedThreadPool(Runnable runnable) {
        this.sharedThreadPool.execute(new TraceRunnable(runnable));
    }

    /**
     * 在独立线程池中执行任务
     *
     * <p>执行前打印线程池状态（活跃线程数/最大线程数/队列大小），便于监控和调优。
     *
     * @param runnable 要执行的任务
     */
    @Override
    public void executeIndependentThreadPool(Runnable runnable) {
        LOGGER.info("executeIndependentThreadPool: active={}/{}, poolSize={}, queueSize={}",
                independentThreadPool.getActiveCount(),
                independentThreadPool.getMaximumPoolSize(),
                independentThreadPool.getPoolSize(),
                independentThreadPool.getQueue().size());
        this.independentThreadPool.execute(new TraceRunnable(runnable));
    }

    /**
     * 在临时线程中执行任务
     *
     * <p>创建一个新的独立线程执行任务，不经过线程池管理。
     * 适用于需要完全隔离执行环境的场景。
     *
     * @param runnable 要执行的任务
     */
    @Override
    public void executeTempThreadPool(Runnable runnable) {
        Thread thread = new Thread(new TraceRunnable(runnable));
        thread.start();
    }

    /**
     * 添加定时任务到共享线程池
     *
     * <p>使用固定延迟模式（fixed delay），即上一次任务执行完毕后，
     * 等待指定延迟时间再执行下一次任务。
     *
     * @param runnable    要执行的任务
     * @param initialDelay 首次执行延迟时间
     * @param delay       后续执行间隔时间
     * @param unit        时间单位
     * @return ScheduledFuture 用于取消任务
     */
    @Override
    public ScheduledFuture<?> addScheduleTaskSharedThreadPool(Runnable runnable, long initialDelay, long delay, TimeUnit unit) {
        return this.scheduledExecutorService.scheduleWithFixedDelay(
                () -> sharedThreadPool.execute(new TraceRunnable(runnable)),
                initialDelay,
                delay,
                unit
        );
    }

    /**
     * 添加定时任务到独立线程池
     *
     * <p>使用固定延迟模式（fixed delay），即上一次任务执行完毕后，
     * 等待指定延迟时间再执行下一次任务。
     *
     * @param runnable    要执行的任务
     * @param initialDelay 首次执行延迟时间
     * @param delay       后续执行间隔时间
     * @param unit        时间单位
     * @return ScheduledFuture 用于取消任务
     */
    @Override
    public ScheduledFuture<?> addScheduleTaskIndependentThreadPool(Runnable runnable, long initialDelay, long delay, TimeUnit unit) {
        return this.scheduledExecutorService.scheduleWithFixedDelay(
                () -> independentThreadPool.execute(new TraceRunnable(runnable)),
                initialDelay,
                delay,
                unit
        );
    }

    /**
     * 添加一次性延迟任务到共享线程池
     *
     * <p>使用 ScheduledExecutorService.schedule() 实现一次性延迟执行，
     * 实际任务执行委托给共享线程池，保持调度线程轻量。</p>
     *
     * @param runnable    要执行的任务
     * @param delay       延迟时间
     * @param unit        时间单位
     * @return ScheduledFuture 用于取消任务
     */
    @Override
    public ScheduledFuture<?> scheduleOnceSharedThreadPool(Runnable runnable, long delay, TimeUnit unit) {
        return this.scheduledExecutorService.schedule(
                () -> sharedThreadPool.execute(new TraceRunnable(runnable)),
                delay,
                unit
        );
    }

    /**
     * 关闭所有线程池
     *
     * <p>在应用退出时调用，释放所有线程资源，防止内存泄漏。
     */
    public void shutdown() {
        sharedThreadPool.shutdown();
        independentThreadPool.shutdown();
        scheduledExecutorService.shutdown();
    }
}
