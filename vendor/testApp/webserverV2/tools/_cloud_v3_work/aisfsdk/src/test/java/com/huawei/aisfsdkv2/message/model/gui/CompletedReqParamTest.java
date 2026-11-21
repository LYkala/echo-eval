/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * CompletedReqParam 测试
 */
public class CompletedReqParamTest {

    private CompletedReqParam param;

    @Before
    public void setUp() {
        param = new CompletedReqParam();
    }

    @Test
    public void testConstructor() {
        assertNotNull(param);
    }
}
