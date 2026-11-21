/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.eventbus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * EventHandleStrategyEnum 测试
 */
public class EventHandleStrategyEnumTest {

    @Test
    public void testEnumValues() {
        EventHandleStrategyEnum[] values = EventHandleStrategyEnum.values();
        assertEquals(3, values.length);
    }

    @Test
    public void testSyncValue() {
        EventHandleStrategyEnum value = EventHandleStrategyEnum.SYNC;
        assertNotNull(value);
        assertEquals("SYNC", value.name());
    }

    @Test
    public void testAsyncSingleThreadValue() {
        EventHandleStrategyEnum value = EventHandleStrategyEnum.ASYNC_SINGLE_THREAD;
        assertNotNull(value);
        assertEquals("ASYNC_SINGLE_THREAD", value.name());
    }

    @Test
    public void testAsyncParallelValue() {
        EventHandleStrategyEnum value = EventHandleStrategyEnum.ASYNC_PARALLEL;
        assertNotNull(value);
        assertEquals("ASYNC_PARALLEL", value.name());
    }

    @Test
    public void testValueOf() {
        assertEquals(EventHandleStrategyEnum.SYNC, EventHandleStrategyEnum.valueOf("SYNC"));
        assertEquals(EventHandleStrategyEnum.ASYNC_SINGLE_THREAD, EventHandleStrategyEnum.valueOf("ASYNC_SINGLE_THREAD"));
        assertEquals(EventHandleStrategyEnum.ASYNC_PARALLEL, EventHandleStrategyEnum.valueOf("ASYNC_PARALLEL"));
    }

    @Test
    public void testOrdinalOrder() {
        assertEquals(0, EventHandleStrategyEnum.SYNC.ordinal());
        assertEquals(1, EventHandleStrategyEnum.ASYNC_SINGLE_THREAD.ordinal());
        assertEquals(2, EventHandleStrategyEnum.ASYNC_PARALLEL.ordinal());
    }
}
