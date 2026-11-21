/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.timertask;

import com.huawei.aisfsdkv2.api.session.SessionAgingConfig;
import com.huawei.aisfsdkv2.api.timertask.ScheduleAction;
import com.huawei.aisfsdkv2.api.timertask.TaskSchedule;
import com.huawei.aisfsdkv2.component.facade.ACMFacade;
import com.huawei.aisfsdkv2.component.facade.model.acm.AcmException;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.session.Session;
import com.huawei.aisfsdkv2.session.SessionManager;
import com.huawei.aisfsdkv2.session.impl.DefaultSessionCallback;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 定时任务事件处理器
 *
 * <p>作为 {@link TimerTaskManager} 的默认全局回调，在任务触发时根据
 * {@link ScheduleAction#getBody()} 组装请求并调用 ACM 接口执行任务。</p>
 *
 * <p>使用方式：在 SDK 初始化时调用 {@link #init()}，会自动将自身注册为
 * {@link TimerTaskManager} 的全局回调。</p>
 *
 * @since 2026-04-08
 */
public class TimerTaskHandler implements Consumer<TaskSchedule> {

    private static final Logger LOGGER = LoggerFactory.getLogger("TimerTaskHandler");

    private static final String ACTION_TYPE_SKILL = "SKILL";
    private static final String SKILL_PREFIX = "通通帮我做";

    private static class Holder {
        private static final TimerTaskHandler INSTANCE = new TimerTaskHandler();
    }

    /**
     * 初始化并注册为 {@link TimerTaskManager} 的全局回调
     *
     * <p>由 {@link com.huawei.aisfsdkv2.api.AISFSDKClient#init} 在
     * {@link TimerTaskManager#start()} 之前调用。</p>
     */
    public static void init() {
        LOGGER.info("TimerTaskHandler.init: registering as TimerTaskManager callback");
        TimerTaskManager.getInstance().setCallback(Holder.INSTANCE);
    }

    /**
     * 任务触发时的处理逻辑
     *
     * <p>创建正常会话（使用 {@link DefaultSessionCallback}，老化时间5分钟），
     * 从 {@code task.getAction().getBody()} 取出用户输入文本，
     * 调用 {@link ACMFacade#sendTaskMessage(String, String)} 发送到服务端执行。</p>
     *
     * @param task 触发的任务信息
     */
    @Override
    public void accept(TaskSchedule task) {
        if (task == null) {
            LOGGER.warn("TimerTaskHandler.accept: task is null, skip");
            return;
        }
        ScheduleAction action = task.getAction();
        if (action == null) {
            LOGGER.warn("TimerTaskHandler.accept: action is null, skip taskId={}", task.getTaskId());
            return;
        }
        String body = action.getBody();
        if (body == null || body.isEmpty()) {
            LOGGER.warn("TimerTaskHandler.accept: action body is empty, skip taskId={}", task.getTaskId());
            return;
        }
        Session session = SessionManager.getInstance().createSession(new DefaultSessionCallback());
        session.setAgingConfig(new SessionAgingConfig().setMaxIdleTimeMinutes(5));
        String sessionId = session.getId();
        LOGGER.info("TimerTaskHandler.accept: created session, sessionId={}, taskId={}", sessionId, task.getTaskId());
        String taskBody = Objects.equals(action.getType(), ACTION_TYPE_SKILL)
                ? SKILL_PREFIX + body
                : body;
        try {
            ACMFacade.getInstance().sendTaskMessage(sessionId, taskBody);
            LOGGER.info("TimerTaskHandler.accept: task message sent successfully, sessionId={}, taskId={}", sessionId, task.getTaskId());
        } catch (AcmException e) {
            LOGGER.error("TimerTaskHandler.accept: failed to send task message, sessionId={}, taskId={}, code={}, msg={}",
                    sessionId, task.getTaskId(), e.getCode(), e.getMessage());
        }
    }
}
