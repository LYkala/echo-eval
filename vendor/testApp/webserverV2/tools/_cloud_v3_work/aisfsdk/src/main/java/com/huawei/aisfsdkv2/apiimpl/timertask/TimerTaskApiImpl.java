/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.timertask;

import com.huawei.aisfsdkv2.api.timertask.CreateScheduleTaskRequest;
import com.huawei.aisfsdkv2.api.timertask.DeleteScheduleTaskRequest;
import com.huawei.aisfsdkv2.api.timertask.ScheduleInfo;
import com.huawei.aisfsdkv2.api.timertask.ScheduleTypeEnum;
import com.huawei.aisfsdkv2.api.timertask.TaskAbilityInfo;
import com.huawei.aisfsdkv2.api.timertask.TaskAbilityResult;
import com.huawei.aisfsdkv2.api.timertask.TaskSchedule;
import com.huawei.aisfsdkv2.api.timertask.TimerTaskApi;
import com.huawei.aisfsdkv2.api.timertask.TimerTaskModel;
import com.huawei.aisfsdkv2.api.timertask.UpdateScheduleTaskRequest;
import com.huawei.aisfsdkv2.component.facade.TimerTaskFacade;
import com.huawei.aisfsdkv2.component.facade.model.timertask.TaskAbilityModel;
import com.huawei.aisfsdkv2.component.facade.model.timertask.TimerTaskException;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.timertask.TimerTaskManager;
import com.huawei.aisfsdkv2.util.TimeUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务接口实现
 *
 * @since 2026-04-07
 */
public class TimerTaskApiImpl implements TimerTaskApi {

    private static final Logger LOGGER = LoggerFactory.getLogger("TimerTaskApiImpl");

    private static class Holder {
        private static final TimerTaskApiImpl INSTANCE = new TimerTaskApiImpl();
    }

    private TimerTaskApiImpl() {
    }

    /**
     * 获取单例
     *
     * @return 单例实例
     */
    public static TimerTaskApiImpl getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 获取用户可执行的任务能力列表
     * <p>对应接口: GET /acm/v1/memory/scheduleTask</p>
     *
     * @return 任务能力查询结果，包含 Skill、Agents、Api 三类能力列表，查询失败返回 null
     */
    @Override
    public TaskAbilityResult getTaskAbilityInfoList() {
        try {
            com.huawei.aisfsdkv2.component.facade.model.timertask.TaskAbilityResult facadeResult =
                    TimerTaskFacade.getInstance().queryTaskAbilityList();
            return convertTaskAbilityResult(facadeResult);
        } catch (TimerTaskException e) {
            LOGGER.error("TimerTaskApiImpl.getTaskAbilityInfoList: failed, code={}, msg={}", e.getCode(), e.getMessage());
            return null;
        }
    }

    /**
     * 获取用户已创建的定时任务列表
     * <p>对应接口: GET /acm/v1/memory/scheduleTask</p>
     *
     * @return 定时任务列表结果
     */
    @Override
    public TimerTaskModel<List<TaskSchedule>> getScheduleTaskList() {
        return TimerTaskFacade.getInstance().queryScheduleTaskList();
    }

    /**
     * 创建定时任务
     * <p>对应接口: POST /acm/v1/memory/scheduleTask</p>
     *
     * @param request 创建定时任务请求
     * @return 创建结果，成功时 data 为任务ID
     */
    @Override
    public TimerTaskModel<String> createScheduleTask(CreateScheduleTaskRequest request) {
        if (request == null) {
            LOGGER.warn("TimerTaskApiImpl.createScheduleTask: request is null");
            return TimerTaskModel.error(-1, "request is null");
        }
        if (!validateOnceTaskTriggerTime(request.getSchedule(), "createScheduleTask")) {
            return TimerTaskModel.error(-1, "trigger time has expired");
        }
        TimerTaskModel<String> result = TimerTaskFacade.getInstance().createScheduleTask(request);
        if (result.isSuccess() && result.getData() != null) {
            TaskSchedule taskSchedule = new TaskSchedule();
            taskSchedule.setTaskId(result.getData());
            taskSchedule.setEnable(request.isEnable());
            taskSchedule.setSchedule(request.getSchedule());
            taskSchedule.setAction(request.getAction());
            TimerTaskManager.getInstance().addTask(taskSchedule);
        }
        return result;
    }

