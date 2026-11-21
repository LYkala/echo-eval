/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.timertask;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.login.LoginStatus;
import com.huawei.aisfsdkv2.api.timertask.DeleteScheduleTaskRequest;
import com.huawei.aisfsdkv2.api.timertask.ScheduleTypeEnum;
import com.huawei.aisfsdkv2.api.timertask.TaskSchedule;
import com.huawei.aisfsdkv2.api.timertask.TimerTaskModel;
import com.huawei.aisfsdkv2.component.eventbus.EventBusManager;
import com.huawei.aisfsdkv2.component.eventbus.event.LoginStatusEvent;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.util.ThreadUtil;
import com.huawei.aisfsdkv2.util.TimeUtil;

import android.content.Context;

import java.util.List;
import java.util.function.Consumer;

/**
 * 定时任务总管理器
 *
 * <p>所有定时任务的统一入口，负责：</p>
 * <ul>
 *   <li>初始化时从服务端拉取任务列表并自动调度</li>
 *   <li>提供 {@link #addTask(TaskSchedule)} / {@link #removeTask(String)} 动态管理任务</li>
 *   <li>按任务类型路由到对应子管理器：
 *     <ul>
 *       <li>{@link ScheduleTypeEnum#ONCE}：路由到 {@link SingleTaskManager}，到期触发一次</li>
 *       <li>{@link ScheduleTypeEnum#RECURRING}：路由到 {@link CrontabTaskManager}，按 cron 周期触发</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>
 *   // 1. 注册全局回调（在 SDK 初始化前调用）
 *   TimerTaskManager.getInstance().setCallback(task -&gt; {
 *       // 所有任务触发都来这里
 *       handleTask(task);
 *   });
 *
 *   // 2. SDK 初始化时自动调用无参 start()，拉取并调度任务
 *   AISFSDKClient.getInstance().init(param);
 *
 *   // 3. 动态添加任务（无需再传回调）
 *   TimerTaskManager.getInstance().addTask(task);
 *
 *   // 4. 动态删除任务
 *   TimerTaskManager.getInstance().removeTask("task_001");
 *
 *   // 5. 停止所有任务
 *   TimerTaskManager.getInstance().stop();
 * </pre>
 *
 * @since 2026-04-08
 */
