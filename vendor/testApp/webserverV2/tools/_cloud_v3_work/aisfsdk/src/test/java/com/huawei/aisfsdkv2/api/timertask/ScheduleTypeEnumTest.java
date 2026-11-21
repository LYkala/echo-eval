/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.timertask;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * ScheduleTypeEnum 测试
 */
public class ScheduleTypeEnumTest {

    @Test
    public void testEnumCount() {
        assertEquals(2, ScheduleTypeEnum.values().length);
    }

    @Test
    public void testGetValue() {
        assertEquals("once", ScheduleTypeEnum.ONCE.getValue());
        assertEquals("recurring", ScheduleTypeEnum.RECURRING.getValue());
    }

    @Test
    public void testFromValueByStringValue() {
        assertEquals(ScheduleTypeEnum.ONCE, ScheduleTypeEnum.fromValue("once"));
        assertEquals(ScheduleTypeEnum.RECURRING, ScheduleTypeEnum.fromValue("recurring"));
    }

    @Test
    public void testFromValueByEnumName() {
        assertEquals(ScheduleTypeEnum.ONCE, ScheduleTypeEnum.fromValue("ONCE"));
        assertEquals(ScheduleTypeEnum.RECURRING, ScheduleTypeEnum.fromValue("RECURRING"));
    }

    @Test
    public void testFromValueCaseInsensitive() {
        assertEquals(ScheduleTypeEnum.ONCE, ScheduleTypeEnum.fromValue("Once"));
        assertEquals(ScheduleTypeEnum.RECURRING, ScheduleTypeEnum.fromValue("Recurring"));
    }

    @Test
    public void testFromValueNull() {
        assertNull(ScheduleTypeEnum.fromValue(null));
    }

    @Test
    public void testFromValueEmpty() {
        assertNull(ScheduleTypeEnum.fromValue(""));
    }

    @Test
    public void testFromValueUnknown() {
        assertNull(ScheduleTypeEnum.fromValue("daily"));
        assertNull(ScheduleTypeEnum.fromValue("unknown"));
    }

    @Test
    public void testFromValueRoundTrip() {
        for (ScheduleTypeEnum e : ScheduleTypeEnum.values()) {
            assertNotNull(ScheduleTypeEnum.fromValue(e.getValue()));
            assertEquals(e, ScheduleTypeEnum.fromValue(e.getValue()));
        }
    }
}
