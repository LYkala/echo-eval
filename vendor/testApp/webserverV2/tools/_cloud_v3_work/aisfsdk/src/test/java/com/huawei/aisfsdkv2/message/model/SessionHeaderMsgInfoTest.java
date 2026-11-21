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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * SessionHeaderMsgInfo 单元测试
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SessionHeaderMsgInfoTest {

    @Test
    public void testGetSetSeqNum() {
        SessionHeaderMsgInfo info = new SessionHeaderMsgInfo();
        info.setSeqNum("seq-001");
        assertEquals("seq-001", info.getSeqNum());
    }

    @Test
    public void testGetSetTeamEnvironmentId() {
        SessionHeaderMsgInfo info = new SessionHeaderMsgInfo();
        info.setTeamEnvironmentId("env-test");
        assertEquals("env-test", info.getTeamEnvironmentId());
    }

    @Test
    public void testDefaultValuesAreNull() {
        SessionHeaderMsgInfo info = new SessionHeaderMsgInfo();
        assertNull(info.getSeqNum());
        assertNull(info.getTeamEnvironmentId());
    }

    @Test
    public void testToJson_containsSeqNumAndTeamEnvironmentId() throws JSONException {
        SessionHeaderMsgInfo info = new SessionHeaderMsgInfo();
        info.setSeqNum("seq-abc");
        info.setTeamEnvironmentId("team-xyz");
        JSONObject json = info.toJson();
        // optString 在 Android stub 环境下也能正常工作
        assertEquals("seq-abc", json.optString("SeqNum"));
        assertEquals("team-xyz", json.optString("TeamEnvironmentID"));
    }

    @Test
    public void testToJson_nullFieldsDefaultToEmpty() throws JSONException {
        SessionHeaderMsgInfo info = new SessionHeaderMsgInfo();
        JSONObject json = info.toJson();
        assertEquals("", json.optString("SeqNum"));
        assertEquals("", json.optString("TeamEnvironmentID"));
    }

    @Test
    public void testToJson_inheritsParentFields() throws JSONException {
        SessionHeaderMsgInfo info = new SessionHeaderMsgInfo();
        info.setMessageType("UserTaskManagement");
        info.setSender("device");
        info.setReceiver("SystemAgent");
        JSONObject json = info.toJson();
        assertEquals("UserTaskManagement", json.optString("MessageType"));
        assertEquals("device", json.optString("Sender"));
        assertEquals("SystemAgent", json.optString("Receiver"));
    }

    @Test
    public void testToJson_notNull() throws JSONException {
        SessionHeaderMsgInfo info = new SessionHeaderMsgInfo();
        assertNotNull(info.toJson());
    }
}
