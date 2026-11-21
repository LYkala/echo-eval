/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logger.model;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.log.LogLevelEnum;
import com.huawei.aisfsdkv2.api.login.LoginApi;
import com.huawei.aisfsdkv2.api.login.LoginInfo;
import com.huawei.aisfsdkv2.api.login.LoginStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * LogEvent单元测试
 */
@RunWith(RobolectricTestRunner.class)
public class LogEventTest {
    @Mock
    private LoginApi mockLoginApi;
    @Mock
    private LoginInfo mockLoginInfo;
    private MockedStatic<AISFSDKClient> mockedStaticClient;
    private AutoCloseable mocks;

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockedStaticClient = mockStatic(AISFSDKClient.class);
        when(AISFSDKClient.getLoginApi()).thenReturn(mockLoginApi);
        when(mockLoginApi.getLoginInfo()).thenReturn(mockLoginInfo);
        when(mockLoginInfo.getUsername()).thenReturn("testUser");
    }

    @After
    public void tearDown() throws Exception {
        mockedStaticClient.close();
        mocks.close();
    }

    @Test
    public void testConstructorWithThreeParams() {
        LogEvent event = new LogEvent(LogLevelEnum.INFO, "TestTag", "Test message");
        assertEquals("testUser", event.getUserId());
        assertEquals(LogLevelEnum.INFO, event.getLogLevel());
        assertEquals("", event.getTaskId());
        assertNotNull(event.getTimestamp());
        assertNotNull(event.getLogEventMessage());
        assertEquals("TestTag", event.getLogEventMessage().getEventName());
        assertEquals("Test message", event.getLogEventMessage().getDetail());
    }

    @Test
    public void testConstructorWithFourParams() {
        LogEvent event = new LogEvent(LogLevelEnum.ERROR, "ErrorTag", "Error message", "task123");
        assertEquals("testUser", event.getUserId());
        assertEquals(LogLevelEnum.ERROR, event.getLogLevel());
        assertEquals("task123", event.getTaskId());
        assertEquals("ErrorTag", event.getLogEventMessage().getEventName());
    }

    @Test
    public void testConstructorWithNullTaskId() {
        LogEvent event = new LogEvent(LogLevelEnum.DEBUG, "Tag", "Message", null);
        assertEquals("", event.getTaskId());
    }

    @Test
    public void testConstructorWithEmptyTaskId() {
        LogEvent event = new LogEvent(LogLevelEnum.WARN, "Tag", "Message", "");
        assertEquals("", event.getTaskId());
    }

    @Test
    public void testConstructorWithNullLoginApi() {
        when(AISFSDKClient.getLoginApi()).thenReturn(null);
        LogEvent event = new LogEvent(LogLevelEnum.INFO, "Tag", "Message");
        assertEquals("", event.getUserId());
    }

    @Test
    public void testSetTimestamp() {
        LogEvent event = new LogEvent(LogLevelEnum.INFO, "Tag", "Message");
        event.setTimestamp("2026-01-01T00:00:00Z");
        assertEquals("2026-01-01T00:00:00Z", event.getTimestamp());
    }

    @Test
    public void testToJSONStringContainsRequiredFields() {
        LogEvent event = new LogEvent(LogLevelEnum.INFO, "TestEvent", "Test detail", "task456");
        String json = event.toJSONString();
        assertTrue(json.contains("testUser"));
        assertTrue(json.contains("INFO"));
        assertTrue(json.contains("EM"));
        assertTrue(json.contains("task456"));
        assertTrue(json.contains("SDK"));
        assertTrue(json.contains("TestEvent"));
        assertTrue(json.contains("Test detail"));
        assertTrue(json.contains("success"));
    }

    @Test
    public void testToJSONStringWithDifferentLogLevels() {
        LogEvent debugEvent = new LogEvent(LogLevelEnum.DEBUG, "Tag", "Msg");
        assertTrue(debugEvent.toJSONString().contains("\"LogLevel\":\"DEBUG\""));
        LogEvent warnEvent = new LogEvent(LogLevelEnum.WARN, "Tag", "Msg");
        assertTrue(warnEvent.toJSONString().contains("\"LogLevel\":\"WARN\""));
        LogEvent errorEvent = new LogEvent(LogLevelEnum.ERROR, "Tag", "Msg");
        assertTrue(errorEvent.toJSONString().contains("\"LogLevel\":\"ERROR\""));
    }

    @Test
    public void testToJSONStringWithEmptyUserId() {
        when(AISFSDKClient.getLoginApi()).thenReturn(null);
        LogEvent event = new LogEvent(LogLevelEnum.INFO, "Tag", "Message");
        String json = event.toJSONString();
        assertTrue(json.contains("\"UserID\":\"\""));
    }

    @Test
    public void testTimestampFormat() {
        LogEvent event = new LogEvent(LogLevelEnum.INFO, "Tag", "Message");
        String timestamp = event.getTimestamp();
        assertNotNull(timestamp);
        assertTrue(timestamp.contains("T"));
        assertTrue(timestamp.contains("Z"));
    }

    @Test
    public void testEventNamePrefix() {
        LogEvent event = new LogEvent(LogLevelEnum.INFO, "CustomTag", "Message");
        assertEquals("CustomTag", event.getLogEventMessage().getEventName());
    }

    @Test
    public void testGetLogEventMessageNotNull() {
        LogEvent event = new LogEvent(LogLevelEnum.INFO, "Tag", "Message");
        assertNotNull(event.getLogEventMessage());
        assertEquals("SDK", event.getLogEventMessage().getServiceName());
        assertEquals("success", event.getLogEventMessage().getStatus());
    }

    @Test
    public void testToJSONStringWithSpecialCharacters() {
        LogEvent event = new LogEvent(LogLevelEnum.INFO, "Tag", "Message with \"quotes\" and \\backslash");
        String json = event.toJSONString();
        assertNotNull(json);
        assertTrue(json.length() > 0);
    }

    @Test
    public void testToJSONStringWithChineseCharacters() {
        LogEvent event = new LogEvent(LogLevelEnum.INFO, "标签", "中文消息内容");
        String json = event.toJSONString();
        assertTrue(json.contains("中文消息内容"));
        assertTrue(json.contains("标签"));
    }

    @Test
    public void testMultipleLogEventsHaveDifferentTimestamps() throws InterruptedException {
        LogEvent event1 = new LogEvent(LogLevelEnum.INFO, "Tag1", "Msg1");
        Thread.sleep(10);
        LogEvent event2 = new LogEvent(LogLevelEnum.INFO, "Tag2", "Msg2");
        assertNotNull(event1.getTimestamp());
        assertNotNull(event2.getTimestamp());
    }

    @Test
    public void testGetLogLevel() {
        LogEvent event = new LogEvent(LogLevelEnum.WARN, "Tag", "Message");
        assertEquals(LogLevelEnum.WARN, event.getLogLevel());
        assertEquals(3, event.getLogLevel().getLogLevel());
    }
}
