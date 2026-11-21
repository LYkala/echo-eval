/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * GUI 模型类测试
 */
public class GuiModelsTest {

    // ======= ClickReqParam =======

    @Test
    public void testClickReqParam_DefaultConstructor() {
        ClickReqParam param = new ClickReqParam();
        assertEquals(-1, param.getStartX());
        assertEquals(-1, param.getStartY());
        assertTrue(param.isNeedScreenShot());
        assertEquals(2, param.getMaxScreenshotTime());
    }

    @Test
    public void testClickReqParam_CoordinatesConstructor() {
        ClickReqParam param = new ClickReqParam(100, 200);
        assertEquals(100, param.getStartX());
        assertEquals(200, param.getStartY());
    }

    @Test
    public void testClickReqParam_Setters() {
        ClickReqParam param = new ClickReqParam();
        param.setStartX(50);
        param.setStartY(100);
        param.setNeedScreenShot(false);
        param.setMaxScreenshotTime(5);
        assertEquals(50, param.getStartX());
        assertEquals(100, param.getStartY());
        assertFalse(param.isNeedScreenShot());
        assertEquals(5, param.getMaxScreenshotTime());
    }

    @Test
    public void testClickReqParam_ToString() {
        ClickReqParam param = new ClickReqParam(10, 20);
        String str = param.toString();
        assertTrue(str.contains("StartX"));
        assertTrue(str.contains("10"));
        assertTrue(str.contains("20"));
    }

    // ======= SwipeReqParam =======

    @Test
    public void testSwipeReqParam_DefaultConstructor() {
        SwipeReqParam param = new SwipeReqParam();
        assertEquals(-1, param.getStartX());
        assertEquals(-1, param.getStartY());
        assertEquals(-1, param.getEndX());
        assertEquals(-1, param.getEndY());
        assertEquals(300, param.getDuration());
        assertEquals(0, param.getSleepTime());
        assertTrue(param.isNeedScreenShot());
    }

    @Test
    public void testSwipeReqParam_FullConstructor() {
        SwipeReqParam param = new SwipeReqParam(0, 100, 100, 100, 500);
        assertEquals(0, param.getStartX());
        assertEquals(100, param.getStartY());
        assertEquals(100, param.getEndX());
        assertEquals(100, param.getEndY());
        assertEquals(500, param.getDuration());
    }

    @Test
    public void testSwipeReqParam_Setters() {
        SwipeReqParam param = new SwipeReqParam();
        param.setStartX(10);
        param.setStartY(20);
        param.setEndX(30);
        param.setEndY(40);
        param.setDuration(100);
        param.setSleepTime(50);
        param.setNeedScreenShot(false);
        param.setMaxScreenshotTime(3);
        assertEquals(10, param.getStartX());
        assertEquals(20, param.getStartY());
        assertEquals(30, param.getEndX());
        assertEquals(40, param.getEndY());
        assertEquals(100, param.getDuration());
        assertEquals(50, param.getSleepTime());
        assertFalse(param.isNeedScreenShot());
        assertEquals(3, param.getMaxScreenshotTime());
    }

    @Test
    public void testSwipeReqParam_ToString() {
        SwipeReqParam param = new SwipeReqParam(0, 0, 100, 100, 300);
        String str = param.toString();
        assertNotNull(str);
        assertTrue(str.contains("StartX"));
        assertTrue(str.contains("EndX"));
    }

    // ======= GuiData =======

    @Test
    public void testGuiData_DefaultValues() {
        GuiData data = new GuiData();
        assertEquals("DataExchange", data.getMessageType());
        assertEquals("", data.getUserOutputText());
        assertNotNull(data.getExtParam());
    }

    @Test
    public void testGuiData_Setters() {
        GuiData data = new GuiData();
        data.setMessageType("TestType");
        data.setUserOutputText("test output");
        assertEquals("TestType", data.getMessageType());
        assertEquals("test output", data.getUserOutputText());
    }

    @Test
    public void testGuiData_SetExtParam() {
        GuiData data = new GuiData();
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("key1", "value1");
        data.setExtParam(map);
        assertEquals("value1", data.getExtParam().get("key1"));
    }

    @Test
    public void testGuiData_SetExtParam_Null() {
        GuiData data = new GuiData();
        data.setExtParam(null);
        assertNotNull(data.getExtParam());
    }

    // ======= InputTextReqParam =======

    @Test
    public void testInputTextReqParam_DefaultConstructor() {
        InputTextReqParam param = new InputTextReqParam();
        assertEquals(0, param.getStartX());
        assertEquals(0, param.getStartY());
        assertEquals("", param.getInputText());
        assertFalse(param.isNeedSearch());
        assertTrue(param.isNeedScreenShot());
    }

    @Test
    public void testInputTextReqParam_TextConstructor() {
        InputTextReqParam param = new InputTextReqParam("hello");
        assertEquals("hello", param.getInputText());
    }

    @Test
    public void testInputTextReqParam_Setters() {
        InputTextReqParam param = new InputTextReqParam();
        param.setStartX(10);
        param.setStartY(20);
        param.setInputText("test input");
        param.setNeedSearch(true);
        param.setNeedScreenShot(false);
        param.setMaxScreenshotTime(5);
        assertEquals(10, param.getStartX());
        assertEquals(20, param.getStartY());
        assertEquals("test input", param.getInputText());
        assertTrue(param.isNeedSearch());
        assertFalse(param.isNeedScreenShot());
        assertEquals(5, param.getMaxScreenshotTime());
    }

    @Test
    public void testInputTextReqParam_ToString() {
        InputTextReqParam param = new InputTextReqParam("hello");
        String str = param.toString();
        assertNotNull(str);
        assertTrue(str.contains("InputText"));
    }

    // ======= GuiPayload =======

    @Test
    public void testGuiPayload_DefaultValues() {
        GuiPayload payload = new GuiPayload();
        assertEquals("GUI", payload.getFunction());
        assertNotNull(payload.getCmdType());
    }

    @Test
    public void testGuiPayload_Setters() {
        GuiPayload payload = new GuiPayload();
        payload.setFunction("TestFunc");
        assertEquals("TestFunc", payload.getFunction());
    }

    @Test
    public void testGuiPayload_SetParams() {
        GuiPayload payload = new GuiPayload();
        ClickReqParam clickParam = new ClickReqParam(100, 200);
        payload.setParams(clickParam);
        assertNotNull(payload.getParams());
    }
}
