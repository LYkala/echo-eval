/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.timertask;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * TimerTaskModel 测试
 */
public class TimerTaskModelTest {

    @Test
    public void testSuccessWithData() {
        String data = "result";
        TimerTaskModel<String> model = TimerTaskModel.success(data);
        assertEquals(200, model.getRetCode());
        assertEquals("success", model.getRetMsg());
        assertEquals("result", model.getData());
        assertTrue(model.isSuccess());
    }

    @Test
    public void testSuccessWithCustomMessage() {
        TimerTaskModel<Integer> model = TimerTaskModel.success(42, "OK");
        assertEquals(200, model.getRetCode());
        assertEquals("OK", model.getRetMsg());
        assertEquals(Integer.valueOf(42), model.getData());
        assertTrue(model.isSuccess());
    }

    @Test
    public void testError() {
        TimerTaskModel<String> model = TimerTaskModel.error(500, "Internal Error");
        assertEquals(500, model.getRetCode());
        assertEquals("Internal Error", model.getRetMsg());
        assertNull(model.getData());
        assertFalse(model.isSuccess());
    }

    @Test
    public void testErrorCode404() {
        TimerTaskModel<Object> model = TimerTaskModel.error(404, "Not Found");
        assertEquals(404, model.getRetCode());
        assertFalse(model.isSuccess());
    }

    @Test
    public void testNullRetMsgDefaultsToEmpty() {
        TimerTaskModel<String> model = new TimerTaskModel<>(200, null, "data");
        assertEquals("", model.getRetMsg());
    }

    @Test
    public void testSuccessWithNullData() {
        TimerTaskModel<String> model = TimerTaskModel.success(null);
        assertTrue(model.isSuccess());
        assertNull(model.getData());
    }

    @Test
    public void testIsSuccessOnlyFor200() {
        assertTrue(new TimerTaskModel<>(200, "ok", null).isSuccess());
        assertFalse(new TimerTaskModel<>(201, "created", null).isSuccess());
        assertFalse(new TimerTaskModel<>(400, "bad request", null).isSuccess());
        assertFalse(new TimerTaskModel<>(0, "zero", null).isSuccess());
    }

    @Test
    public void testGenericTypeInteger() {
        TimerTaskModel<Integer> model = TimerTaskModel.success(100);
        assertEquals(Integer.valueOf(100), model.getData());
    }

    @Test
    public void testSuccessCustomMsgNullDefaultsEmpty() {
        TimerTaskModel<String> model = TimerTaskModel.success("data", null);
        assertNotNull(model.getRetMsg());
        assertEquals("", model.getRetMsg());
    }
}
