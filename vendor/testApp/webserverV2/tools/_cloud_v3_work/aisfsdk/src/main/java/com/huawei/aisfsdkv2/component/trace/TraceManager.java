/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.trace;

/**
 * 链路追踪管理器
 *
 * <p>基于 ThreadLocal 管理当前线程的任务上下文信息，供日志、消息等模块使用。</p>
 *
 * <p>使用方式：
 * <pre>
 *   // 任务开始时设置
 *   TraceManager.getInstance().setCurrentTaskId(taskId);
 *
 *   // 任务结束时清除（防止ThreadLocal内存泄漏）
 *   TraceManager.getInstance().clearCurrentTaskId();
 * </pre>
 * </p>
 */
public class TraceManager {

    private static class Holder {
        private static final TraceManager INSTANCE = new TraceManager();
    }

    /**
     * 获取 TraceManager 单例
     *
     * @return TraceManager 单例实例
     */
    public static TraceManager getInstance() {
        return Holder.INSTANCE;
    }

    /** 当前线程的任务ID */
    private final ThreadLocal<String> currentTaskId = new ThreadLocal<>();

    private TraceManager() {}

    /**
     * 设置当前线程的任务ID
     *
     * <p>在处理某个任务的线程入口处调用，使该线程后续所有日志都能携带对应的 taskId。</p>
     *
     * @param taskId 任务ID，传入 null 等同于调用 {@link #clearCurrentTaskId()}
     */
    public void setCurrentTaskId(String taskId) {
        currentTaskId.set(taskId);
    }

    /**
     * 获取当前线程的任务ID
     *
     * @return 当前线程的任务ID，未设置时返回空字符串
     */
    public String getCurrentTaskId() {
        String taskId = currentTaskId.get();
        return taskId != null ? taskId : "";
    }

    /**
     * 清除当前线程的任务ID
     *
     * <p>在任务处理完成或线程复用前调用，防止 ThreadLocal 内存泄漏。</p>
     */
    public void clearCurrentTaskId() {
        currentTaskId.remove();
    }
}
