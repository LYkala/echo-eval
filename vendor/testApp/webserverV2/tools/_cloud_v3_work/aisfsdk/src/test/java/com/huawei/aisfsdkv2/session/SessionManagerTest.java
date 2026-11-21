/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.session;

import com.huawei.aisfsdkv2.api.session.SessionStatus;
import com.huawei.aisfsdkv2.session.impl.SessionManagerImpl;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SessionManager 测试
 */
public class SessionManagerTest {
    @Test
    public void testSingletonInstance() {
        SessionManager manager1 = SessionManager.getInstance();
        SessionManager manager2 = SessionManager.getInstance();
        assertSame(manager1, manager2);
    }

    @Test
    public void testCreateSession() {
        SessionManager manager = SessionManager.getInstance();
        Session session = manager.createSession(null);
        assertNotNull(session);
        assertNotNull(session.getId());
        assertEquals(SessionStatus.ACTIVE, session.getStatus());
        assertNotNull(manager.getSession(session.getId()));
    }

    @Test
    public void testDestroySession() {
        SessionManager manager = SessionManager.getInstance();
        Session session = manager.createSession(null);
        String id = session.getId();
        assertTrue(manager.destroySession(id));
        assertNull(manager.getSession(id));
        assertFalse(manager.destroySession(id)); // Already destroyed
    }

    @Test
    public void testDestroyAllSessions() {
        SessionManager manager = SessionManager.getInstance();
        manager.destroyAllSessions();
        Session s1 = manager.createSession(null);
        Session s2 = manager.createSession(null);
        Session s3 = manager.createSession(null);
        assertNotNull(manager.getSession(s1.getId()));
        assertNotNull(manager.getSession(s2.getId()));
        assertNotNull(manager.getSession(s3.getId()));
        manager.destroyAllSessions();
        assertNull(manager.getSession(s1.getId()));
        assertNull(manager.getSession(s2.getId()));
        assertNull(manager.getSession(s3.getId()));
    }