public class TimerTaskManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("TimerTaskManager");

    private static final String[] ALLOWED_PACKAGES = {
            "com.unicom.xiaowo.ttgui",
            "com.example.devicesdkdemo"
    };

    private volatile Consumer<TaskSchedule> globalCallback;

    private TimerTaskManager() {
        // 订阅登录成功事件，启动任务调度
        EventBusManager.getInstance().subscribeEvent(event -> {
            if (event instanceof LoginStatusEvent && ((LoginStatusEvent) event).isLoggedIn()) {
                userLoginPostHandler();
            }
        }, new LoginStatusEvent(LoginStatus.CONNECTED));
        // 订阅登出事件，清理所有任务
        EventBusManager.getInstance().subscribeEvent(event -> {
            if (event instanceof LoginStatusEvent && ((LoginStatusEvent) event).isLoggedOut()) {
                clearAllTasks();
            }
        }, new LoginStatusEvent(LoginStatus.IDLE));
    }

    private static class Holder {
        private static final TimerTaskManager INSTANCE = new TimerTaskManager();
    }

    // -------------------------------------------------------------------------
    // 单例 & 生命周期
    // -------------------------------------------------------------------------

    /**
     * 获取单例实例
     *
     * @return {@link TimerTaskManager} 单例
     */
    public static TimerTaskManager getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 启动定时任务管理
     *
     * <p>由 {@link com.huawei.aisfsdkv2.api.AISFSDKClient#init} 自动调用，
     * 使用已通过 {@link #setCallback(Consumer)} 注册的全局回调。
     * 若回调尚未注册，则任务触发时不执行任何操作（占位回调）。
     * 仅当当前应用包名在白名单中时才会继续执行。</p>
     */
    public void start() {
    }

    /**
     * 停止所有定时任务
     *
     * <p>分别调用 {@link SingleTaskManager#shutdown()} 和 {@link CrontabTaskManager#shutdown()}
     * 停止两类任务管理器，并清除全局回调。</p>
     */
    public void stop() {
        LOGGER.info("TimerTaskManager.stop: stopping all task managers");
        SingleTaskManager.getInstance().shutdown();
        CrontabTaskManager.getInstance().shutdown();
        globalCallback = null;
        LOGGER.info("TimerTaskManager.stop: all task managers stopped");
    }

    // -------------------------------------------------------------------------
    // 公共任务管理
    // -------------------------------------------------------------------------

    /**
     * 注册全局任务触发回调
     *
     * <p>所有任务触发时均调用此回调，建议在 SDK 初始化前调用。</p>
     *
     * @param callback 全局回调，不可为 null
     */
    public void setCallback(Consumer<TaskSchedule> callback) {
        if (callback == null) {
            LOGGER.warn("TimerTaskManager.setCallback: callback is null, skip");
            return;
        }
        this.globalCallback = wrapCallback(callback);
        LOGGER.info("TimerTaskManager.setCallback: callback registered");
    }

    /**
     * 动态添加定时任务
     *
     * <p>按任务类型路由到对应子管理器，使用已注册的全局回调触发。
     * 若尚未调用 {@link #start()}，则记录警告并忽略。</p>
     *
     * @param task 任务信息，不可为 null
     */
    public void addTask(TaskSchedule task) {
        if (globalCallback == null) {
            LOGGER.warn("TimerTaskManager.addTask: not started yet, call start() first, skip taskId={}",
                    task != null ? task.getTaskId() : "null");
            return;
        }
        dispatchTask(task);
    }

    /**
     * 动态删除定时任务
     *
     * <p>同时从 {@link SingleTaskManager} 和 {@link CrontabTaskManager} 中尝试移除，
     * 无需关心任务类型。</p>
     *
     * @param taskId 任务ID，不可为 null
     */
    public void removeTask(String taskId) {
        if (taskId == null) {
            LOGGER.warn("TimerTaskManager.removeTask: taskId is null, skip");
            return;
        }
        LOGGER.info("TimerTaskManager.removeTask: taskId={}", taskId);
        SingleTaskManager.getInstance().removeTask(taskId);
        CrontabTaskManager.getInstance().removeTask(taskId);
    }

    // -------------------------------------------------------------------------
    // 登录事件处理
    // -------------------------------------------------------------------------

    /**
     * 用户登录后处理器
     *
     * <p>使用 {@link ThreadPoolManager} 提交到共享线程池异步执行，
     * 调用 {@code TimerTaskApi.getScheduleTaskList()} 获取任务列表，
     * 过滤掉 {@code enable=false} 的任务，然后按 {@link ScheduleTypeEnum} 自动路由调度。</p>
     *
     * <p>如果请求失败，会进行重试：
     * <ul>
     *   <li>重试间隔：30 秒</li>
     *   <li>最大重试次数：5 次</li>
     * </ul>
     * </p>
     */
    private void userLoginPostHandler() {
        Context context = AISFSDKClient.getBaseApi().getAppContext();
        if (context == null) {
            LOGGER.warn("TimerTaskManager.userLoginPostHandler: appContext is null, skip");
            return;
        }
        String packageName = context.getPackageName();
        if (!isPackageAllowed(packageName)) {
            LOGGER.info("TimerTaskManager.userLoginPostHandler: packageName={} is not allowed, skip", packageName);
            return;
        }
        if (globalCallback == null) {
            LOGGER.warn("TimerTaskManager.userLoginPostHandler: callback not registered, skip");
            return;
        }
        ThreadPoolManager.getInstance().executeSharedThreadPool(() -> {
            List<TaskSchedule> taskList = fetchScheduleTaskListWithRetry();
            dispatchTaskList(taskList);
        });
    }

    /**
     * 带重试机制从服务端拉取定时任务列表
     *
     * <p>最多重试 5 次，间隔 30 秒，全部失败返回 null。</p>
     *
     * @return 任务列表，拉取失败返回 null
     */
    private List<TaskSchedule> fetchScheduleTaskListWithRetry() {
        LOGGER.info("TimerTaskManager.fetchScheduleTaskListWithRetry: loading schedule task list from server");
        final long retryIntervalMs = 30000;
        final int maxRetryCount = 5;
        int retryCount = 0;
        TimerTaskModel<List<TaskSchedule>> result = null;
        while (retryCount < maxRetryCount) {
            try {
                result = AISFSDKClient.getTimerTaskApi().getScheduleTaskList();
            } catch (Exception e) {
                LOGGER.error("TimerTaskManager.fetchScheduleTaskListWithRetry: failed, retry={}/{}", retryCount, maxRetryCount, e);
            }
            if (result != null && result.isSuccess()) {
                break;
            }
            if (result != null) {
                LOGGER.warn("TimerTaskManager.fetchScheduleTaskListWithRetry: retCode={}, retMsg={}, retry={}/{}",
                        result.getRetCode(), result.getRetMsg(), retryCount, maxRetryCount);
            }
            retryCount++;
            if (retryCount < maxRetryCount) {
                LOGGER.info("TimerTaskManager.fetchScheduleTaskListWithRetry: will retry in {}ms", retryIntervalMs);
                ThreadUtil.sleepNoExcept(retryIntervalMs);
            }
        }
        if (result == null || !result.isSuccess()) {
            LOGGER.error("TimerTaskManager.fetchScheduleTaskListWithRetry: failed after {} retries, give up", maxRetryCount);
            return null;
        }
        return result.getData();
    }

    /**
     * 分发任务列表到各子管理器
     *
     * @param taskList 待分发的任务列表，为空则跳过
     */
    private void dispatchTaskList(List<TaskSchedule> taskList) {
        if (taskList == null || taskList.isEmpty()) {
            LOGGER.info("TimerTaskManager.dispatchTaskList: no tasks to dispatch");
            return;
        }
        LOGGER.info("TimerTaskManager.dispatchTaskList: dispatching {} tasks", taskList.size());
        for (TaskSchedule task : taskList) {
            dispatchTask(task);
        }
        LOGGER.info("TimerTaskManager.dispatchTaskList: dispatch completed");
    }

    /**
     * 清理所有定时任务
     *
     * <p>分别调用 {@link SingleTaskManager#clearAll()} 和 {@link CrontabTaskManager#clearAll()}
     * 清理两类任务，调度器仍可接受新任务。</p>
     */
    private void clearAllTasks() {
        LOGGER.info("TimerTaskManager.clearAllTasks: clearing all tasks");
        SingleTaskManager.getInstance().clearAll();
        CrontabTaskManager.getInstance().clearAll();
        LOGGER.info("TimerTaskManager.clearAllTasks: completed");
    }

    // -------------------------------------------------------------------------
    // 任务分发
    // -------------------------------------------------------------------------

    /**
     * 将单个任务按类型路由到对应子管理器
     *
     * <p>若任务未启用（{@code enable=false}）或调度信息不完整，则跳过该任务。
     * 对于 ONCE 类型任务，如果触发时间已过期，则主动删除该任务。</p>
     *
     * @param task 任务信息
     */
    private void dispatchTask(TaskSchedule task) {
        if (task == null || task.getTaskId() == null) {
            LOGGER.warn("TimerTaskManager.dispatchTask: task or taskId is null, skip");
            return;
        }
        if (!task.isEnable()) {
            LOGGER.info("TimerTaskManager.dispatchTask: task disabled, skip taskId={}", task.getTaskId());
            return;
        }
        if (task.getSchedule() == null || task.getSchedule().getType() == null) {
            LOGGER.warn("TimerTaskManager.dispatchTask: schedule or type is null, skip taskId={}", task.getTaskId());
            return;
        }
        ScheduleTypeEnum type = task.getSchedule().getType();
        if (ScheduleTypeEnum.ONCE == type) {
            LOGGER.info("TimerTaskManager.dispatchTask: dispatching ONCE task, taskId={}", task.getTaskId());
            if (isOnceTaskExpired(task)) {
                LOGGER.warn("TimerTaskManager.dispatchTask: ONCE task expired, will delete taskId={}", task.getTaskId());
                deleteExpiredTask(task.getTaskId());
                return;
            }
            SingleTaskManager.getInstance().addTask(task, globalCallback);
        } else if (ScheduleTypeEnum.RECURRING == type) {
            LOGGER.info("TimerTaskManager.dispatchTask: dispatching RECURRING task, taskId={}", task.getTaskId());
            CrontabTaskManager.getInstance().addTask(task, globalCallback);
        } else {
            LOGGER.warn("TimerTaskManager.dispatchTask: unknown schedule type={}, skip taskId={}", type,
                    task.getTaskId());
        }
    }

    /**
     * 检查一次性任务是否已过期
     *
     * @param task 任务信息
     * @return true 表示任务已过期（触发时间 <= 当前时间）
     */
    private boolean isOnceTaskExpired(TaskSchedule task) {
        if (task.getSchedule() == null || task.getSchedule().getTriggerTime() == null) {
            return true;
        }
        long triggerTimeMs = TimeUtil.parseTriggerTime(task.getSchedule().getTriggerTime());
        if (triggerTimeMs <= 0) {
            return true;
        }
        return triggerTimeMs <= System.currentTimeMillis();
    }

    /**
     * 删除过期任务
     *
     * <p>使用 {@link ThreadPoolManager} 提交到共享线程池异步执行，避免阻塞当前线程。</p>
     *
     * @param taskId 任务ID
     */
    private void deleteExpiredTask(String taskId) {
        ThreadPoolManager.getInstance().executeSharedThreadPool(() -> {
            try {
                LOGGER.info("TimerTaskManager.deleteExpiredTask: deleting expired taskId={}", taskId);
                DeleteScheduleTaskRequest request = new DeleteScheduleTaskRequest(taskId);
                TimerTaskModel<Boolean> result = AISFSDKClient.getTimerTaskApi().deleteScheduleTask(request);
                if (result != null && result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
                    LOGGER.info("TimerTaskManager.deleteExpiredTask: deleted taskId={} successfully", taskId);
                } else {
                    LOGGER.warn("TimerTaskManager.deleteExpiredTask: failed to delete taskId={}, retCode={}, retMsg={}",
                            taskId, result != null ? result.getRetCode() : -1, result != null ? result.getRetMsg() : "null");
                }
            } catch (Exception e) {
                LOGGER.error("TimerTaskManager.deleteExpiredTask: exception when deleting taskId={}", taskId, e);
            }
        });
    }

    /**
     * 任务触发后删除服务端记录
     *
     * <p>使用 {@link ThreadPoolManager} 提交到共享线程池异步执行，避免阻塞任务触发流程。</p>
     *
     * @param taskId 任务ID
     */
    private void deleteTaskAfterTrigger(String taskId) {
        ThreadPoolManager.getInstance().executeSharedThreadPool(() -> {
            try {
                LOGGER.info("TimerTaskManager.deleteTaskAfterTrigger: deleting taskId={}", taskId);
                DeleteScheduleTaskRequest request = new DeleteScheduleTaskRequest(taskId);
                TimerTaskModel<Boolean> result = AISFSDKClient.getTimerTaskApi().deleteScheduleTask(request);
                if (result != null && result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
                    LOGGER.info("TimerTaskManager.deleteTaskAfterTrigger: deleted taskId={} successfully", taskId);
                } else {
                    LOGGER.warn("TimerTaskManager.deleteTaskAfterTrigger: failed to delete taskId={}, retCode={}, retMsg={}",
                            taskId, result != null ? result.getRetCode() : -1, result != null ? result.getRetMsg() : "null");
                }
            } catch (Exception e) {
                LOGGER.error("TimerTaskManager.deleteTaskAfterTrigger: exception when deleting taskId={}", taskId, e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // 工具方法
    // -------------------------------------------------------------------------

    /**
     * 检查包名是否在白名单中
     *
     * @param packageName 应用包名
     * @return true 表示包名在白名单中
     */
    private boolean isPackageAllowed(String packageName) {
        if (packageName == null) {
            return false;
        }
        for (String allowed : ALLOWED_PACKAGES) {
            if (allowed.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 包装用户回调，在任务触发后执行清理操作
     *
     * <p>包装后的回调会先执行用户原始回调，然后对于 ONCE 类型任务，
     * 异步调用服务端删除接口清理任务记录。</p>
     *
     * @param callback 用户原始回调
     * @return 包装后的回调
     */
    private Consumer<TaskSchedule> wrapCallback(Consumer<TaskSchedule> callback) {
        return task -> {
            try {
                callback.accept(task);
            } catch (Exception e) {
                LOGGER.error("TimerTaskManager: user callback threw exception for taskId={}",
                        task != null ? task.getTaskId() : "null", e);
            }
            if (task != null && task.getSchedule() != null
                    && task.getSchedule().getType() == ScheduleTypeEnum.ONCE) {
                deleteTaskAfterTrigger(task.getTaskId());
            }
        };
    }
}
