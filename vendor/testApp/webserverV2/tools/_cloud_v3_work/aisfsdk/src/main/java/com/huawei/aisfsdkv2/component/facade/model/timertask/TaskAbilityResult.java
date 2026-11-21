/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.timertask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务能力查询结果模型（Facade 层）
 * <p>key 为能力分类名（如 Skill、Agents、Api），value 为该分类下的能力列表。</p>
 *
 * @since 2026-04-08
 */
public class TaskAbilityResult {

    /**
     * 任务能力模型 Map，key 为能力分类名，value 为该分类下的能力列表
     */
    private Map<String, List<TaskAbilityModel>> taskAbilityModelMap = new HashMap<>();

    /**
     * 默认构造方法
     */
    public TaskAbilityResult() {
    }

    /**
     * 获取任务能力模型 Map
     *
     * @return 任务能力模型 Map
     */
    public Map<String, List<TaskAbilityModel>> getTaskAbilityModelMap() {
        return taskAbilityModelMap;
    }

    /**
     * 设置任务能力模型 Map
     *
     * @param taskAbilityModelMap 任务能力模型 Map
     */
    public void setTaskAbilityModelMap(Map<String, List<TaskAbilityModel>> taskAbilityModelMap) {
        this.taskAbilityModelMap = taskAbilityModelMap;
    }

    @Override
    public String toString() {
        return "TaskAbilityResult{taskAbilityModelMap=" + taskAbilityModelMap + "}";
    }
}
