/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.skill;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * SkillException 测试
 */
public class SkillExceptionTest {

    @Test
    public void testConstructorAndGetCode() {
        SkillException ex = new SkillException(400, "Bad Request");
        assertEquals(400, ex.getCode());
        assertEquals("Bad Request", ex.getMessage());
    }

    @Test
    public void testThrownAndCaught() {
        try {
            throw new SkillException(403, "Forbidden");
        } catch (SkillException e) {
            assertEquals(403, e.getCode());
            assertEquals("Forbidden", e.getMessage());
        }
    }

    @Test
    public void testCodeZero() {
        SkillException ex = new SkillException(0, "Success");
        assertEquals(0, ex.getCode());
    }

    @Test
    public void testNegativeCode() {
        SkillException ex = new SkillException(-1, "Unknown");
        assertEquals(-1, ex.getCode());
    }
}
