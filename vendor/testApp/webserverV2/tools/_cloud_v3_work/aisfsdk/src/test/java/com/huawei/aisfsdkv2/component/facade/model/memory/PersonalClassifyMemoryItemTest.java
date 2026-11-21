/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.memory;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * PersonalClassifyMemoryItem 测试
 */
public class PersonalClassifyMemoryItemTest {

    @Test
    public void testDefaultConstructor() {
        PersonalClassifyMemoryItem item = new PersonalClassifyMemoryItem();
        assertNull(item.getId());
        assertNull(item.getMemType());
        assertNull(item.getTopic());
        assertNull(item.getContent());
    }

    @Test
    public void testParameterizedConstructor() {
        PersonalClassifyMemoryItem item = new PersonalClassifyMemoryItem(
                "id-1", "core", "hobby", "likes music");
        assertEquals("id-1", item.getId());
        assertEquals("core", item.getMemType());
        assertEquals("hobby", item.getTopic());
        assertEquals("likes music", item.getContent());
    }

    @Test
    public void testSettersAndGetters() {
        PersonalClassifyMemoryItem item = new PersonalClassifyMemoryItem();
        item.setId("id-2");
        item.setMemType("semantic");
        item.setTopic("work");
        item.setContent("software engineer");
        assertEquals("id-2", item.getId());
        assertEquals("semantic", item.getMemType());
        assertEquals("work", item.getTopic());
        assertEquals("software engineer", item.getContent());
    }

    @Test
    public void testOverrideWithSetter() {
        PersonalClassifyMemoryItem item = new PersonalClassifyMemoryItem(
                "old-id", "old-type", "old-topic", "old-content");
        item.setId("new-id");
        assertEquals("new-id", item.getId());
    }
}
