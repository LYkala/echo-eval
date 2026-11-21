/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.location;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * LocationOprResult 单元测试
 */
public class LocationOprResultTest {

    @Test
    public void testConstructor_Success() {
        LocationOprResult result = new LocationOprResult(true, "success");
        assertTrue(result.isResult());
        assertEquals("success", result.getMessage());
    }

    @Test
    public void testConstructor_Failure() {
        LocationOprResult result = new LocationOprResult(false, "error occurred");
        assertFalse(result.isResult());
        assertEquals("error occurred", result.getMessage());
    }

    @Test
    public void testIsResult_True() {
        LocationOprResult result = new LocationOprResult(true, "ok");
        assertTrue(result.isResult());
    }

    @Test
    public void testIsResult_False() {
        LocationOprResult result = new LocationOprResult(false, "failed");
        assertFalse(result.isResult());
    }

    @Test
    public void testGetMessage_NotNull() {
        LocationOprResult result = new LocationOprResult(true, "msg");
        assertNotNull(result.getMessage());
    }

    @Test
    public void testGetMessage_Empty() {
        LocationOprResult result = new LocationOprResult(false, "");
        assertEquals("", result.getMessage());
    }

    @Test
    public void testGetMessage_NullMessage() {
        LocationOprResult result = new LocationOprResult(false, null);
        assertFalse(result.isResult());
        assertEquals(null, result.getMessage());
    }
}
