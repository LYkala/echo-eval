/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.session.impl;

import com.huawei.aisfsdkv2.api.session.SessionStatus;
import com.huawei.aisfsdkv2.session.Session;
import com.huawei.aisfsdkv2.session.SessionManager;
import com.huawei.aisfsdkv2.session.SessionType;

import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * SessionManagerImpl 单元测试
 */
public class SessionManagerImplTest {

    private SessionManagerImpl manager;

    @Before
    public void setUp() {
        manager = SessionManagerImpl.getInstance();
        manager.destroyAllSessions();
    }

    // ======= 单例 =======

    @Test
    public void testSingleton() {
        SessionManagerImpl m1 = SessionManagerImpl.getInstance();
        SessionManagerImpl m2 = SessionManagerImpl.getInstance();
        assertSame(m1, m2);
    }

    @Test
    public void testGetInstance_SameAsSessionManager() {
        assertSame(SessionManager.getInstance(), SessionManagerImpl.getInstance());
    }

    // ======= createSession =======

    @Test
    public void testCreateSession_NotNull() {
        Session session = manager.createSession(null);
        assertNotNull(session);
    }

    @Test
    public void testCreateSession_HasId() {
        Session session = manager.createSession(null);
        assertNotNull(session.getId());
        assertFalse(session.getId().isEmpty());
    }

    @Test
    public void testCreateSession_TypeIsActive() {
        Session session = manager.createSession(null);
        assertEquals(SessionType.ACTIVE, session.getSessionType());
    }

    @Test
    public void testCreateSession_StatusIsActive() {
        Session session = manager.createSession(null);
        assertEquals(SessionStatus.ACTIVE, session.getStatus());
    }

    @Test
    public void testCreateSession_StoredInManager() {
        Session session = manager.createSession(null);
        assertSame(session, manager.getSession(session.getId()));
    }

    @Test
    public void testCreateSession_UniqueIds() {
        Session s1 = manager.createSession(null);
        Session s2 = manager.createSession(null);
        assertFalse(s1.getId().equals(s2.getId()));
    }

    // ======= destroySession =======

    @Test
    public void testDestroySession_Success() {
        Session session = manager.createSession(null);
        assertTrue(manager.destroySession(session.getId()));
    }

    @Test
    public void testDestroySession_RemovedFromManager() {
        Session session = manager.createSession(null);
        String id = session.getId();
        manager.destroySession(id);
        assertNull(manager.getSession(id));
    }

    @Test
    public void testDestroySession_StatusSetToDestroyed() {
        Session session = manager.createSession(null);
        manager.destroySession(session.getId());
        assertEquals(SessionStatus.DESTROYED, session.getStatus());
    }

    @Test
    public void testDestroySession_NotExist_ReturnsFalse() {
        assertFalse(manager.destroySession("non-existent"));
    }

    @Test
    public void testDestroySession_TwiceSameId_SecondReturnsFalse() {
        Session session = manager.createSession(null);
        String id = session.getId();
        assertTrue(manager.destroySession(id));
        assertFalse(manager.destroySession(id));
    }

    // ======= destroyAllSessions =======

    @Test
    public void testDestroyAllSessions_ClearsAll() {
        manager.createSession(null);
        manager.createSession(null);
        manager.createSession(null);
        manager.destroyAllSessions();
        assertEquals(0, manager.getSessionCount());
    }

    @Test
    public void testDestroyAllSessions_EmptyManager_NoError() {
        manager.destroyAllSessions();
        assertEquals(0, manager.getSessionCount());
    }

    // ======= getSession =======

    @Test
    public void testGetSession_Exists() {
        Session session = manager.createSession(null);
        assertSame(session, manager.getSession(session.getId()));
    }

    @Test
    public void testGetSession_NotExist_ReturnsNull() {
        assertNull(manager.getSession("non-existent"));
    }

    // ======= listSessions =======

    @Test
    public void testListSessions_Empty() {
        Collection<Session> sessions = manager.listSessions();
        assertTrue(sessions.isEmpty());
    }

    @Test
    public void testListSessions_ContainsCreated() {
        Session s1 = manager.createSession(null);
        Session s2 = manager.createSession(null);
        Collection<Session> sessions = manager.listSessions();
        assertEquals(2, sessions.size());
        assertTrue(sessions.contains(s1));
        assertTrue(sessions.contains(s2));
    }

    @Test
    public void testListSessions_ReturnsCopy() {
        manager.createSession(null);
        Collection<Session> sessions = manager.listSessions();
        sessions.clear();
        assertEquals(1, manager.getSessionCount());
    }

    // ======= getSessionCount =======

    @Test
    public void testGetSessionCount_InitiallyZero() {
        assertEquals(0, manager.getSessionCount());
    }

    @Test
    public void testGetSessionCount_AfterCreate() {
        manager.createSession(null);
        manager.createSession(null);
        assertEquals(2, manager.getSessionCount());
    }

    @Test
    public void testGetSessionCount_AfterDestroy() {
        Session session = manager.createSession(null);
        manager.createSession(null);
        manager.destroySession(session.getId());
        assertEquals(1, manager.getSessionCount());
    }
}
