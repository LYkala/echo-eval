/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.timertask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务能力查询结果
 * <p>key 为能力分类名（如 Skill、Agents、Api），value 为该分类下的能力列表。</p>
 *
 * @since 2026-04-08
 */
public class TaskAbilityResult {

    /**
     * 任务能力信息 Map，key 为能力分类名，value 为该分类下的能力列表
     */
    private Map<String, List<TaskAbilityInfo>> taskAbilityInfoMap = new HashMap<>();

    /**
     * 默认构造方法
     */
    public TaskAbilityResult() {
    }

    /**
     * 获取任务能力信息 Map
     *
     * @return 任务能力信息 Map
     */
    public Map<String, List<TaskAbilityInfo>> getTaskAbilityInfoMap() {
        return taskAbilityInfoMap;
    }

    /**
     * 设置任务能力信息 Map
     *
     * @param taskAbilityInfoMap 任务能力信息 Map
     */
    public void setTaskAbilityInfoMap(Map<String, List<TaskAbilityInfo>> taskAbilityInfoMap) {
        this.taskAbilityInfoMap = taskAbilityInfoMap;
    }

    @Override
    public String toString() {
        return "TaskAbilityResult{taskAbilityInfoMap=" + taskAbilityInfoMap + "}";
    }
}
