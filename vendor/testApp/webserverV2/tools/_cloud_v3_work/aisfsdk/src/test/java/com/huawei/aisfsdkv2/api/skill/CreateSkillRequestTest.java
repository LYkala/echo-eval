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
 * CreateSkillRequest 测试
 */
public class CreateSkillRequestTest {

    private CreateSkillRequest request;

    @Before
    public void setUp() {
        request = new CreateSkillRequest();
    }

    @Test
    public void testDefaultConstructor() {
        assertNull(request.getSkillName());
        assertNull(request.getSteps());
        assertNull(request.getSummary());
        assertNull(request.getApp());
        assertNull(request.getVariables());
    }

    @Test
    public void testParameterizedConstructor() {
        CreateSkillRequest req = new CreateSkillRequest("skill1", "step1|step2", "summary", "app1", "{\"key\":\"val\"}");
        assertEquals("skill1", req.getSkillName());
        assertEquals("step1|step2", req.getSteps());
        assertEquals("summary", req.getSummary());
        assertEquals("app1", req.getApp());
        assertEquals("{\"key\":\"val\"}", req.getVariables());
    }

    @Test
    public void testSetAndGetSkillName() {
        request.setSkillName("Weather Query");
        assertEquals("Weather Query", request.getSkillName());
    }

    @Test
    public void testSetAndGetSteps() {
        request.setSteps("step1|step2|step3");
        assertEquals("step1|step2|step3", request.getSteps());
    }

    @Test
    public void testSetAndGetSummary() {
        request.setSummary("Query weather information");
        assertEquals("Query weather information", request.getSummary());
    }

    @Test
    public void testSetAndGetApp() {
        request.setApp("com.example.weather");
        assertEquals("com.example.weather", request.getApp());
    }

    @Test
    public void testSetAndGetVariables() {
        request.setVariables("{\"city\":\"Beijing\"}");
        assertEquals("{\"city\":\"Beijing\"}", request.getVariables());
    }

    @Test
    public void testSetNullValues() {
        request.setSkillName(null);
        request.setSteps(null);
        request.setSummary(null);
        request.setApp(null);
        request.setVariables(null);
        assertNull(request.getSkillName());
        assertNull(request.getSteps());
        assertNull(request.getSummary());
        assertNull(request.getApp());
        assertNull(request.getVariables());
    }

    @Test
    public void testSetEmptyValues() {
        request.setSkillName("");
        request.setSteps("");
        request.setSummary("");
        request.setApp("");
        request.setVariables("");
        assertEquals("", request.getSkillName());
        assertEquals("", request.getSteps());
        assertEquals("", request.getSummary());
        assertEquals("", request.getApp());
        assertEquals("", request.getVariables());
    }

    @Test
    public void testToString() {
        request.setSkillName("testSkill");
        request.setSteps("step1");
        request.setSummary("a summary");
        request.setApp("com.test");
        request.setVariables("vars");
        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("testSkill"));
        assertTrue(str.contains("step1"));
        assertTrue(str.contains("a summary"));
        assertTrue(str.contains("com.test"));
        assertTrue(str.contains("vars"));
    }

    @Test
    public void testToStringWithNulls() {
        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("CreateSkillRequest"));
    }

    @Test
    public void testFieldOverwrite() {
        request.setSkillName("name1");
        assertEquals("name1", request.getSkillName());
        request.setSkillName("name2");
        assertEquals("name2", request.getSkillName());
    }
}
