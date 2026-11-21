/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * GuiTaskEnum 枚举测试
 */
public class GuiTaskEnumTest {

    @Test
    public void testEnumCount() {
        assertEquals(7, GuiTaskEnum.values().length);
    }

    @Test
    public void testGetDescription() {
        assertEquals("START_GUI", GuiTaskEnum.START_GUI.getDescription());
        assertEquals("STOP_GUI", GuiTaskEnum.STOP_GUI.getDescription());
        assertEquals("START_PAYMENT", GuiTaskEnum.START_PAYMENT.getDescription());
        assertEquals("START_MANUAL", GuiTaskEnum.START_MANUAL.getDescription());
        assertEquals("START_TAKEOVER", GuiTaskEnum.START_TAKEOVER.getDescription());
        assertEquals("STOP_TAKEOVER", GuiTaskEnum.STOP_TAKEOVER.getDescription());
        assertEquals("CANCEL_TAKEOVER", GuiTaskEnum.CANCEL_TAKEOVER.getDescription());
    }

    @Test
    public void testGetServerType() {
        assertEquals("STARTGUI", GuiTaskEnum.START_GUI.getServerType());
        assertEquals("STOPGUI", GuiTaskEnum.STOP_GUI.getServerType());
        assertEquals("STARTPAYMENT", GuiTaskEnum.START_PAYMENT.getServerType());
        assertEquals("STARTMANUAL", GuiTaskEnum.START_MANUAL.getServerType());
        assertEquals("STARTTAKEOVER", GuiTaskEnum.START_TAKEOVER.getServerType());
        assertEquals("STOPTAKEOVER", GuiTaskEnum.STOP_TAKEOVER.getServerType());
        assertEquals("CANCELTAKEOVER", GuiTaskEnum.CANCEL_TAKEOVER.getServerType());
    }

    @Test
    public void testFromTypeAllValues() {
        assertEquals(GuiTaskEnum.START_GUI, GuiTaskEnum.fromType("STARTGUI"));
        assertEquals(GuiTaskEnum.STOP_GUI, GuiTaskEnum.fromType("STOPGUI"));
        assertEquals(GuiTaskEnum.START_PAYMENT, GuiTaskEnum.fromType("STARTPAYMENT"));
        assertEquals(GuiTaskEnum.START_MANUAL, GuiTaskEnum.fromType("STARTMANUAL"));
        assertEquals(GuiTaskEnum.START_TAKEOVER, GuiTaskEnum.fromType("STARTTAKEOVER"));
        assertEquals(GuiTaskEnum.STOP_TAKEOVER, GuiTaskEnum.fromType("STOPTAKEOVER"));
        assertEquals(GuiTaskEnum.CANCEL_TAKEOVER, GuiTaskEnum.fromType("CANCELTAKEOVER"));
    }

    @Test
    public void testFromTypeNull() {
        assertNull(GuiTaskEnum.fromType(null));
    }

    @Test
    public void testFromTypeEmpty() {
        assertNull(GuiTaskEnum.fromType(""));
    }

    @Test
    public void testFromTypeUnknown() {
        assertNull(GuiTaskEnum.fromType("UNKNOWN"));
        assertNull(GuiTaskEnum.fromType("startgui")); // case-sensitive
        assertNull(GuiTaskEnum.fromType("STARTGUI2"));
    }

    @Test
    public void testFromTypeRoundTrip() {
        for (GuiTaskEnum task : GuiTaskEnum.values()) {
            assertNotNull(GuiTaskEnum.fromType(task.getServerType()));
            assertEquals(task, GuiTaskEnum.fromType(task.getServerType()));
        }
    }
}
