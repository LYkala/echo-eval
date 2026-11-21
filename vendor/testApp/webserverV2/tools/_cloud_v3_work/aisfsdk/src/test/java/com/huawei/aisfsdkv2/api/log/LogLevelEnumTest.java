/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.log;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * LogLevelEnum 测试
 */
public class LogLevelEnumTest {

    @Test
    public void testEnumCount() {
        assertEquals(4, LogLevelEnum.values().length);
    }

    @Test
    public void testGetLogLevel() {
        assertEquals(1, LogLevelEnum.DEBUG.getLogLevel());
        assertEquals(2, LogLevelEnum.INFO.getLogLevel());
        assertEquals(3, LogLevelEnum.WARN.getLogLevel());
        assertEquals(4, LogLevelEnum.ERROR.getLogLevel());
    }

    @Test
    public void testLevelOrdering() {
        assertTrue(LogLevelEnum.DEBUG.getLogLevel() < LogLevelEnum.INFO.getLogLevel());
        assertTrue(LogLevelEnum.INFO.getLogLevel() < LogLevelEnum.WARN.getLogLevel());
        assertTrue(LogLevelEnum.WARN.getLogLevel() < LogLevelEnum.ERROR.getLogLevel());
    }

    @Test
    public void testCompareEqual() {
        assertEquals(0, LogLevelEnum.DEBUG.compare(LogLevelEnum.DEBUG));
        assertEquals(0, LogLevelEnum.INFO.compare(LogLevelEnum.INFO));
    }

    @Test
    public void testCompareHigherLevel() {
        assertTrue(LogLevelEnum.ERROR.compare(LogLevelEnum.DEBUG) > 0);
        assertTrue(LogLevelEnum.WARN.compare(LogLevelEnum.INFO) > 0);
        assertTrue(LogLevelEnum.INFO.compare(LogLevelEnum.DEBUG) > 0);
    }

    @Test
    public void testCompareLowerLevel() {
        assertTrue(LogLevelEnum.DEBUG.compare(LogLevelEnum.ERROR) < 0);
        assertTrue(LogLevelEnum.INFO.compare(LogLevelEnum.WARN) < 0);
    }

    @Test
    public void testCompareResult() {
        assertEquals(3, LogLevelEnum.ERROR.compare(LogLevelEnum.DEBUG)); // 4-1
        assertEquals(-2, LogLevelEnum.DEBUG.compare(LogLevelEnum.WARN)); // 1-3
    }

    @Test
    public void testValueOf() {
        assertEquals(LogLevelEnum.DEBUG, LogLevelEnum.valueOf("DEBUG"));
        assertEquals(LogLevelEnum.ERROR, LogLevelEnum.valueOf("ERROR"));
    }
}
