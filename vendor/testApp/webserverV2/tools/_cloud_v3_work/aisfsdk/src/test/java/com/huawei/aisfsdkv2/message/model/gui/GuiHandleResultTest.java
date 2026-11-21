/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * GuiHandleResult 单元测试
 */
public class GuiHandleResultTest {

    // ======= 默认构造 =======

    @Test
    public void testDefaultConstructor_ResultIsFalse() {
        GuiHandleResult result = new GuiHandleResult();
        assertFalse(result.isResult());
    }

    @Test
    public void testDefaultConstructor_DefaultCode() {
        GuiHandleResult result = new GuiHandleResult();
        assertEquals(GuiHandleResult.DEFAULT_CODE, result.getCode());
        assertEquals(101, result.getCode());
    }

    @Test
    public void testDefaultConstructor_DefaultDescription() {
        GuiHandleResult result = new GuiHandleResult();
        assertEquals("Internal error", result.getDescription());
    }

    // ======= 全参构造 =======

    @Test
    public void testFullConstructor() {
        GuiHandleResult result = new GuiHandleResult(true, 200, "OK");
        assertTrue(result.isResult());
        assertEquals(200, result.getCode());
        assertEquals("OK", result.getDescription());
    }

    // ======= success() =======

    @Test
    public void testSuccess_ResultIsTrue() {
        GuiHandleResult result = GuiHandleResult.success();
        assertTrue(result.isResult());
    }

    @Test
    public void testSuccess_CodeIsZero() {
        GuiHandleResult result = GuiHandleResult.success();
        assertEquals(0, result.getCode());
    }

    @Test
    public void testSuccess_DescriptionIsSuccess() {
        GuiHandleResult result = GuiHandleResult.success();
        assertEquals("Success", result.getDescription());
    }

    // ======= failure(code, desc) =======

    @Test
    public void testFailureWithCodeAndDesc_ResultIsFalse() {
        GuiHandleResult result = GuiHandleResult.failure(404, "Not Found");
        assertFalse(result.isResult());
    }

    @Test
    public void testFailureWithCodeAndDesc_CorrectFields() {
        GuiHandleResult result = GuiHandleResult.failure(404, "Not Found");
        assertEquals(404, result.getCode());
        assertEquals("Not Found", result.getDescription());
    }

    // ======= failure(desc) =======

    @Test
    public void testFailureWithDescOnly_UsesDefaultCode() {
        GuiHandleResult result = GuiHandleResult.failure("some error");
        assertFalse(result.isResult());
        assertEquals(101, result.getCode());
        assertEquals("some error", result.getDescription());
    }

    // ======= failure() =======

    @Test
    public void testFailureNoArgs_UsesDefaults() {
        GuiHandleResult result = GuiHandleResult.failure();
        assertFalse(result.isResult());
        assertEquals(101, result.getCode());
        assertEquals("Internal error", result.getDescription());
    }

    // ======= setters =======

    @Test
    public void testSetResult() {
        GuiHandleResult result = new GuiHandleResult();
        result.setResult(true);
        assertTrue(result.isResult());
    }

    @Test
    public void testSetCode() {
        GuiHandleResult result = new GuiHandleResult();
        result.setCode(500);
        assertEquals(500, result.getCode());
    }

    @Test
    public void testSetDescription() {
        GuiHandleResult result = new GuiHandleResult();
        result.setDescription("Custom message");
        assertEquals("Custom message", result.getDescription());
    }

    // ======= toString =======

    @Test
    public void testToString_NotNull() {
        GuiHandleResult result = GuiHandleResult.success();
        assertNotNull(result.toString());
    }

    @Test
    public void testToString_ContainsFields() {
        GuiHandleResult result = new GuiHandleResult(true, 200, "OK");
        String str = result.toString();
        assertTrue(str.contains("true"));
        assertTrue(str.contains("200"));
        assertTrue(str.contains("OK"));
    }

    // ======= DEFAULT_CODE 常量 =======

    @Test
    public void testDefaultCodeConstant() {
        assertEquals(101, GuiHandleResult.DEFAULT_CODE);
    }
}
