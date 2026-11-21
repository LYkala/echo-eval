/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.mimictask;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * ScrollState 测试
 */
public class ScrollStateTest {

    @Test
    public void testDefaultValues() {
        ScrollState state = new ScrollState();
        assertEquals(0, state.getLastScrollX());
        assertEquals(0, state.getLastScrollY());
        assertEquals(0, state.getMaxScrollX());
        assertEquals(0, state.getMaxScrollY());
        assertEquals(0L, state.getLastEventTime());
    }

    @Test
    public void testSetAndGetLastScrollX() {
        ScrollState state = new ScrollState();
        state.setLastScrollX(100);
        assertEquals(100, state.getLastScrollX());
    }

    @Test
    public void testSetAndGetLastScrollY() {
        ScrollState state = new ScrollState();
        state.setLastScrollY(200);
        assertEquals(200, state.getLastScrollY());
    }

    @Test
    public void testSetAndGetMaxScrollX() {
        ScrollState state = new ScrollState();
        state.setMaxScrollX(500);
        assertEquals(500, state.getMaxScrollX());
    }

    @Test
    public void testSetAndGetMaxScrollY() {
        ScrollState state = new ScrollState();
        state.setMaxScrollY(800);
        assertEquals(800, state.getMaxScrollY());
    }

    @Test
    public void testSetAndGetLastEventTime() {
        ScrollState state = new ScrollState();
        long now = System.currentTimeMillis();
        state.setLastEventTime(now);
        assertEquals(now, state.getLastEventTime());
    }

    @Test
    public void testMultipleFields() {
        ScrollState state = new ScrollState();
        state.setLastScrollX(10);
        state.setLastScrollY(20);
        state.setMaxScrollX(100);
        state.setMaxScrollY(200);
        state.setLastEventTime(9999L);
        assertEquals(10, state.getLastScrollX());
        assertEquals(20, state.getLastScrollY());
        assertEquals(100, state.getMaxScrollX());
        assertEquals(200, state.getMaxScrollY());
        assertEquals(9999L, state.getLastEventTime());
    }

    @Test
    public void testNegativeValues() {
        ScrollState state = new ScrollState();
        state.setLastScrollX(-5);
        state.setLastScrollY(-10);
        assertEquals(-5, state.getLastScrollX());
        assertEquals(-10, state.getLastScrollY());
    }
}
