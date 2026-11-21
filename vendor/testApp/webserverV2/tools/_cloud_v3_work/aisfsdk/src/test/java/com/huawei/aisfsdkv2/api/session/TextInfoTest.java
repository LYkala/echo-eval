/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TextInfo 测试
 */
public class TextInfoTest {

    private TextInfo textInfo;

    @Before
    public void setUp() {
        textInfo = new TextInfo();
    }

    @Test
    public void testDefaultValues() {
        assertNull(textInfo.getText());
        assertFalse(textInfo.isStreamFinishFlag());
        assertFalse(textInfo.isVoiceBroadcast());
    }

    @Test
    public void testSetAndGetText() {
        textInfo.setText("Hello World");
        assertEquals("Hello World", textInfo.getText());
    }

    @Test
    public void testSetStreamFinishFlagTrue() {
        textInfo.setStreamFinishFlag(true);
        assertTrue(textInfo.isStreamFinishFlag());
    }

    @Test
    public void testSetStreamFinishFlagFalse() {
        textInfo.setStreamFinishFlag(true);
        textInfo.setStreamFinishFlag(false);
        assertFalse(textInfo.isStreamFinishFlag());
    }

    @Test
    public void testSetVoiceBroadcastTrue() {
        textInfo.setVoiceBroadcast(true);
        assertTrue(textInfo.isVoiceBroadcast());
    }

    @Test
    public void testSetVoiceBroadcastFalse() {
        textInfo.setVoiceBroadcast(true);
        textInfo.setVoiceBroadcast(false);
        assertFalse(textInfo.isVoiceBroadcast());
    }

    @Test
    public void testToStringContainsText() {
        textInfo.setText("Test content");
        String str = textInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("Test content"));
        assertTrue(str.contains("streamFinishFlag"));
        assertTrue(str.contains("isVoiceBroadcast"));
    }

    @Test
    public void testToStringWithNullText() {
        String str = textInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("text"));
    }

    @Test
    public void testToStringAllFields() {
        textInfo.setText("content");
        textInfo.setStreamFinishFlag(true);
        textInfo.setVoiceBroadcast(true);
        String str = textInfo.toString();
        assertTrue(str.contains("content"));
        assertTrue(str.contains("true"));
    }
}
