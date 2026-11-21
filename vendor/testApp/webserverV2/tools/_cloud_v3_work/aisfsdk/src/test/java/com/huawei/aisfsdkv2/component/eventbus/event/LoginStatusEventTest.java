/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.eventbus.event;

import com.huawei.aisfsdkv2.api.login.LoginStatus;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * LoginStatusEvent 单元测试
 */
public class LoginStatusEventTest {

    @Test
    public void testGetEventType() {
        LoginStatusEvent event = new LoginStatusEvent(LoginStatus.CONNECTED);
        assertEquals(LoginStatusEvent.EVENT_TYPE, event.getEventType());
        assertEquals("LOGIN_STATUS", event.getEventType());
    }

    @Test
    public void testGetStatus() {
        LoginStatusEvent event = new LoginStatusEvent(LoginStatus.AUTHING);
        assertEquals(LoginStatus.AUTHING, event.getStatus());
    }

    @Test
    public void testIsLoggedIn_WhenConnected_ReturnsTrue() {
        LoginStatusEvent event = new LoginStatusEvent(LoginStatus.CONNECTED);
        assertTrue(event.isLoggedIn());
    }

    @Test
    public void testIsLoggedIn_WhenNotConnected_ReturnsFalse() {
        LoginStatusEvent event = new LoginStatusEvent(LoginStatus.IDLE);
        assertFalse(event.isLoggedIn());
        event = new LoginStatusEvent(LoginStatus.AUTHING);
        assertFalse(event.isLoggedIn());
        event = new LoginStatusEvent(LoginStatus.CONNECTING);
        assertFalse(event.isLoggedIn());
        event = new LoginStatusEvent(LoginStatus.DISCONNECTED);
        assertFalse(event.isLoggedIn());
    }

    @Test
    public void testIsLoggedOut_WhenIdle_ReturnsTrue() {
        LoginStatusEvent event = new LoginStatusEvent(LoginStatus.IDLE);
        assertTrue(event.isLoggedOut());
    }

    @Test
    public void testIsLoggedOut_WhenNotIdle_ReturnsFalse() {
        LoginStatusEvent event = new LoginStatusEvent(LoginStatus.CONNECTED);
        assertFalse(event.isLoggedOut());
        event = new LoginStatusEvent(LoginStatus.AUTHING);
        assertFalse(event.isLoggedOut());
        event = new LoginStatusEvent(LoginStatus.CONNECTING);
        assertFalse(event.isLoggedOut());
        event = new LoginStatusEvent(LoginStatus.DISCONNECTED);
        assertFalse(event.isLoggedOut());
    }

    @Test
    public void testEventImplementsEvent() {
        LoginStatusEvent event = new LoginStatusEvent(LoginStatus.CONNECTED);
        assertTrue(event instanceof com.huawei.aisfsdkv2.component.eventbus.Event);
    }

    @Test
    public void testAllLoginStatuses() {
        for (LoginStatus status : LoginStatus.values()) {
            LoginStatusEvent event = new LoginStatusEvent(status);
            assertEquals(status, event.getStatus());
            assertEquals(status == LoginStatus.CONNECTED, event.isLoggedIn());
            assertEquals(status == LoginStatus.IDLE, event.isLoggedOut());
        }
    }
}
