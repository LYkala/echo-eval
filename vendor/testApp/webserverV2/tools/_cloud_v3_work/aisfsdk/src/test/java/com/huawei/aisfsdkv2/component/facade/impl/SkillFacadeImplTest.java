/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.impl;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * SkillFacadeImpl 测试 - 仅测试模型类
 */
public class SkillFacadeImplTest {

    @Test
    public void testSkillException_Fields() {
        com.huawei.aisfsdkv2.component.facade.model.skill.SkillException ex = 
                new com.huawei.aisfsdkv2.component.facade.model.skill.SkillException(404, "Not found");
        assertEquals(404, ex.getCode());
        assertEquals("Not found", ex.getMessage());
    }

    @Test
    public void testSkillModel_Fields() {
        com.huawei.aisfsdkv2.component.facade.model.skill.SkillModel model = 
                new com.huawei.aisfsdkv2.component.facade.model.skill.SkillModel();
        model.setSkillId("s1");
        model.setSkillName("MySkill");
        model.setSteps("step1");
        model.setSummary("summary");
        model.setIsDefault("true");
        model.setApp("com.example");
        model.setVariables("var1");
        assertEquals("s1", model.getSkillId());
        assertEquals("MySkill", model.getSkillName());
        assertEquals("step1", model.getSteps());
        assertEquals("summary", model.getSummary());
        assertEquals("true", model.getIsDefault());
        assertEquals("com.example", model.getApp());
        assertEquals("var1", model.getVariables());
    }

    @Test
    public void testSkillModel_DefaultValues() {
        com.huawei.aisfsdkv2.component.facade.model.skill.SkillModel model = 
                new com.huawei.aisfsdkv2.component.facade.model.skill.SkillModel();
        assertNotNull(model.toString());
    }
}
