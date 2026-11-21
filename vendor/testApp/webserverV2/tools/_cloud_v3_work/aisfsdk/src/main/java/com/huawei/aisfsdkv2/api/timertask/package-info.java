/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 定时任务 API 接口包
 *
 * <p>本包提供定时任务的查询、创建、修改和删除能力，支持一次性任务和周期性任务。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.api.timertask.TimerTaskApi}：定时任务接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.timertask.TaskAbilityResult}：任务能力查询结果</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.timertask.TimerTaskModel}：定时任务数据模型</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.timertask.TaskSchedule}：任务调度信息</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.timertask.CreateScheduleTaskRequest}：创建定时任务请求</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.timertask.DeleteScheduleTaskRequest}：删除定时任务请求</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.timertask.UpdateScheduleTaskRequest}：修改定时任务请求</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.timertask.ScheduleTypeEnum}：调度类型枚举</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取定时任务 API 实例
 *   TimerTaskApi timerTaskApi = SdkManager.getInstance().getTimerTaskApi();
 *
 *   // 获取用户可执行的任务能力列表
 *   TaskAbilityResult abilities = timerTaskApi.getTaskAbilityInfoList();
 *   List&lt;SkillAbility&gt; skills = abilities.getSkills();
 *   List&lt;AgentAbility&gt; agents = abilities.getAgents();
 *   List&lt;ApiAbility&gt; apis = abilities.getApis();
 *
 *   // 获取已创建的定时任务列表
 *   TimerTaskModel&lt;List&lt;TaskSchedule&gt;&gt; taskList = timerTaskApi.getScheduleTaskList();
 *
 *   // 创建定时任务
 *   CreateScheduleTaskRequest request = new CreateScheduleTaskRequest();
 *   request.setTaskName("每日提醒");
 *   request.setScheduleType(ScheduleTypeEnum.RECURRING);
 *   request.setCronExpression("0 9 * * *"); // 每天9点
 *   request.setActionType("skill");
 *   request.setActionId("skill_id_123");
 *   TimerTaskModel&lt;String&gt; result = timerTaskApi.createScheduleTask(request);
 *   String taskId = result.getData();
 *
 *   // 修改定时任务
 *   UpdateScheduleTaskRequest updateRequest = new UpdateScheduleTaskRequest();
 *   updateRequest.setTaskId(taskId);
 *   updateRequest.setCronExpression("0 10 * * *"); // 改为每天10点
 *   timerTaskApi.updateScheduleTask(updateRequest);
 *
 *   // 删除定时任务
 *   DeleteScheduleTaskRequest deleteRequest = new DeleteScheduleTaskRequest();
 *   deleteRequest.setTaskId(taskId);
 *   timerTaskApi.deleteScheduleTask(deleteRequest);
 * </pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>创建任务前先获取任务能力列表，确认可用的技能/Agent/API</li>
 *   <li>cron 表达式使用 5 段格式：分 时 日 月 周</li>
 *   <li>合理设置任务触发时间，避免集中触发导致服务器压力</li>
 *   <li>不再使用的定时任务及时删除，避免资源浪费</li>
 *   <li>任务执行结果通过消息回调通知</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.api.timertask;
