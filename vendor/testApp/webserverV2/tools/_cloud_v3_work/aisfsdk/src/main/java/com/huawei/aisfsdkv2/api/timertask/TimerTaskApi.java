/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.timertask;

/**
 * 任务接口
 *
 * @since 2026-04-07
 */
public interface TimerTaskApi {

    /**
     * 获取用户可执行的任务能力列表
     * <p>对应接口: GET /acm/v1/memory/scheduleTaskAbility</p>
     *
     * @return 任务能力查询结果，包含 Skill、Agents、Api 三类能力列表，查询失败返回 null
     */
    TaskAbilityResult getTaskAbilityInfoList();

    /**
     * 获取用户已创建的定时任务列表
     * <p>对应接口: GET /acm/v1/memory/scheduleTask</p>
     *
     * @return 定时任务列表结果
     */
    TimerTaskModel<java.util.List<TaskSchedule>> getScheduleTaskList();

    /**
     * 创建定时任务
     * <p>对应接口: POST /acm/v1/memory/scheduleTask</p>
     *
     * @param request 创建定时任务请求
     * @return 创建结果，成功时 data 为任务ID
     */
    TimerTaskModel<String> createScheduleTask(CreateScheduleTaskRequest request);

    /**
     * 删除定时任务
     * <p>对应接口: DELETE /acm/v1/memory/scheduleTask</p>
     *
     * @param request 删除定时任务请求
     * @return 删除结果
     */
    TimerTaskModel<Boolean> deleteScheduleTask(DeleteScheduleTaskRequest request);

    /**
     * 修改定时任务
     * <p>对应接口: PUT /acm/v1/memory/scheduleTask</p>
     *
     * @param request 修改定时任务请求
     * @return 修改结果
     */
    TimerTaskModel<Boolean> updateScheduleTask(UpdateScheduleTaskRequest request);
}
