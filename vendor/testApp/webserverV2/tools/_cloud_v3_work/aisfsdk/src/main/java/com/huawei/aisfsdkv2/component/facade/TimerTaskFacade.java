/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade;

import com.huawei.aisfsdkv2.api.timertask.CreateScheduleTaskRequest;
import com.huawei.aisfsdkv2.api.timertask.DeleteScheduleTaskRequest;
import com.huawei.aisfsdkv2.api.timertask.TaskSchedule;
import com.huawei.aisfsdkv2.api.timertask.TimerTaskModel;
import com.huawei.aisfsdkv2.api.timertask.UpdateScheduleTaskRequest;
import com.huawei.aisfsdkv2.component.facade.impl.TimerTaskFacadeImpl;
import com.huawei.aisfsdkv2.component.facade.model.timertask.TaskAbilityResult;
import com.huawei.aisfsdkv2.component.facade.model.timertask.TimerTaskException;

import java.util.List;

/**
 * 定时任务 Facade 接口
 * <p>封装定时任务相关 HTTP 接口调用（同步接口）。</p>
 */
public interface TimerTaskFacade {

    /**
     * 获取 TimerTaskFacade 实例（单例）
     *
     * @return TimerTaskFacade 实例
     */
    static TimerTaskFacade getInstance() {
        return TimerTaskFacadeImpl.getInstance();
    }

    /**
     * 查询任务能力列表（同步）
     * <p>对应接口: GET /acm/v1/memory/scheduleTaskAbility</p>
     *
     * @return 任务能力查询结果，包含 Skill、Agents、Api 三类能力列表
     * @throws TimerTaskException 查询失败时抛出异常
     */
    TaskAbilityResult queryTaskAbilityList() throws TimerTaskException;

    /**
     * 查询定时任务列表（同步）
     * <p>对应接口: GET /acm/v1/memory/scheduleTask</p>
     *
     * @return 定时任务列表结果
     */
    TimerTaskModel<List<TaskSchedule>> queryScheduleTaskList();

    /**
     * 创建定时任务（同步）
     * <p>对应接口: POST /acm/v1/memory/scheduleTask</p>
     *
     * @param request 创建定时任务请求
     * @return 创建结果，成功时 data 为任务ID
     */
    TimerTaskModel<String> createScheduleTask(CreateScheduleTaskRequest request);

    /**
     * 删除定时任务（同步）
     * <p>对应接口: DELETE /acm/v1/memory/scheduleTask</p>
     *
     * @param request 删除定时任务请求
     * @return 删除结果
     */
    TimerTaskModel<Boolean> deleteScheduleTask(DeleteScheduleTaskRequest request);

    /**
     * 更新定时任务（同步）
     * <p>对应接口: PUT /acm/v1/memory/scheduleTask</p>
     *
     * @param request 更新定时任务请求
     * @return 更新结果
     */
    TimerTaskModel<Boolean> updateScheduleTask(UpdateScheduleTaskRequest request);
}
