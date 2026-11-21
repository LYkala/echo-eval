/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.log;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * SdkLocalLogConfig 测试
 */
public class SdkLocalLogConfigTest {

    private SdkLocalLogConfig config;

    @Before
    public void setUp() {
        config = new SdkLocalLogConfig();
    }

    @Test
    public void testDefaultValues() {
        assertEquals(LogLevelEnum.INFO, config.getLogLevel());
        assertEquals(10 * 1024 * 1024, config.getMaxLogFileSize());
        assertEquals(10, config.getMaxLogFileCount());
    }

    @Test
    public void testSetAndGetLogLevel() {
        config.setLogLevel(LogLevelEnum.DEBUG);
        assertEquals(LogLevelEnum.DEBUG, config.getLogLevel());
    }

    @Test
    public void testSetAllLogLevels() {
        for (LogLevelEnum level : LogLevelEnum.values()) {
            config.setLogLevel(level);
            assertEquals(level, config.getLogLevel());
        }
    }

    @Test
    public void testSetAndGetMaxLogFileSize() {
        config.setMaxLogFileSize(5 * 1024 * 1024);
        assertEquals(5 * 1024 * 1024, config.getMaxLogFileSize());
    }

    @Test
    public void testSetMaxLogFileSizeZero() {
        config.setMaxLogFileSize(0);
        assertEquals(0, config.getMaxLogFileSize());
    }

    @Test
    public void testSetAndGetMaxLogFileCount() {
        config.setMaxLogFileCount(20);
        assertEquals(20, config.getMaxLogFileCount());
    }

    @Test
    public void testSetMaxLogFileCountZero() {
        config.setMaxLogFileCount(0);
        assertEquals(0, config.getMaxLogFileCount());
    }

    @Test
    public void testToString() {
        String str = config.toString();
        assertNotNull(str);
        assertTrue(str.contains("SdkLocalLogConfig"));
        assertTrue(str.contains("logLevel"));
        assertTrue(str.contains("maxLogFileSize"));
        assertTrue(str.contains("maxLogFileCount"));
    }

    @Test
    public void testToStringWithCustomValues() {
        config.setLogLevel(LogLevelEnum.DEBUG);
        config.setMaxLogFileSize(1024);
        config.setMaxLogFileCount(5);
        String str = config.toString();
        assertNotNull(str);
        assertTrue(str.contains("1024"));
        assertTrue(str.contains("5"));
    }

    @Test
    public void testFieldOverwrite() {
        config.setMaxLogFileSize(100);
        assertEquals(100, config.getMaxLogFileSize());
        config.setMaxLogFileSize(200);
        assertEquals(200, config.getMaxLogFileSize());
    }
}
