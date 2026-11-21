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
 * GuiTaskInfo 测试
 */
public class GuiTaskInfoTest {

    private GuiTaskInfo guiTaskInfo;

    @Before
    public void setUp() {
        guiTaskInfo = new GuiTaskInfo();
    }

    @Test
    public void testDefaultValues() {
        assertNull(guiTaskInfo.getGuiTaskType());
        assertNull(guiTaskInfo.getText());
    }

    @Test
    public void testSetAndGetGuiTaskType() {
        guiTaskInfo.setGuiTaskType(GuiTaskEnum.START_GUI);
        assertEquals(GuiTaskEnum.START_GUI, guiTaskInfo.getGuiTaskType());
    }

    @Test
    public void testSetAndGetText() {
        guiTaskInfo.setText("Some text");
        assertEquals("Some text", guiTaskInfo.getText());
    }

    @Test
    public void testAllGuiTaskTypes() {
        for (GuiTaskEnum type : GuiTaskEnum.values()) {
            guiTaskInfo.setGuiTaskType(type);
            assertEquals(type, guiTaskInfo.getGuiTaskType());
        }
    }

    @Test
    public void testToStringWithType() {
        guiTaskInfo.setGuiTaskType(GuiTaskEnum.START_GUI);
        guiTaskInfo.setText("start text");
        String str = guiTaskInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("START_GUI"));
        assertTrue(str.contains("start text"));
    }

    @Test
    public void testToStringWithNullType() {
        String str = guiTaskInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("null"));
    }

    @Test
    public void testToStringWithNullText() {
        guiTaskInfo.setGuiTaskType(GuiTaskEnum.STOP_GUI);
        String str = guiTaskInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("STOP_GUI"));
        // text 为 null 时不应出现在 toString 中
    }

    @Test
    public void testToStringWithEmptyText() {
        guiTaskInfo.setGuiTaskType(GuiTaskEnum.START_PAYMENT);
        guiTaskInfo.setText("");
        String str = guiTaskInfo.toString();
        assertNotNull(str);
    }
}
