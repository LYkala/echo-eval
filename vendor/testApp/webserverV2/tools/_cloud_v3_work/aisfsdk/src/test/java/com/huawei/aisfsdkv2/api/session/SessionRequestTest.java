/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * SessionRequest 测试
 */
public class SessionRequestTest {

    @Test
    public void testBuilderDefaults() {
        SessionRequest request = SessionRequest.newBuilder().build();
        assertNull(request.getUserInputText());
        assertEquals(-1, request.getUserInputObjectCode());
        assertNull(request.getUserInputObjectData());
        assertNull(request.getUserInputObjectRawString());
        assertNotNull(request.getUserIntent());
        assertTrue(request.getUserIntent().isEmpty());
        assertNull(request.getUserSpecifiedAgent());
        assertNull(request.getVoiceId());
        assertNotNull(request.getParams());
        assertTrue(request.getParams().isEmpty());
    }

    @Test
    public void testBuilderUserInputText() {
        SessionRequest request = SessionRequest.newBuilder()
                .userInputText("hello")
                .build();
        assertEquals("hello", request.getUserInputText());
    }

    @Test
    public void testBuilderUserInputObjectCode() {
        SessionRequest request = SessionRequest.newBuilder()
                .userInputObjectCode(42)
                .build();
        assertEquals(42, request.getUserInputObjectCode());
    }

    @Test
    public void testBuilderUserInputObjectData() {
        SessionRequest request = SessionRequest.newBuilder()
                .userInputObjectData("data-payload")
                .build();
        assertEquals("data-payload", request.getUserInputObjectData());
    }

    @Test
    public void testBuilderUserInputObjectRawString() {
        SessionRequest request = SessionRequest.newBuilder()
                .userInputObjectRawString("{\"raw\":true}")
                .build();
        assertEquals("{\"raw\":true}", request.getUserInputObjectRawString());
    }

    @Test
    public void testBuilderUserIntent() {
        SessionRequest request = SessionRequest.newBuilder()
                .userIntent("greeting")
                .build();
        assertEquals(1, request.getUserIntent().size());
        assertEquals("greeting", request.getUserIntent().get(0));
    }

    @Test
    public void testBuilderUserIntentList() {
        List<String> intents = new ArrayList<>();
        intents.add("intent1");
        intents.add("intent2");
        SessionRequest request = SessionRequest.newBuilder()
                .userIntent(intents)
                .build();
        assertEquals(2, request.getUserIntent().size());
        assertEquals("intent1", request.getUserIntent().get(0));
        assertEquals("intent2", request.getUserIntent().get(1));
    }

    @Test
    public void testBuilderUserSpecifiedAgent() {
        SessionRequest request = SessionRequest.newBuilder()
                .userSpecifiedAgent("agent-001")
                .build();
        assertEquals("agent-001", request.getUserSpecifiedAgent());
    }

    @Test
    public void testBuilderVoiceId() {
        SessionRequest request = SessionRequest.newBuilder()
                .voiceId("voice-123")
                .build();
        assertEquals("voice-123", request.getVoiceId());
    }

    @Test
    public void testBuilderParams() {
        Map<String, String> params = new HashMap<>();
        params.put("key1", "value1");
        params.put("key2", "value2");
        SessionRequest request = SessionRequest.newBuilder()
                .params(params)
                .build();
        assertEquals(2, request.getParams().size());
        assertEquals("value1", request.getParams().get("key1"));
        assertEquals("value2", request.getParams().get("key2"));
    }

    @Test
    public void testBuilderParamsNullDefaultsToEmpty() {
        SessionRequest request = SessionRequest.newBuilder()
                .params(null)
                .build();
        assertNotNull(request.getParams());
        assertTrue(request.getParams().isEmpty());
    }

    @Test
    public void testBuilderAllFields() {
        Map<String, String> params = new HashMap<>();
        params.put("p1", "v1");
        SessionRequest request = SessionRequest.newBuilder()
                .userInputText("text")
                .userInputObjectCode(10)
                .userInputObjectData("obj-data")
                .userInputObjectRawString("raw-string")
                .userIntent("intent")
                .userSpecifiedAgent("agent")
                .voiceId("voice")
                .params(params)
                .build();
        assertEquals("text", request.getUserInputText());
        assertEquals(10, request.getUserInputObjectCode());
        assertEquals("obj-data", request.getUserInputObjectData());
        assertEquals("raw-string", request.getUserInputObjectRawString());
        assertEquals(1, request.getUserIntent().size());
        assertEquals("intent", request.getUserIntent().get(0));
        assertEquals("agent", request.getUserSpecifiedAgent());
        assertEquals("voice", request.getVoiceId());
        assertEquals(1, request.getParams().size());
        assertEquals("v1", request.getParams().get("p1"));
    }

    @Test
    public void testNewBuilderReturnsNewInstance() {
        SessionRequest.Builder builder1 = SessionRequest.newBuilder();
        SessionRequest.Builder builder2 = SessionRequest.newBuilder();
        assertNotNull(builder1);
        assertNotNull(builder2);
    }

    @Test
    public void testBuilderChaining() {
        SessionRequest request = SessionRequest.newBuilder()
                .userInputText("text")
                .userIntent("intent")
                .voiceId("voice")
                .build();
        assertEquals("text", request.getUserInputText());
        assertEquals(1, request.getUserIntent().size());
        assertEquals("intent", request.getUserIntent().get(0));
        assertEquals("voice", request.getVoiceId());
    }

    @Test
    public void testEmptyParams() {
        SessionRequest request = SessionRequest.newBuilder()
                .params(Collections.emptyMap())
                .build();
        assertNotNull(request.getParams());
        assertTrue(request.getParams().isEmpty());
    }
}
