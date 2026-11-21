/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 线程池实现包
 *
 * <p>本包提供 {@link com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager} 接口的实现。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.threadpool.impl.ThreadPoolManagerImpl}：线程池管理器实现</li>
 * </ul>
 *
 * <h3>实现细节</h3>
 * <ul>
 *   <li>共享线程池：核心线程数 4，最大线程数 8</li>
 *   <li>独立线程池：核心线程数 2，最大线程数 4</li>
 *   <li>临时线程池：单线程执行</li>
 *   <li>定时任务使用 ScheduledThreadPoolExecutor</li>
 *   <li>线程工厂设置有意义的线程名，便于调试</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <p>本包类仅供 SDK 内部使用，业务代码应通过 {@code ThreadPoolManager} 接口访问。</p>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.threadpool.impl;
