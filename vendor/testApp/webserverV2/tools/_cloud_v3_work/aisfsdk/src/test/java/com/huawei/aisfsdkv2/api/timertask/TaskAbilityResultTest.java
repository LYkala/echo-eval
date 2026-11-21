/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.timertask;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * TaskAbilityResult 测试
 */
public class TaskAbilityResultTest {

    private TaskAbilityResult result;

    @Before
    public void setUp() {
        result = new TaskAbilityResult();
    }

    @Test
    public void testDefaultConstructor() {
        assertNotNull(result.getTaskAbilityInfoMap());
        assertTrue(result.getTaskAbilityInfoMap().isEmpty());
    }

    @Test
    public void testSetAndGetMap() {
        Map<String, List<TaskAbilityInfo>> map = new HashMap<>();
        List<TaskAbilityInfo> skillList = new ArrayList<>();
        TaskAbilityInfo info = new TaskAbilityInfo();
        info.setId("skill-001");
        skillList.add(info);
        map.put("Skill", skillList);
        result.setTaskAbilityInfoMap(map);
        assertEquals(1, result.getTaskAbilityInfoMap().size());
        assertTrue(result.getTaskAbilityInfoMap().containsKey("Skill"));
    }

    @Test
    public void testMapWithMultipleCategories() {
        Map<String, List<TaskAbilityInfo>> map = new HashMap<>();
        map.put("Skill", new ArrayList<>());
        map.put("Agents", new ArrayList<>());
        map.put("Api", new ArrayList<>());
        result.setTaskAbilityInfoMap(map);
        assertEquals(3, result.getTaskAbilityInfoMap().size());
    }

    @Test
    public void testToString() {
        String str = result.toString();
        assertNotNull(str);
        assertTrue(str.contains("TaskAbilityResult"));
    }

    @Test
    public void testToStringWithData() {
        Map<String, List<TaskAbilityInfo>> map = new HashMap<>();
        map.put("Skill", new ArrayList<>());
        result.setTaskAbilityInfoMap(map);
        String str = result.toString();
        assertTrue(str.contains("Skill"));
    }
}
