/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.impl;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * TimerTaskFacadeImpl 测试 - 仅测试模型类
 */
public class TimerTaskFacadeImplTest {

    @Test
    public void testTimerTaskException_Fields() {
        com.huawei.aisfsdkv2.component.facade.model.timertask.TimerTaskException ex = 
                new com.huawei.aisfsdkv2.component.facade.model.timertask.TimerTaskException(400, "Bad request");
        assertEquals(400, ex.getCode());
        assertEquals("Bad request", ex.getMessage());
    }

    @Test
    public void testTaskAbilityModel_Fields() {
        com.huawei.aisfsdkv2.component.facade.model.timertask.TaskAbilityModel model = 
                new com.huawei.aisfsdkv2.component.facade.model.timertask.TaskAbilityModel();
        model.setId("a1");
        model.setShowLabel("Label");
        model.setDescription("Desc");
        model.setDefault(true);
        assertEquals("a1", model.getId());
        assertEquals("Label", model.getShowLabel());
        assertEquals("Desc", model.getDescription());
        assertTrue(model.isDefault());
    }

    @Test
    public void testTaskAbilityResult_Fields() {
        com.huawei.aisfsdkv2.component.facade.model.timertask.TaskAbilityResult result = 
                new com.huawei.aisfsdkv2.component.facade.model.timertask.TaskAbilityResult();
        assertNotNull(result.getTaskAbilityModelMap());
    }

    @Test
    public void testTimerTaskModel_Success() {
        com.huawei.aisfsdkv2.api.timertask.TimerTaskModel<String> model = 
                com.huawei.aisfsdkv2.api.timertask.TimerTaskModel.success("data");
        assertEquals(200, model.getRetCode());
        assertEquals("data", model.getData());
        assertTrue(model.isSuccess());
    }

    @Test
    public void testTimerTaskModel_Error() {
        com.huawei.aisfsdkv2.api.timertask.TimerTaskModel<String> model = 
                com.huawei.aisfsdkv2.api.timertask.TimerTaskModel.error(500, "Error");
        assertEquals(500, model.getRetCode());
        assertEquals("Error", model.getRetMsg());
        assertFalse(model.isSuccess());
    }

    @Test
    public void testTimerTaskModel_Constructor() {
        com.huawei.aisfsdkv2.api.timertask.TimerTaskModel<Integer> model = 
                new com.huawei.aisfsdkv2.api.timertask.TimerTaskModel<>(200, "OK", 42);
        assertEquals(200, model.getRetCode());
        assertEquals("OK", model.getRetMsg());
        assertEquals(Integer.valueOf(42), model.getData());
        assertTrue(model.isSuccess());
    }
}
