/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.session.aging;

import com.huawei.aisfsdkv2.api.session.SessionAgingConfig;
import com.huawei.aisfsdkv2.api.session.SessionStatus;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.session.Session;
import com.huawei.aisfsdkv2.session.SessionManager;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * 会话老化管理器
 *
 * <p>根据 {@link SessionAgingConfig} 配置，定时检查所有会话的空闲时间，
 * 超过最大空闲时间的会话将被自动销毁。</p>
 *
 * <h3>老化判定规则</h3>
 * <ul>
 *   <li>空闲时间 = 当前时间 - 最后一次消息收发时间（{@link Session#getLastActiveTime()}）</li>
 *   <li>maxIdleTimeMinutes = -1 表示永不老化，检查时跳过</li>
 *   <li>未设置老化配置的会话不参与检查</li>
 *   <li>已销毁的会话不参与检查</li>
 *   <li>有任务执行中的会话不销毁，等待下次检查</li>
 * </ul>
 *
 * <h3>检查机制</h3>
 * <p>每1分钟执行一次检查任务，由 {@link com.huawei.aisfsdkv2.session.impl.SessionManagerImpl} 初始化时显式启动。</p>
 *
 * @since 2026-04-22
 */
public class SessionAgingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("SessionAgingManager");

    /** 检查间隔：1分钟（毫秒） */
    private static final long CHECK_INTERVAL_MS = 60 * 1000;

    /** 单例Holder */
    private static class Holder {
        private static final SessionAgingManager INSTANCE = new SessionAgingManager();
    }

    /**
     * 获取单例实例
     *
     * @return SessionAgingManager 单例
     */
    public static SessionAgingManager getInstance() {
        return Holder.INSTANCE;
    }

    /** 是否已启动检查任务 */
    private volatile boolean started = false;

    /** 检查轮次计数器，用于心跳日志 */
    private int checkRound = 0;

    /** 心跳日志间隔：每5次检查（5分钟）打印一次 */
    private static final int HEARTBEAT_INTERVAL = 5;

    private SessionAgingManager() {
    }

    /**
     * 初始化会话老化管理器
     *
     * <p>启动定时检查任务，由 {@link com.huawei.aisfsdkv2.session.impl.SessionManagerImpl} 调用。</p>
     */
    public void init() {
        startCheckTask();
    }

    /**
     * 启动定时检查任务
     */
    private synchronized void startCheckTask() {
        if (started) {
            return;
        }
        ThreadPoolManager.getInstance().addScheduleTaskSharedThreadPool(
            this::checkAndCleanup,
            CHECK_INTERVAL_MS,
            CHECK_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        started = true;
        LOGGER.info("SessionAgingManager: aging check task started, interval={}min", CHECK_INTERVAL_MS / 60000);
    }

    /**
     * 检查并清理老化会话
     */
    private void checkAndCleanup() {
        long now = System.currentTimeMillis();
        int checkedCount = 0;
        int cleanedCount = 0;
        Collection<Session> sessions = SessionManager.getInstance().listSessions();
        for (Session session : sessions) {
            if (session.getStatus() == SessionStatus.DESTROYED) {
                continue;
            }
            SessionAgingConfig config = session.getAgingConfig();
            if (config == null || !config.isAgingEnabled()) {
                continue;
            }
            checkedCount++;
            long idleTime = now - session.getLastActiveTime();
            long maxIdleMillis = (long) config.getMaxIdleTimeMinutes() * 60 * 1000;
            if (idleTime > maxIdleMillis) {
                String currentTaskId = session.getCurrentTaskId();
                if (currentTaskId != null && !currentTaskId.isEmpty()) {
                    LOGGER.info("SessionAgingManager.checkAndCleanup: skip aging session with running task, " +
                                    "sessionId={}, taskId={}",
                            session.getId(), currentTaskId);
                    continue;
                }
                LOGGER.info("SessionAgingManager.checkAndCleanup: aging session, sessionId={}, " +
                                "idleTime={}min, maxIdleTime={}min",
                        session.getId(), idleTime / 60000, config.getMaxIdleTimeMinutes());
                SessionManager.getInstance().destroySession(session.getId());
                cleanedCount++;
            }
        }
        if (cleanedCount > 0) {
            LOGGER.info("SessionAgingManager.checkAndCleanup: checked={}, cleaned={}", checkedCount, cleanedCount);
        }
        checkRound++;
        if (checkRound >= HEARTBEAT_INTERVAL) {
            checkRound = 0;
            LOGGER.info("SessionAgingManager.checkAndCleanup: heartbeat, totalSessions={}, agingSessions={}",
                    sessions.size(), checkedCount);
        }
    }
}
