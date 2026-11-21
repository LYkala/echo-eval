/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.timertask;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TaskAbilityModel / TaskAbilityResult / TimerTaskException 合并测试
 */
public class TimerTaskFacadeModelTest {

    // ——— TaskAbilityModel ———

    @Test
    public void testTaskAbilityModelDefaults() {
        TaskAbilityModel model = new TaskAbilityModel();
        assertNull(model.getId());
        assertNull(model.getShowLabel());
        assertFalse(model.isDefault());
        assertNull(model.getDescription());
    }

    @Test
    public void testTaskAbilityModelSetters() {
        TaskAbilityModel model = new TaskAbilityModel();
        model.setId("a-1");
        model.setShowLabel("Weather");
        model.setDefault(true);
        model.setDescription("Check weather");
        assertEquals("a-1", model.getId());
        assertEquals("Weather", model.getShowLabel());
        assertTrue(model.isDefault());
        assertEquals("Check weather", model.getDescription());
    }

    @Test
    public void testTaskAbilityModelToString() {
        TaskAbilityModel model = new TaskAbilityModel();
        model.setId("m-1");
        model.setShowLabel("label");
        String str = model.toString();
        assertNotNull(str);
        assertTrue(str.contains("m-1"));
        assertTrue(str.contains("label"));
        assertTrue(str.contains("TaskAbilityModel"));
    }

    // ——— TaskAbilityResult ———

    @Test
    public void testTaskAbilityResultDefault() {
        TaskAbilityResult result = new TaskAbilityResult();
        assertNotNull(result.getTaskAbilityModelMap());
        assertTrue(result.getTaskAbilityModelMap().isEmpty());
    }

    @Test
    public void testTaskAbilityResultSetMap() {
        TaskAbilityResult result = new TaskAbilityResult();
        Map<String, List<TaskAbilityModel>> map = new HashMap<>();
        List<TaskAbilityModel> skills = new ArrayList<>();
        TaskAbilityModel model = new TaskAbilityModel();
        model.setId("skill-1");
        skills.add(model);
        map.put("Skill", skills);
        result.setTaskAbilityModelMap(map);
        assertEquals(1, result.getTaskAbilityModelMap().size());
        assertTrue(result.getTaskAbilityModelMap().containsKey("Skill"));
    }

    @Test
    public void testTaskAbilityResultToString() {
        TaskAbilityResult result = new TaskAbilityResult();
        String str = result.toString();
        assertNotNull(str);
        assertTrue(str.contains("TaskAbilityResult"));
    }

    // ——— TimerTaskException ———

    @Test
    public void testTimerTaskException() {
        TimerTaskException ex = new TimerTaskException(500, "Internal Error");
        assertEquals(500, ex.getCode());
        assertEquals("Internal Error", ex.getMessage());
    }

    @Test
    public void testTimerTaskExceptionThrow() {
        try {
            throw new TimerTaskException(404, "Not Found");
        } catch (TimerTaskException e) {
            assertEquals(404, e.getCode());
            assertEquals("Not Found", e.getMessage());
        }
    }

    @Test
    public void testTimerTaskExceptionCodeZero() {
        TimerTaskException ex = new TimerTaskException(0, "OK");
        assertEquals(0, ex.getCode());
    }
}
