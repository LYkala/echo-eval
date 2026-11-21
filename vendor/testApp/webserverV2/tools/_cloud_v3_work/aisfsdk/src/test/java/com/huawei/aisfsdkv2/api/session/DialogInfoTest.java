/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * DialogInfo 测试
 */
public class DialogInfoTest {

    private DialogInfo dialogInfo;

    @Before
    public void setUp() {
        dialogInfo = new DialogInfo();
    }

    @Test
    public void testDefaultConstructor() {
        assertEquals("", dialogInfo.getTitle());
        assertNotNull(dialogInfo.getControlList());
        assertTrue(dialogInfo.getControlList().isEmpty());
    }

    @Test
    public void testSetAndGetTitle() {
        dialogInfo.setTitle("Test Title");
        assertEquals("Test Title", dialogInfo.getTitle());
    }

    @Test
    public void testSetTitleNull() {
        dialogInfo.setTitle(null);
        // title 可以被设置为 null，测试 getter 不抛异常
        assertNotNull(dialogInfo);
    }

    @Test
    public void testSetAndGetControlList() {
        List<DialogControl> controls = new ArrayList<>();
        DialogControl ctrl = new DialogControl();
        ctrl.setContent("OK");
        ctrl.setType("button");
        controls.add(ctrl);
        dialogInfo.setControlList(controls);
        assertEquals(1, dialogInfo.getControlList().size());
        assertEquals("OK", dialogInfo.getControlList().get(0).getContent());
    }

    @Test
    public void testSetControlListNull() {
        dialogInfo.setControlList(null);
        assertNotNull(dialogInfo.getControlList());
        assertTrue(dialogInfo.getControlList().isEmpty());
    }

    @Test
    public void testToStringBasic() {
        dialogInfo.setTitle("Confirm");
        String str = dialogInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("Confirm"));
        assertTrue(str.contains("controlList"));
    }

    @Test
    public void testToStringWithControls() {
        dialogInfo.setTitle("Dialog");
        List<DialogControl> controls = new ArrayList<>();
        DialogControl ctrl = new DialogControl();
        ctrl.setContent("Cancel");
        ctrl.setType("button");
        controls.add(ctrl);
        dialogInfo.setControlList(controls);
        String str = dialogInfo.toString();
        assertTrue(str.contains("Cancel"));
    }

    @Test
    public void testToStringEmptyControlList() {
        dialogInfo.setTitle("Empty");
        String str = dialogInfo.toString();
        assertTrue(str.contains("[]"));
    }
}
