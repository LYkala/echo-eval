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
 * DeleteSkillRequest 测试
 */
public class DeleteSkillRequestTest {

    private DeleteSkillRequest request;

    @Before
    public void setUp() {
        request = new DeleteSkillRequest();
    }

    @Test
    public void testDefaultConstructor() {
        assertNull(request.getSkillId());
    }

    @Test
    public void testParameterizedConstructor() {
        DeleteSkillRequest req = new DeleteSkillRequest("skill-001");
        assertEquals("skill-001", req.getSkillId());
    }

    @Test
    public void testSetAndGetSkillId() {
        request.setSkillId("skill-123");
        assertEquals("skill-123", request.getSkillId());
    }

    @Test
    public void testSetNullSkillId() {
        request.setSkillId(null);
        assertNull(request.getSkillId());
    }

    @Test
    public void testSetEmptySkillId() {
        request.setSkillId("");
        assertEquals("", request.getSkillId());
    }

    @Test
    public void testToString() {
        request.setSkillId("skill-abc");
        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("skill-abc"));
        assertTrue(str.contains("DeleteSkillRequest"));
    }

    @Test
    public void testToStringWithNull() {
        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("DeleteSkillRequest"));
    }

    @Test
    public void testFieldOverwrite() {
        request.setSkillId("id1");
        assertEquals("id1", request.getSkillId());
        request.setSkillId("id2");
        assertEquals("id2", request.getSkillId());
    }
}
