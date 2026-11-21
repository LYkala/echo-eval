/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * DialogControl 测试
 */
public class DialogControlTest {

    private DialogControl control;

    @Before
    public void setUp() {
        control = new DialogControl();
    }

    @Test
    public void testDefaultConstructor() {
        assertEquals("", control.getContent());
        assertEquals("", control.getType());
    }

    @Test
    public void testSetAndGetContent() {
        control.setContent("OK");
        assertEquals("OK", control.getContent());
    }

    @Test
    public void testSetAndGetType() {
        control.setType("button");
        assertEquals("button", control.getType());
    }

    @Test
    public void testToStringFormat() {
        control.setContent("Confirm");
        control.setType("primary");
        String str = control.toString();
        assertNotNull(str);
        assertTrue(str.contains("Confirm"));
        assertTrue(str.contains("primary"));
        assertTrue(str.contains("content"));
        assertTrue(str.contains("type"));
    }

    @Test
    public void testToStringWithEmptyValues() {
        String str = control.toString();
        assertNotNull(str);
        assertTrue(str.contains("content"));
        assertTrue(str.contains("type"));
    }

    @Test
    public void testSetNullContent() {
        control.setContent(null);
        String str = control.toString();
        assertNotNull(str); // toString 不应抛异常
    }

    @Test
    public void testSetNullType() {
        control.setType(null);
        String str = control.toString();
        assertNotNull(str);
    }
}
