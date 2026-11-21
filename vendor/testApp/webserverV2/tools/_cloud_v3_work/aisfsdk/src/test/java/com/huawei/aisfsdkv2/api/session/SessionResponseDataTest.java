/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * SessionResponseData 测试
 */
public class SessionResponseDataTest {

    private SessionResponseData data;

    @Before
    public void setUp() {
        data = new SessionResponseData();
    }

    @Test
    public void testDefaultValues() {
        assertNull(data.getOutputType());
        assertNull(data.getTextInfo());
        assertNull(data.getNotifyInfo());
        assertNull(data.getTaskInfo());
        assertNull(data.getGuiTaskInfo());
        assertNull(data.getDialogInfo());
    }

    @Test
    public void testSetOutputType() {
        data.setOutputType(OutputTypeEnum.TEXT);
        assertEquals(OutputTypeEnum.TEXT, data.getOutputType());
    }

    @Test
    public void testSetTextInfo() {
        TextInfo textInfo = new TextInfo();
        textInfo.setText("Hello");
        data.setTextInfo(textInfo);
        assertNotNull(data.getTextInfo());
        assertEquals("Hello", data.getTextInfo().getText());
    }

    @Test
    public void testSetNotifyInfo() {
        NotifyInfo notifyInfo = new NotifyInfo();
        notifyInfo.setCode(1);
        data.setNotifyInfo(notifyInfo);
        assertNotNull(data.getNotifyInfo());
        assertEquals(1, data.getNotifyInfo().getCode());
    }

    @Test
    public void testSetTaskInfo() {
        TaskInfo taskInfo = new TaskInfo();
        taskInfo.setTaskCompleted(true);
        data.setTaskInfo(taskInfo);
        assertNotNull(data.getTaskInfo());
        assertTrue(data.getTaskInfo().isTaskCompleted());
    }

    @Test
    public void testSetGuiTaskInfo() {
        GuiTaskInfo guiTaskInfo = new GuiTaskInfo();
        guiTaskInfo.setGuiTaskType(GuiTaskEnum.START_GUI);
        data.setGuiTaskInfo(guiTaskInfo);
        assertNotNull(data.getGuiTaskInfo());
        assertEquals(GuiTaskEnum.START_GUI, data.getGuiTaskInfo().getGuiTaskType());
    }

    @Test
    public void testSetDialogInfo() {
        DialogInfo dialogInfo = new DialogInfo();
        dialogInfo.setTitle("Test Dialog");
        data.setDialogInfo(dialogInfo);
        assertNotNull(data.getDialogInfo());
        assertEquals("Test Dialog", data.getDialogInfo().getTitle());
    }

    @Test
    public void testToStringWithTextType() {
        data.setOutputType(OutputTypeEnum.TEXT);
        TextInfo textInfo = new TextInfo();
        textInfo.setText("response text");
        data.setTextInfo(textInfo);
        String str = data.toString();
        assertNotNull(str);
        assertTrue(str.contains("TEXT"));
        assertTrue(str.contains("response text"));
    }

    @Test
    public void testToStringWithNullOutputType() {
        String str = data.toString();
        assertNotNull(str);
        assertTrue(str.contains("null"));
    }

    @Test
    public void testAllOutputTypes() {
        for (OutputTypeEnum type : OutputTypeEnum.values()) {
            data.setOutputType(type);
            assertEquals(type, data.getOutputType());
        }
    }

    @Test
    public void testToStringWithAllFields() {
        data.setOutputType(OutputTypeEnum.DIALOG);
        DialogInfo dialogInfo = new DialogInfo();
        dialogInfo.setTitle("Alert");
        data.setDialogInfo(dialogInfo);
        String str = data.toString();
        assertTrue(str.contains("DIALOG"));
        assertTrue(str.contains("Alert"));
    }
}
