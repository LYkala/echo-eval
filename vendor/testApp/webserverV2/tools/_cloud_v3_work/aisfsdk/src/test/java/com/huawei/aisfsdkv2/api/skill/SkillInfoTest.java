/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.skill;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * SkillInfo 测试
 */
public class SkillInfoTest {

    private SkillInfo skillInfo;

    @Before
    public void setUp() {
        skillInfo = new SkillInfo();
    }

    @Test
    public void testDefaultConstructor() {
        assertNull(skillInfo.getSkillId());
        assertNull(skillInfo.getSkillName());
        assertNull(skillInfo.getSteps());
        assertNull(skillInfo.getSummary());
        assertNull(skillInfo.getApp());
        assertNull(skillInfo.getVariables());
        assertNull(skillInfo.getIsDefault());
    }

    @Test
    public void testSetAndGetSkillId() {
        skillInfo.setSkillId("skill-001");
        assertEquals("skill-001", skillInfo.getSkillId());
    }

    @Test
    public void testSetAndGetSkillName() {
        skillInfo.setSkillName("Weather Query");
        assertEquals("Weather Query", skillInfo.getSkillName());
    }

    @Test
    public void testSetAndGetSteps() {
        skillInfo.setSteps("step1|step2|step3");
        assertEquals("step1|step2|step3", skillInfo.getSteps());
    }

    @Test
    public void testSetAndGetSummary() {
        skillInfo.setSummary("Query weather information");
        assertEquals("Query weather information", skillInfo.getSummary());
    }

    @Test
    public void testSetAndGetApp() {
        skillInfo.setApp("com.example.weather");
        assertEquals("com.example.weather", skillInfo.getApp());
    }

    @Test
    public void testSetAndGetVariables() {
        skillInfo.setVariables("{\"city\":\"Beijing\"}");
        assertEquals("{\"city\":\"Beijing\"}", skillInfo.getVariables());
    }

    @Test
    public void testSetAndGetIsDefault() {
        skillInfo.setIsDefault("true");
        assertEquals("true", skillInfo.getIsDefault());
    }

    @Test
    public void testToString() {
        skillInfo.setSkillId("s-1");
        skillInfo.setSkillName("test skill");
        skillInfo.setApp("com.test.app");
        String str = skillInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("s-1"));
        assertTrue(str.contains("test skill"));
        assertTrue(str.contains("com.test.app"));
    }

    @Test
    public void testToStringWithNulls() {
        String str = skillInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("SkillInfo"));
    }

    @Test
    public void testIsDefaultValues() {
        skillInfo.setIsDefault("false");
        assertEquals("false", skillInfo.getIsDefault());
        skillInfo.setIsDefault("1");
        assertEquals("1", skillInfo.getIsDefault());
    }
}