    @Test
    public void testConcurrentAccess() throws InterruptedException {
        SessionManager manager = SessionManager.getInstance();
        manager.destroyAllSessions();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(100);
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                try {
                    Session s = manager.createSession(null);
                    // Session operations are handled internally by Session object
                    s.setStatus(SessionStatus.PAUSED);
                    s.setStatus(SessionStatus.ACTIVE);
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS));
        // Verify all sessions were created
        assertEquals(100, ((SessionManagerImpl) manager).getSessionCount());
        executor.shutdown();
    }

    // ==================== 边界条件测试 ====================

    /**
     * 测试销毁不存在的会话
     */
    @Test
    public void testDestroySession_NotExist() {
        SessionManager manager = SessionManager.getInstance();
        manager.destroyAllSessions();
        
        // 销毁不存在的会话应返回 false
        assertFalse("销毁不存在的会话应返回 false", 
            manager.destroySession("non-existent-id"));
    }

    /**
     * 测试获取不存在的会话
     */
    @Test
    public void testGetSession_NotExist() {
        SessionManager manager = SessionManager.getInstance();
        
        assertNull("获取不存在的会话应返回 null", 
            manager.getSession("non-existent-id"));
    }

    /**
     * 测试重复销毁同一会话
     */
    @Test
    public void testDestroySession_Duplicate() {
        SessionManager manager = SessionManager.getInstance();
        Session session = manager.createSession(null);
        String id = session.getId();
        
        // 第一次销毁应成功
        assertTrue("第一次销毁应成功", manager.destroySession(id));
        // 第二次销毁应失败（已不存在）
        assertFalse("第二次销毁应失败", manager.destroySession(id));
        // 第三次销毁仍应失败
        assertFalse("第三次销毁仍应失败", manager.destroySession(id));
    }

    /**
     * 测试创建多个会话
     */
    @Test
    public void testCreateMultipleSessions() {
        SessionManager manager = SessionManager.getInstance();
        manager.destroyAllSessions();
        
        Session s1 = manager.createSession(null);
        Session s2 = manager.createSession(null);
        Session s3 = manager.createSession(null);
        
        // 验证所有会话都有唯一的ID
        assertNotNull(s1.getId());
        assertNotNull(s2.getId());
        assertNotNull(s3.getId());
        
        assertFalse("会话ID应唯一", s1.getId().equals(s2.getId()));
        assertFalse("会话ID应唯一", s2.getId().equals(s3.getId()));
        assertFalse("会话ID应唯一", s1.getId().equals(s3.getId()));
        
        // 验证都能正确获取
        assertSame(s1, manager.getSession(s1.getId()));
        assertSame(s2, manager.getSession(s2.getId()));
        assertSame(s3, manager.getSession(s3.getId()));
    }

    /**
     * 测试会话ID格式
     */
    @Test
    public void testSessionIdFormat() {
        SessionManager manager = SessionManager.getInstance();
        Session session = manager.createSession(null);
        String id = session.getId();
        
        assertNotNull("会话ID不应为null", id);
        assertFalse("会话ID不应为空字符串", id.isEmpty());
        assertTrue("会话ID长度应大于0", id.length() > 0);
    }

    /**
     * 测试创建和销毁会话计数
     */
    @Test
    public void testSessionCount() {
        SessionManager manager = SessionManager.getInstance();
        manager.destroyAllSessions();
        
        assertEquals("初始计数应为0", 0, ((SessionManagerImpl) manager).getSessionCount());
        
        Session s1 = manager.createSession(null);
        assertEquals("创建1个会话后计数应为1", 1, ((SessionManagerImpl) manager).getSessionCount());
        
        Session s2 = manager.createSession(null);
        assertEquals("创建2个会话后计数应为2", 2, ((SessionManagerImpl) manager).getSessionCount());
        
        manager.destroySession(s1.getId());
        assertEquals("销毁1个会话后计数应为1", 1, ((SessionManagerImpl) manager).getSessionCount());
        
        manager.destroySession(s2.getId());
        assertEquals("销毁所有会话后计数应为0", 0, ((SessionManagerImpl) manager).getSessionCount());
    }

    /**
     * 测试destroyAllSessions的幂等性
     */
    @Test
    public void testDestroyAllSessions_Idempotent() {
        SessionManager manager = SessionManager.getInstance();
        
        // 第一次调用
        manager.destroyAllSessions();
        assertEquals("第一次destroyAllSessions后计数应为0", 
            0, ((SessionManagerImpl) manager).getSessionCount());
        
        // 第二次调用（无会话时）
        manager.destroyAllSessions();
        assertEquals("第二次destroyAllSessions后计数仍应为0", 
            0, ((SessionManagerImpl) manager).getSessionCount());
        
        // 第三次调用
        manager.destroyAllSessions();
        assertEquals("第三次destroyAllSessions后计数仍应为0", 
            0, ((SessionManagerImpl) manager).getSessionCount());
    }

    /**
     * 测试会话状态流转
     */
    @Test
    public void testSessionStatusTransition() {
        SessionManager manager = SessionManager.getInstance();
        manager.destroyAllSessions();
        
        Session session = manager.createSession(null);
        
        // 创建后状态应为 ACTIVE
        assertEquals("创建后状态应为 ACTIVE", SessionStatus.ACTIVE, session.getStatus());
        
        // 修改为 PAUSED
        session.setStatus(SessionStatus.PAUSED);
        assertEquals("修改后状态应为 PAUSED", SessionStatus.PAUSED, session.getStatus());
        
        // 修改回 ACTIVE
        session.setStatus(SessionStatus.ACTIVE);
        assertEquals("再次修改后状态应为 ACTIVE", SessionStatus.ACTIVE, session.getStatus());
        
        // 修改为 DESTROYED
        session.setStatus(SessionStatus.DESTROYED);
        assertEquals("修改为 DESTROYED 后状态应为 DESTROYED", SessionStatus.DESTROYED, session.getStatus());
    }
}
