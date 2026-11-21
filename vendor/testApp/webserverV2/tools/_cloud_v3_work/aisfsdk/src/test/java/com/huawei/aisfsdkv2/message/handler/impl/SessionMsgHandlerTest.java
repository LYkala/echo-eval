/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler.impl;

import com.huawei.aisfsdkv2.session.Session;
import com.huawei.aisfsdkv2.session.SessionManager;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SessionMsgHandler 测试
 *
 * <p>使用 Robolectric 让 org.json 真实运行，mockStatic SessionManager 打桩外部依赖。</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SessionMsgHandlerTest {

    @Mock
    private SessionManager mockSessionManager;

    private MockedStatic<SessionManager> mockedSessionManager;
    private AutoCloseable mocks;
    private SessionMsgHandler handler;

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockedSessionManager = mockStatic(SessionManager.class);
        mockedSessionManager.when(SessionManager::getInstance).thenReturn(mockSessionManager);
        handler = new SessionMsgHandler();
    }

    @After
    public void tearDown() throws Exception {
        mockedSessionManager.close();
        mocks.close();
    }

    @Test
    public void testGetName() {
        assertEquals("SessionMsgHandler", handler.getName());
    }

    @Test
    public void testGetPriority() {
        assertEquals(20, handler.getPriority());
    }

    @Test
    public void testHandleReceived_NoHeader_ReturnsFalse() throws Exception {
        JSONObject json = new JSONObject();
        assertFalse(handler.handleReceived(json));
    }

    @Test
    public void testHandleReceived_WrongHeaderMsgType_ReturnsFalse() throws Exception {
        JSONObject json = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("MessageType", "OtherType");
        json.put("Header", header);
        assertFalse(handler.handleReceived(json));
    }

    @Test
    public void testHandleReceived_NoData_ReturnsFalse() throws Exception {
        JSONObject json = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("MessageType", "DataExchange");
        json.put("Header", header);
        assertFalse(handler.handleReceived(json));
    }

    @Test
    public void testHandleReceived_WrongDataMsgType_ReturnsFalse() throws Exception {
        JSONObject json = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("MessageType", "DataExchange");
        JSONObject data = new JSONObject();
        data.put("MessageType", "OtherType");
        json.put("Header", header);
        json.put("Data", data);
        assertFalse(handler.handleReceived(json));
    }

    @Test
    public void testHandleReceived_MatchingMessage_SessionFound_ReturnsTrue() throws Exception {
        Session mockSession = mock(Session.class);
        when(mockSessionManager.getSession("session-abc")).thenReturn(mockSession);
        JSONObject json = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("MessageType", "DataExchange");
        header.put("ClientSessionId", "session-abc");
        JSONObject data = new JSONObject();
        data.put("MessageType", "UserOutput");
        json.put("Header", header);
        json.put("Data", data);
        assertTrue(handler.handleReceived(json));
        verify(mockSession).receive(json);
    }

    @Test
    public void testHandleReceived_MatchingMessage_SessionNotFound_ReturnsTrue() throws Exception {
        when(mockSessionManager.getSession("no-such-session")).thenReturn(null);
        JSONObject json = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("MessageType", "DataExchange");
        header.put("ClientSessionId", "no-such-session");
        JSONObject data = new JSONObject();
        data.put("MessageType", "UserOutput");
        json.put("Header", header);
        json.put("Data", data);
        // session 不存在也返回 true（消息已被消费）
        assertTrue(handler.handleReceived(json));
    }

    @Test
    public void testHandleReceived_MatchingMessage_EmptyClientSessionId_ReturnsTrue() throws Exception {
        JSONObject json = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("MessageType", "DataExchange");
        header.put("ClientSessionId", "");
        JSONObject data = new JSONObject();
        data.put("MessageType", "UserOutput");
        json.put("Header", header);
        json.put("Data", data);
        // clientSessionId 为空走 warn 分支，仍返回 true
        assertTrue(handler.handleReceived(json));
    }

    @Test
    public void testHandleReceived_MatchingMessage_MissingClientSessionId_ReturnsTrue() throws Exception {
        JSONObject json = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("MessageType", "DataExchange");
        JSONObject data = new JSONObject();
        data.put("MessageType", "UserOutput");
        json.put("Header", header);
        json.put("Data", data);
        // 缺少 ClientSessionId，optString 返回空字符串，走 warn 分支
        assertTrue(handler.handleReceived(json));
    }
}
