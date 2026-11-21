/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.timertask;

import com.huawei.aisfsdkv2.api.timertask.TaskSchedule;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.timertask.cron.CronExpression;

import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 周期性（cron）定时任务管理器
 *
 * <p>负责管理 {@link com.huawei.aisfsdkv2.api.timertask.ScheduleTypeEnum#RECURRING RECURRING}
 * 类型的定时任务。内部使用一个后台线程，每分钟整点（在每分钟的第 0 秒）检查一次所有注册的
 * cron 任务，若当前时刻与 cron 表达式匹配则触发 {@link java.util.function.Consumer#accept(Object)}。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 *   CrontabTaskManager.getInstance().addTask(taskSchedule, task -&gt; {
 *       // 处理任务触发
 *   });
 * </pre>
 *
 * @since 2026-04-08
 */
public class CrontabTaskManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("CrontabTaskManager");
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int MILLIS_PER_SECOND = 1000;

    private final ConcurrentHashMap<String, TaskEntry> taskMap = new ConcurrentHashMap<>();    private volatile ScheduledFuture<?> checkFuture;

    private CrontabTaskManager() {
        startChecking();
    }

    private static class Holder {
        private static final CrontabTaskManager INSTANCE = new CrontabTaskManager();
    }

    /**
     * 获取单例实例
     *
     * @return {@link CrontabTaskManager} 单例
     */
    public static CrontabTaskManager getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 启动每分钟整点的检查调度
     *
     * <p>计算距离下一个整分钟的毫秒延迟，然后每 60 秒执行一次检查，
     * 使用 {@link ThreadPoolManager#addScheduleTaskIndependentThreadPool} 进行调度。</p>
     */
    private void startChecking() {
        long now = System.currentTimeMillis();
        long millisInCurrentMinute = now % (SECONDS_PER_MINUTE * MILLIS_PER_SECOND);
        long initialDelay = (SECONDS_PER_MINUTE * MILLIS_PER_SECOND) - millisInCurrentMinute;
        checkFuture = ThreadPoolManager.getInstance().addScheduleTaskIndependentThreadPool(
                this::checkAndTrigger, initialDelay, SECONDS_PER_MINUTE * MILLIS_PER_SECOND, TimeUnit.MILLISECONDS);
        LOGGER.info("CrontabTaskManager: started, first check in {}ms", initialDelay);
    }

    /**
     * 检查所有注册任务，触发匹配当前时刻 cron 表达式的任务
     */
    private void checkAndTrigger() {
        if (taskMap.isEmpty()) {
            return;
        }
        Calendar now = Calendar.getInstance();
        LOGGER.debug("CrontabTaskManager: checking {} tasks at minute={}, hour={}",
                taskMap.size(), now.get(Calendar.MINUTE), now.get(Calendar.HOUR_OF_DAY));
        for (Map.Entry<String, TaskEntry> entry : taskMap.entrySet()) {
            String taskId = entry.getKey();
            TaskEntry taskEntry = entry.getValue();
            try {
                if (taskEntry.cronExpression.matches(now)) {
                    LOGGER.info("CrontabTaskManager: triggering taskId={}", taskId);
                    taskEntry.callback.accept(taskEntry.task);
                }
            } catch (Exception e) {
                LOGGER.error("CrontabTaskManager: error triggering taskId={}", taskId, e);
            }
        }
    }

    /**
     * 添加周期性 cron 定时任务
     *
     * <p>任务到达 cron 表达式指定时刻时触发，可多次触发。若 taskId 已存在，则忽略本次添加并记录日志。
     * 若 cron 表达式解析失败，则忽略该任务并记录错误日志。</p>
     *
     * @param task     任务信息，包含任务ID和 cron 表达式，不可为 null
     * @param callback 触发回调，不可为 null
     */
    public void addTask(TaskSchedule task, Consumer<TaskSchedule> callback) {
        if (task == null || task.getTaskId() == null) {
            LOGGER.warn("CrontabTaskManager.addTask: task or taskId is null, skip");
            return;
        }
        if (callback == null) {
            LOGGER.warn("CrontabTaskManager.addTask: callback is null, skip taskId={}", task.getTaskId());
            return;
        }
        if (task.getSchedule() == null || task.getSchedule().getCron() == null) {
            LOGGER.warn("CrontabTaskManager.addTask: schedule or cron is null, skip taskId={}", task.getTaskId());
            return;
        }
        String cronStr = task.getSchedule().getCron();
        CronExpression cronExpression;
        try {
            cronExpression = CronExpression.parse(cronStr);
        } catch (IllegalArgumentException e) {
            LOGGER.error("CrontabTaskManager.addTask: invalid cron='{}' for taskId={}", cronStr, task.getTaskId(), e);
            return;
        }
        TaskEntry taskEntry = new TaskEntry(task, callback, cronExpression);
        if (taskMap.putIfAbsent(task.getTaskId(), taskEntry) != null) {
            LOGGER.info("CrontabTaskManager.addTask: taskId={} already exists, skip", task.getTaskId());
            return;
        }
        LOGGER.info("CrontabTaskManager.addTask: registered taskId={}, cron='{}'", task.getTaskId(), cronStr);
    }

    /**
     * 移除指定 cron 任务
     *
     * <p>若任务不存在，则不做任何操作。</p>
     *
     * @param taskId 任务ID，不可为 null
     */
    public void removeTask(String taskId) {
        if (taskId == null) {
            LOGGER.warn("CrontabTaskManager.removeTask: taskId is null, skip");
            return;
        }
        TaskEntry removed = taskMap.remove(taskId);
        if (removed != null) {
            LOGGER.info("CrontabTaskManager.removeTask: removed taskId={}", taskId);
        } else {
            LOGGER.info("CrontabTaskManager.removeTask: taskId={} not found", taskId);
        }
    }

    /**
     * 清理所有任务（不停止调度器）
     *
     * <p>移除所有任务，调度器仍可接受新任务。</p>
     */
    public void clearAll() {
        LOGGER.info("CrontabTaskManager.clearAll: clearing all tasks, count={}", taskMap.size());
        taskMap.clear();
        LOGGER.info("CrontabTaskManager.clearAll: completed");
    }

    /**
     * 停止调度并清理所有任务
     *
     * <p>调用后不再触发任何任务。</p>
     */
    public void shutdown() {
        LOGGER.info("CrontabTaskManager.shutdown: shutting down, taskCount={}", taskMap.size());
        if (checkFuture != null) {
            checkFuture.cancel(false);
        }
        taskMap.clear();
        LOGGER.info("CrontabTaskManager.shutdown: completed");
    }

    /**
     * 任务条目，聚合任务信息、回调和 cron 表达式
     */
    private static class TaskEntry {
        private final TaskSchedule task;
        private final Consumer<TaskSchedule> callback;
        private final CronExpression cronExpression;

        TaskEntry(TaskSchedule task, Consumer<TaskSchedule> callback, CronExpression cronExpression) {
            this.task = task;
            this.callback = callback;
            this.cronExpression = cronExpression;
        }
    }
}
