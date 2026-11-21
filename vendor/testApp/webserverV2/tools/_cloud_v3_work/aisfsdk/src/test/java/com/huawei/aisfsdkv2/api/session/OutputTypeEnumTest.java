/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * OutputTypeEnum 测试
 */
public class OutputTypeEnumTest {

    @Test
    public void testEnumCount() {
        assertEquals(5, OutputTypeEnum.values().length);
    }

    @Test
    public void testGetDescription() {
        assertEquals("TEXT", OutputTypeEnum.TEXT.getDescription());
        assertEquals("NOTIFY", OutputTypeEnum.NOTIFY.getDescription());
        assertEquals("TASK_STATUS", OutputTypeEnum.TASK_STATUS.getDescription());
        assertEquals("GUI_TASK", OutputTypeEnum.GUI_TASK.getDescription());
        assertEquals("DIALOG", OutputTypeEnum.DIALOG.getDescription());
    }

    @Test
    public void testEnumNames() {
        assertEquals("TEXT", OutputTypeEnum.TEXT.name());
        assertEquals("NOTIFY", OutputTypeEnum.NOTIFY.name());
        assertEquals("TASK_STATUS", OutputTypeEnum.TASK_STATUS.name());
        assertEquals("GUI_TASK", OutputTypeEnum.GUI_TASK.name());
        assertEquals("DIALOG", OutputTypeEnum.DIALOG.name());
    }

    @Test
    public void testValueOf() {
        assertEquals(OutputTypeEnum.TEXT, OutputTypeEnum.valueOf("TEXT"));
        assertEquals(OutputTypeEnum.DIALOG, OutputTypeEnum.valueOf("DIALOG"));
    }
}
