/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.session;

import com.huawei.aisfsdkv2.api.session.SessionStatus;
import com.huawei.aisfsdkv2.session.impl.SessionImpl;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Session 接口测试
 */
public class SessionTest {
    @Test
    public void testSessionCreation() {
        Session session = new SessionImpl("test-123", null);
        assertEquals("test-123", session.getId());
        assertEquals(SessionStatus.ACTIVE, session.getStatus());
    }

    @Test
    public void testSetStatus() {
        Session session = new SessionImpl("test-456", null);
        assertTrue(session.setStatus(SessionStatus.PAUSED));
        assertEquals(SessionStatus.PAUSED, session.getStatus());
        assertTrue(session.setStatus(SessionStatus.ACTIVE));
        assertEquals(SessionStatus.ACTIVE, session.getStatus());
        assertTrue(session.setStatus(SessionStatus.DESTROYED));
        assertEquals(SessionStatus.DESTROYED, session.getStatus());
    }

    @Test
    public void testSetStatusToDestroyedBlocksFurtherChanges() {
        Session session = new SessionImpl("test-destroy", null);
        assertTrue(session.setStatus(SessionStatus.DESTROYED));
        assertEquals(SessionStatus.DESTROYED, session.getStatus());
        assertFalse(session.setStatus(SessionStatus.ACTIVE));
        assertFalse(session.setStatus(SessionStatus.PAUSED));
        assertEquals(SessionStatus.DESTROYED, session.getStatus());
    }

    @Test
    public void testSetNullStatus() {
        Session session = new SessionImpl("test-null", null);
        assertFalse(session.setStatus(null));
        assertEquals(SessionStatus.ACTIVE, session.getStatus());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullSessionId() {
        new SessionImpl(null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptySessionId() {
        new SessionImpl("", null);
    }
}
