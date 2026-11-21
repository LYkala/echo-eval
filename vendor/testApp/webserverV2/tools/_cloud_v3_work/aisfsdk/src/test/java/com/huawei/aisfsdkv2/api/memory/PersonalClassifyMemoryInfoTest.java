/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.memory;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * PersonalClassifyMemoryInfo 测试
 */
public class PersonalClassifyMemoryInfoTest {

    @Test
    public void testDefaultConstructor() {
        PersonalClassifyMemoryInfo info = new PersonalClassifyMemoryInfo();
        assertNull(info.getId());
        assertNull(info.getMemType());
        assertNull(info.getTopic());
        assertNull(info.getContent());
    }

    @Test
    public void testParameterizedConstructor() {
        PersonalClassifyMemoryInfo info = new PersonalClassifyMemoryInfo(
                "id-1", "core", "hobby", "likes music");
        assertEquals("id-1", info.getId());
        assertEquals("core", info.getMemType());
        assertEquals("hobby", info.getTopic());
        assertEquals("likes music", info.getContent());
    }

    @Test
    public void testSetAndGetId() {
        PersonalClassifyMemoryInfo info = new PersonalClassifyMemoryInfo();
        info.setId("mem-999");
        assertEquals("mem-999", info.getId());
    }

    @Test
    public void testSetAndGetMemType() {
        PersonalClassifyMemoryInfo info = new PersonalClassifyMemoryInfo();
        info.setMemType("episodic");
        assertEquals("episodic", info.getMemType());
    }

    @Test
    public void testSetAndGetTopic() {
        PersonalClassifyMemoryInfo info = new PersonalClassifyMemoryInfo();
        info.setTopic("food preferences");
        assertEquals("food preferences", info.getTopic());
    }

    @Test
    public void testSetAndGetContent() {
        PersonalClassifyMemoryInfo info = new PersonalClassifyMemoryInfo();
        info.setContent("prefers spicy food");
        assertEquals("prefers spicy food", info.getContent());
    }

    @Test
    public void testToString() {
        PersonalClassifyMemoryInfo info = new PersonalClassifyMemoryInfo(
                "id-42", "semantic", "work", "software engineer");
        String str = info.toString();
        assertNotNull(str);
        assertTrue(str.contains("id-42"));
        assertTrue(str.contains("semantic"));
        assertTrue(str.contains("work"));
        assertTrue(str.contains("software engineer"));
    }

    @Test
    public void testToStringWithNulls() {
        PersonalClassifyMemoryInfo info = new PersonalClassifyMemoryInfo();
        String str = info.toString();
        assertNotNull(str);
        assertTrue(str.contains("PersonalClassifyMemoryInfo"));
    }

    @Test
    public void testSetterOverridesConstructor() {
        PersonalClassifyMemoryInfo info = new PersonalClassifyMemoryInfo(
                "original-id", "core", "topic", "content");
        info.setId("new-id");
        assertEquals("new-id", info.getId());
    }
}