    /**
     * 删除定时任务
     * <p>对应接口: DELETE /acm/v1/memory/scheduleTask</p>
     *
     * @param request 删除定时任务请求
     * @return 删除结果
     */
    @Override
    public TimerTaskModel<Boolean> deleteScheduleTask(DeleteScheduleTaskRequest request) {
        if (request == null) {
            LOGGER.warn("TimerTaskApiImpl.deleteScheduleTask: request is null");
            return TimerTaskModel.error(-1, "request is null");
        }
        return TimerTaskFacade.getInstance().deleteScheduleTask(request);
    }

    /**
     * 更新定时任务
     * <p>对应接口: PUT /acm/v1/memory/scheduleTask</p>
     *
     * @param request 更新定时任务请求
     * @return 更新结果
     */
    @Override
    public TimerTaskModel<Boolean> updateScheduleTask(UpdateScheduleTaskRequest request) {
        if (request == null) {
            LOGGER.warn("TimerTaskApiImpl.updateScheduleTask: request is null");
            return TimerTaskModel.error(-1, "request is null");
        }
        if (!validateOnceTaskTriggerTime(request.getSchedule(), "updateScheduleTask")) {
            return TimerTaskModel.error(-1, "trigger time has expired");
        }
        TimerTaskModel<Boolean> result = TimerTaskFacade.getInstance().updateScheduleTask(request);
        if (result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
            TimerTaskManager.getInstance().removeTask(request.getTaskId());
            TaskSchedule taskSchedule = new TaskSchedule();
            taskSchedule.setTaskId(request.getTaskId());
            taskSchedule.setEnable(request.isEnable());
            taskSchedule.setSchedule(request.getSchedule());
            taskSchedule.setAction(request.getAction());
            TimerTaskManager.getInstance().addTask(taskSchedule);
        }
        return result;
    }

    /**
     * 将 Facade 层任务能力结果转换为 API 层结果
     *
     * @param source Facade 层任务能力结果
     * @return API 层任务能力结果
     */
    private TaskAbilityResult convertTaskAbilityResult(
            com.huawei.aisfsdkv2.component.facade.model.timertask.TaskAbilityResult source) {
        Map<String, List<TaskAbilityInfo>> infoMap = new HashMap<>();
        Map<String, List<TaskAbilityModel>> modelMap = source.getTaskAbilityModelMap();
        if (modelMap != null) {
            for (Map.Entry<String, List<TaskAbilityModel>> entry : modelMap.entrySet()) {
                infoMap.put(entry.getKey(), convertAbilityList(entry.getValue()));
            }
        }
        TaskAbilityResult result = new TaskAbilityResult();
        result.setTaskAbilityInfoMap(infoMap);
        return result;
    }

    /**
     * 将 Facade 层能力模型列表转换为 API 层能力信息列表
     *
     * @param source Facade 层能力模型列表
     * @return API 层能力信息列表
     */
    private List<TaskAbilityInfo> convertAbilityList(List<TaskAbilityModel> source) {
        List<TaskAbilityInfo> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (TaskAbilityModel model : source) {
            TaskAbilityInfo info = new TaskAbilityInfo();
            info.setId(model.getId());
            info.setShowLabel(model.getShowLabel());
            info.setDefault(model.isDefault());
            info.setDescription(model.getDescription());
            result.add(info);
        }
        return result;
    }

    /**
     * 校验一次性任务的触发时间是否过期
     *
     * <p>对于 ONCE 类型的任务，如果触发时间小于等于当前时间，则返回 false。</p>
     *
     * @param schedule  调度信息
     * @param methodName 方法名（用于日志）
     * @return true 表示校验通过（非 ONCE 类型或时间未过期），false 表示已过期
     */
    private boolean validateOnceTaskTriggerTime(ScheduleInfo schedule, String methodName) {
        if (schedule == null || schedule.getType() != ScheduleTypeEnum.ONCE) {
            return true;
        }
        if (schedule.getTriggerTime() == null) {
            LOGGER.warn("TimerTaskApiImpl.{}: ONCE task triggerTime is null", methodName);
            return false;
        }
        long triggerTimeMs = TimeUtil.parseTriggerTime(schedule.getTriggerTime());
        if (triggerTimeMs <= 0) {
            LOGGER.warn("TimerTaskApiImpl.{}: invalid triggerTime={}", methodName, schedule.getTriggerTime());
            return false;
        }
        long now = System.currentTimeMillis();
        if (triggerTimeMs <= now) {
            LOGGER.warn("TimerTaskApiImpl.{}: triggerTime={} has expired (now={}), reject task",
                    methodName, triggerTimeMs, now);
            return false;
        }
        return true;
    }
}
