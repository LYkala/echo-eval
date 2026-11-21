/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * SessionData 单元测试
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SessionDataTest {

    @Test
    public void testGetSetMessageType() {
        SessionData data = new SessionData();
        data.setMessageType("TaskAssignment");
        assertEquals("TaskAssignment", data.getMessageType());
    }

    @Test
    public void testGetSetUserInputText() {
        SessionData data = new SessionData();
        data.setUserInputText("hello");
        assertEquals("hello", data.getUserInputText());
    }

    @Test
    public void testGetSetUserSpecifiedAgent() {
        SessionData data = new SessionData();
        data.setUserSpecifiedAgent("agent-01");
        assertEquals("agent-01", data.getUserSpecifiedAgent());
    }

    @Test
    public void testGetSetUserInputObject() {
        SessionData data = new SessionData();
        data.setUserInputObject("{\"key\":\"value\"}");
        assertEquals("{\"key\":\"value\"}", data.getUserInputObject());
    }

    @Test
    public void testGetSetUserIntent() {
        SessionData data = new SessionData();
        List<String> intents = new ArrayList<>();
        intents.add("intent-test");
        data.setUserIntent(intents);
        assertEquals(1, data.getUserIntent().size());
        assertEquals("intent-test", data.getUserIntent().get(0));
    }

    @Test
    public void testGetSetVoiceId() {
        SessionData data = new SessionData();
        data.setVoiceId("voice-123");
        assertEquals("voice-123", data.getVoiceId());
    }

    @Test
    public void testDefaultValuesAreNull() {
        SessionData data = new SessionData();
        assertNull(data.getMessageType());
        assertNull(data.getUserInputText());
        assertNull(data.getUserSpecifiedAgent());
        assertNull(data.getUserInputObject());
        assertNull(data.getUserIntent());
        assertNull(data.getVoiceId());
    }

    @Test
    public void testToJson_messageTypeNullDefaultsToEmpty() throws JSONException {
        SessionData data = new SessionData();
        JSONObject json = data.toJson();
        assertEquals("", json.optString("MessageType"));
    }

    @Test
    public void testToJson_withAllFields() throws JSONException {
        SessionData data = new SessionData();
        data.setMessageType("TaskAssignment");
        data.setUserInputText("hello world");
        data.setUserSpecifiedAgent("agent-01");
        List<String> intents = new ArrayList<>();
        intents.add("query");
        data.setUserIntent(intents);
        data.setUserInputObject("{\"code\":210001}");
        JSONObject json = data.toJson();
        assertEquals("TaskAssignment", json.optString("MessageType"));
        assertEquals("hello world", json.optString("UserInputText"));
        assertEquals("agent-01", json.optString("UserSpecifiedAgent"));
        assertEquals("query", json.optString("UserIntent"));
        assertEquals("{\"code\":210001}", json.optString("UserInputObject"));
    }

    @Test
    public void testToJson_emptyOptionalFieldsNotIncluded() throws JSONException {
        SessionData data = new SessionData();
        data.setMessageType("TaskAssignment");
        JSONObject json = data.toJson();
        assertFalse(json.has("UserInputText"));
        assertFalse(json.has("UserSpecifiedAgent"));
        assertFalse(json.has("UserIntent"));
        assertFalse(json.has("UserInputObject"));
    }

    @Test
    public void testToJson_notNull() throws JSONException {
        SessionData data = new SessionData();
        assertNotNull(data.toJson());
    }
}
