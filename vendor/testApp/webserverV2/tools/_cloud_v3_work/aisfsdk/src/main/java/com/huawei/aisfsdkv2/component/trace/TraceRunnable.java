/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.trace;

/**
 * Runnable 装饰器，用于跨线程传播 TraceManager 任务上下文。
 *
 * <p>工作流程：
 * <ol>
 *   <li>构造时（提交线程）：从 TraceManager 快照当前 taskId</li>
 *   <li>{@link #run()} 开始时（工作线程）：将快照 taskId 注入 TraceManager</li>
 *   <li>{@link #run()} 结束时（工作线程）：清除 TraceManager 中的 taskId，防止内存泄漏</li>
 * </ol>
 * </p>
 *
 * <p>使用示例：
 * <pre>
 *   threadPool.execute(new TraceRunnable(originalRunnable));
 * </pre>
 * </p>
 */
public class TraceRunnable implements Runnable {

    private final Runnable delegate;
    private final String snapshotTaskId;

    /**
     * 构造 TraceRunnable，在当前（提交）线程快照 taskId。
     *
     * @param delegate 被装饰的原始 Runnable，不能为 null
     */
    public TraceRunnable(Runnable delegate) {
        this.delegate = delegate;
        this.snapshotTaskId = TraceManager.getInstance().getCurrentTaskId();
    }

    /**
     * 执行被装饰的 Runnable，执行前注入快照 taskId，执行后清除。
     */
    @Override
    public void run() {
        try {
            TraceManager.getInstance().setCurrentTaskId(snapshotTaskId);
            delegate.run();
        } finally {
            TraceManager.getInstance().clearCurrentTaskId();
        }
    }
}
