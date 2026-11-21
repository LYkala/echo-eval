/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.timertask;

import com.huawei.aisfsdkv2.api.timertask.TaskSchedule;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.util.TimeUtil;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 一次性定时任务管理器
 *
 * <p>负责管理 {@link com.huawei.aisfsdkv2.api.timertask.ScheduleTypeEnum#ONCE ONCE} 类型的定时任务。
 * 到达指定时间戳后触发一次 {@link java.util.function.Consumer#accept(Object)}，触发完成后自动移除任务。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 *   SingleTaskManager.getInstance().addTask(taskSchedule, task -&gt; {
 *       // 处理任务触发
 *   });
 * </pre>
 *
 * @since 2026-04-08
 */
public class SingleTaskManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("SingleTaskManager");

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ConcurrentHashMap<String, ScheduledFuture<?>> taskFutures = new ConcurrentHashMap<>();

    private SingleTaskManager() {
    }

    private static class Holder {
        private static final SingleTaskManager INSTANCE = new SingleTaskManager();
    }

    /**
     * 获取单例实例
     *
     * @return {@link SingleTaskManager} 单例
     */
    public static SingleTaskManager getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 添加一次性定时任务
     *
     * <p>根据 {@code task.getSchedule().getTriggerTime()} 中的毫秒时间戳计算延迟时间，
     * 到时触发 {@code callback.accept(task)}，触发后自动从管理器中移除。
     * 若 taskId 已存在，则忽略本次添加并记录日志。
     * 若 triggerTime 小于等于当前时间，则忽略该任务并记录警告日志。</p>
     *
     * @param task     任务信息，包含任务ID和调度信息，不可为 null
     * @param callback 触发回调，不可为 null
     */
    public void addTask(TaskSchedule task, Consumer<TaskSchedule> callback) {
        if (task == null || task.getTaskId() == null) {
            LOGGER.warn("SingleTaskManager.addTask: task or taskId is null, skip");
            return;
        }
        if (callback == null) {
            LOGGER.warn("SingleTaskManager.addTask: callback is null, skip taskId={}", task.getTaskId());
            return;
        }
        if (task.getSchedule() == null || task.getSchedule().getTriggerTime() == null) {
            LOGGER.warn("SingleTaskManager.addTask: schedule or triggerTime is null, skip taskId={}", task.getTaskId());
            return;
        }
        long triggerTimeMs = TimeUtil.parseTriggerTime(task.getSchedule().getTriggerTime());
        if (triggerTimeMs <= 0) {
            LOGGER.error("SingleTaskManager.addTask: invalid triggerTime={}, taskId={}",
                    task.getSchedule().getTriggerTime(), task.getTaskId());
            return;
        }
        long now = System.currentTimeMillis();
        long delayMs = triggerTimeMs - now;
        if (delayMs <= 0) {
            LOGGER.warn("SingleTaskManager.addTask: triggerTime={} is not in the future, skip taskId={}",
                    triggerTimeMs, task.getTaskId());
            return;
        }
        String taskId = task.getTaskId();
        if (taskFutures.containsKey(taskId)) {
            LOGGER.info("SingleTaskManager.addTask: taskId={} already exists, skip", taskId);
            return;
        }
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            LOGGER.info("SingleTaskManager: triggering taskId={}", taskId);
            try {
                callback.accept(task);
            } catch (Exception e) {
                LOGGER.error("SingleTaskManager: callback threw exception for taskId={}", taskId, e);
            } finally {
                taskFutures.remove(taskId);
                LOGGER.info("SingleTaskManager: task removed after trigger, taskId={}", taskId);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
        taskFutures.put(taskId, future);
        LOGGER.info("SingleTaskManager.addTask: scheduled taskId={}, delayMs={}", taskId, delayMs);
    }

    /**
     * 移除并取消指定任务
     *
     * <p>若任务不存在或已完成，则不做任何操作。</p>
     *
     * @param taskId 任务ID，不可为 null
     */
    public void removeTask(String taskId) {
        if (taskId == null) {
            LOGGER.warn("SingleTaskManager.removeTask: taskId is null, skip");
            return;
        }
        ScheduledFuture<?> future = taskFutures.remove(taskId);
        if (future != null) {
            future.cancel(false);
            LOGGER.info("SingleTaskManager.removeTask: cancelled taskId={}", taskId);
        } else {
            LOGGER.info("SingleTaskManager.removeTask: taskId={} not found", taskId);
        }
    }

    /**
     * 清理所有任务（不关闭调度器）
     *
     * <p>取消并移除所有待执行任务，调度器仍可接受新任务。</p>
     */
    public void clearAll() {
        LOGGER.info("SingleTaskManager.clearAll: clearing all tasks, count={}", taskFutures.size());
        for (ScheduledFuture<?> future : taskFutures.values()) {
            future.cancel(false);
        }
        taskFutures.clear();
        LOGGER.info("SingleTaskManager.clearAll: completed");
    }

    /**
     * 停止所有任务并关闭调度器
     *
     * <p>调用后当前实例不再接受新任务。若需再次使用，请重新调用 {@link #getInstance()} 并注意
     * 单例模式下调度器已被关闭，需重启应用或重新初始化。</p>
     */
    public void shutdown() {
        LOGGER.info("SingleTaskManager.shutdown: shutting down all tasks, count={}", taskFutures.size());
        for (ScheduledFuture<?> future : taskFutures.values()) {
            future.cancel(false);
        }
        taskFutures.clear();
        scheduler.shutdown();
        LOGGER.info("SingleTaskManager.shutdown: completed");
    }
}
