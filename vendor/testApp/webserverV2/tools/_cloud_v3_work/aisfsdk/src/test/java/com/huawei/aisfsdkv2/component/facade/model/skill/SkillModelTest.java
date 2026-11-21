/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.skill;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * SkillModel 测试
 */
public class SkillModelTest {

    private SkillModel model;

    @Before
    public void setUp() {
        model = new SkillModel();
    }

    @Test
    public void testDefaultConstructor() {
        assertNull(model.getSkillId());
        assertNull(model.getSkillName());
        assertNull(model.getSteps());
        assertNull(model.getSummary());
        assertNull(model.getIsDefault());
        assertNull(model.getApp());
        assertNull(model.getVariables());
    }

    @Test
    public void testSetAndGetSkillId() {
        model.setSkillId("skill-001");
        assertEquals("skill-001", model.getSkillId());
    }

    @Test
    public void testSetAndGetSkillName() {
        model.setSkillName("Weather Query");
        assertEquals("Weather Query", model.getSkillName());
    }

    @Test
    public void testSetAndGetSteps() {
        model.setSteps("step1|step2");
        assertEquals("step1|step2", model.getSteps());
    }

    @Test
    public void testSetAndGetSummary() {
        model.setSummary("Query weather");
        assertEquals("Query weather", model.getSummary());
    }

    @Test
    public void testSetAndGetIsDefault() {
        model.setIsDefault("true");
        assertEquals("true", model.getIsDefault());
    }

    @Test
    public void testSetAndGetApp() {
        model.setApp("com.example.app");
        assertEquals("com.example.app", model.getApp());
    }

    @Test
    public void testSetAndGetVariables() {
        model.setVariables("{\"city\":\"Beijing\"}");
        assertEquals("{\"city\":\"Beijing\"}", model.getVariables());
    }

    @Test
    public void testToString() {
        model.setSkillId("s-1");
        model.setSkillName("test");
        String str = model.toString();
        assertNotNull(str);
        assertTrue(str.contains("s-1"));
        assertTrue(str.contains("test"));
        assertTrue(str.contains("SkillModel"));
    }

    @Test
    public void testToStringWithNulls() {
        String str = model.toString();
        assertNotNull(str);
        assertTrue(str.contains("SkillModel"));
    }
}
