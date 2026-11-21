/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.log;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * SdkLogConfig 测试
 */
public class SdkLogConfigTest {

    private SdkLogConfig config;

    @Before
    public void setUp() {
        config = new SdkLogConfig();
    }

    @Test
    public void testDefaultValues() {
        assertNull(config.getSdkLocalLogConfig());
        assertFalse(config.isStdoutEnable());
    }

    @Test
    public void testSetAndGetSdkLocalLogConfig() {
        SdkLocalLogConfig localConfig = new SdkLocalLogConfig();
        config.setSdkLocalLogConfig(localConfig);
        assertEquals(localConfig, config.getSdkLocalLogConfig());
    }

    @Test
    public void testSetNullSdkLocalLogConfig() {
        config.setSdkLocalLogConfig(null);
        assertNull(config.getSdkLocalLogConfig());
    }

    @Test
    public void testSetAndGetStdoutEnable() {
        config.setStdoutEnable(true);
        assertTrue(config.isStdoutEnable());
        config.setStdoutEnable(false);
        assertFalse(config.isStdoutEnable());
    }

    @Test
    public void testToString() {
        config.setStdoutEnable(true);
        String str = config.toString();
        assertNotNull(str);
        assertTrue(str.contains("SdkLogConfig"));
        assertTrue(str.contains("true"));
    }

    @Test
    public void testToStringWithLocalConfig() {
        SdkLocalLogConfig localConfig = new SdkLocalLogConfig();
        config.setSdkLocalLogConfig(localConfig);
        String str = config.toString();
        assertNotNull(str);
        assertTrue(str.contains("SdkLogConfig"));
        assertTrue(str.contains("sdkLocalLogConfig"));
    }

    @Test
    public void testToStringWithNullLocalConfig() {
        String str = config.toString();
        assertNotNull(str);
        assertTrue(str.contains("SdkLogConfig"));
    }
}
