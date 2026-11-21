/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.session.passive;

import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.session.Session;
import com.huawei.aisfsdkv2.session.SessionManager;
import com.huawei.aisfsdkv2.session.SessionType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 被动会话管理器
 *
 * <p>管理被动会话的生命周期，定期清理长时间无任务的被动会话。</p>
 *
 * <h3>清理策略</h3>
 * <ul>
 *   <li>每1分钟检查一次所有被动会话</li>
 *   <li>如果会话连续5次检查都无任务，自动销毁</li>
 *   <li>如果有任务，重置计数</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <p>此管理器由SDK内部自动使用，业务层无需直接调用。</p>
 *
 * @since 2026-04-15
 */
public class PassiveSessionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("PassiveSessionManager");

    /** 检查间隔：1分钟（毫秒） */
    private static final long CHECK_INTERVAL_MS = 60 * 1000;

    /** 连续无任务检查次数阈值 */
    private static final int MAX_IDLE_CHECK_COUNT = 5;

    /** 单例Holder */
    private static class Holder {
        private static final PassiveSessionManager INSTANCE = new PassiveSessionManager();
    }

    /**
     * 获取单例实例
     *
     * @return PassiveSessionManager 单例
     */
    public static PassiveSessionManager getInstance() {
        return Holder.INSTANCE;
    }

    /** 会话ID -> 连续无任务检查次数 */
    private final Map<String, Integer> sessionIdleCount;

    private PassiveSessionManager() {
        this.sessionIdleCount = new ConcurrentHashMap<>();
        ThreadPoolManager.getInstance().addScheduleTaskSharedThreadPool(
            this::cleanupIdleSessions,
            CHECK_INTERVAL_MS,
            CHECK_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        LOGGER.info("PassiveSessionManager: cleanup task started, checkInterval={}min, maxIdleCheckCount={}", CHECK_INTERVAL_MS / 60000, MAX_IDLE_CHECK_COUNT);
    }

    /**
     * 注册被动会话
     *
     * @param sessionId 会话ID
     */
    public void registerSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        sessionIdleCount.put(sessionId, 0);
        LOGGER.debug("PassiveSessionManager.registerSession: registered, sessionId={}", sessionId);
    }

    /**
     * 清理空闲会话
     *
     * <p>遍历所有被动会话，检查是否需要清理。</p>
     */
    private void cleanupIdleSessions() {
        int checkedCount = 0;
        int cleanedCount = 0;
        for (Map.Entry<String, Integer> entry : sessionIdleCount.entrySet()) {
            String sessionId = entry.getKey();
            checkedCount++;
            Session session = SessionManager.getInstance().getSession(sessionId);
            if (session == null || session.getSessionType() != SessionType.PASSIVE) {
                sessionIdleCount.remove(sessionId);
                continue;
            }
            if (hasActiveTask(session)) {
                sessionIdleCount.put(sessionId, 0);
                continue;
            }
            int idleCount = entry.getValue() + 1;
            if (idleCount >= MAX_IDLE_CHECK_COUNT) {
                LOGGER.info("PassiveSessionManager.cleanupIdleSessions: cleaning up idle passive session, sessionId={}, idleCount={}", sessionId, idleCount);
                SessionManager.getInstance().destroySession(sessionId);
                sessionIdleCount.remove(sessionId);
                cleanedCount++;
            } else {
                sessionIdleCount.put(sessionId, idleCount);
            }
        }
        if (checkedCount > 0 || cleanedCount > 0) {
            LOGGER.debug("PassiveSessionManager.cleanupIdleSessions: checked={}, cleaned={}", checkedCount, cleanedCount);
        }
    }

    /**
     * 检查会话是否有活动任务
     *
     * @param session 会话对象
     * @return true 有活动任务
     */
    private boolean hasActiveTask(Session session) {
        if (!(session instanceof com.huawei.aisfsdkv2.session.impl.SessionImpl)) {
            return false;
        }
        com.huawei.aisfsdkv2.session.impl.SessionImpl sessionImpl = (com.huawei.aisfsdkv2.session.impl.SessionImpl) session;
        return sessionImpl.getTaskStackDepth() > 0;
    }
}
