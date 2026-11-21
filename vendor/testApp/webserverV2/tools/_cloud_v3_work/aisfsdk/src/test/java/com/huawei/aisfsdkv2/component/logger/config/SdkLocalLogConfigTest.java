/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logger.config;

import com.huawei.aisfsdkv2.api.log.LogLevelEnum;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * SdkLocalLogConfig 单元测试
 */
public class SdkLocalLogConfigTest {

    @Test
    public void testDefaultLogLevel() {
        SdkLocalLogConfig config = new SdkLocalLogConfig();
        assertEquals(LogLevelEnum.INFO, config.getLogLevel());
    }

    @Test
    public void testDefaultMaxLogFileSize() {
        SdkLocalLogConfig config = new SdkLocalLogConfig();
        assertEquals(10 * 1024 * 1024L, config.getMaxLogFileSize());
    }

    @Test
    public void testDefaultMaxLogFileCount() {
        SdkLocalLogConfig config = new SdkLocalLogConfig();
        assertEquals(5, config.getMaxLogFileCount());
    }

    @Test
    public void testSetLogLevel() {
        SdkLocalLogConfig config = new SdkLocalLogConfig();
        config.setLogLevel(LogLevelEnum.DEBUG);
        assertEquals(LogLevelEnum.DEBUG, config.getLogLevel());
    }

    @Test
    public void testSetMaxLogFileSize() {
        SdkLocalLogConfig config = new SdkLocalLogConfig();
        config.setMaxLogFileSize(5 * 1024 * 1024L);
        assertEquals(5 * 1024 * 1024L, config.getMaxLogFileSize());
    }

    @Test
    public void testSetMaxLogFileCount() {
        SdkLocalLogConfig config = new SdkLocalLogConfig();
        config.setMaxLogFileCount(10);
        assertEquals(10, config.getMaxLogFileCount());
    }

    @Test
    public void testSetLogLevel_warn() {
        SdkLocalLogConfig config = new SdkLocalLogConfig();
        config.setLogLevel(LogLevelEnum.WARN);
        assertEquals(LogLevelEnum.WARN, config.getLogLevel());
    }

    @Test
    public void testSetLogLevel_error() {
        SdkLocalLogConfig config = new SdkLocalLogConfig();
        config.setLogLevel(LogLevelEnum.ERROR);
        assertEquals(LogLevelEnum.ERROR, config.getLogLevel());
    }
}
