/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.session.impl;

import com.huawei.aisfsdkv2.api.session.SessionCallback;
import com.huawei.aisfsdkv2.api.session.SessionStatus;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.message.MessageProcessor;
import com.huawei.aisfsdkv2.session.Session;
import com.huawei.aisfsdkv2.session.SessionManager;
import com.huawei.aisfsdkv2.session.SessionType;
import com.huawei.aisfsdkv2.session.aging.SessionAgingManager;
import com.huawei.aisfsdkv2.session.timeout.StreamResponseTimeoutManager;
import com.huawei.aisfsdkv2.session.passive.PassiveSessionManager;
import com.huawei.aisfsdkv2.util.RandomUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器实现类
 * 线程安全的单例，管理所有会话的生命周期
 */
public class SessionManagerImpl implements SessionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("SessionManagerImpl");

    /**
     * 静态内部类单例（线程安全 + 延迟加载）
     */
    private static class Holder {
        private static final SessionManagerImpl INSTANCE = new SessionManagerImpl();
    }

    /**
     * 获取单例实例
     *
     * @return SessionManagerImpl 单例
     */
    public static SessionManagerImpl getInstance() {
        return Holder.INSTANCE;
    }

    private final ConcurrentHashMap<String, Session> sessions;

    /**
     * 私有构造函数，防止外部实例化
     */
    private SessionManagerImpl() {
        this.sessions = new ConcurrentHashMap<>();
        SessionAgingManager.getInstance().init();
        StreamResponseTimeoutManager.getInstance().init();
    }

    /**
     * 创建新会话（主动会话）
     *
     * @param callback 会话回调接口，用于接收会话事件通知，可以为null
     * @return 新创建的主动会话
     */
    @Override
    public Session createSession(SessionCallback callback) {
        return createSession(callback, false);
    }

    /**
     * 创建新会话（主动会话），支持临时会话模式
     *
     * <p>临时会话在收到 taskCompleted 消息且任务栈为空时，会自动销毁。</p>
     *
     * @param callback 会话回调接口，用于接收会话事件通知，可以为null
     * @param isTemporary 是否为临时会话，true 表示临时会话，false 表示普通会话
     * @return 新创建的主动会话
     * @since 2026-05-19
     */
    @Override
    public Session createSession(SessionCallback callback, boolean isTemporary) {
        String sessionId = RandomUtil.getSessionId();
        Session session = new SessionImpl(sessionId, SessionType.ACTIVE, callback);
        session.setTemporary(isTemporary);
        sessions.put(sessionId, session);
        LOGGER.info("SessionManagerImpl.createSession: session created, " +
                "sessionId={}, isTemporary={}", sessionId, isTemporary);
        return session;
    }

    /**
     * 创建默认会话
     *
     * <p>创建前会先遍历所有会话，取消已有默认会话的标记，
     * 确保同一时刻只有一个默认会话。</p>
     *
     * @param callback 会话回调接口，用于接收会话事件通知，可以为null
     * @return 新创建的默认会话
     */
    @Override
    public Session createDefaultSession(SessionCallback callback) {
        for (Session session : sessions.values()) {
            if (session.isDefault()) {
                session.setDefault(false);
                LOGGER.info("SessionManagerImpl.createDefaultSession: cleared previous default session, " +
                        "sessionId={}", session.getId());
            }
        }
        String sessionId = RandomUtil.getSessionId();
        Session session = new SessionImpl(sessionId, SessionType.PASSIVE, callback);
        session.setDefault(true);
        sessions.put(sessionId, session);
        PassiveSessionManager.getInstance().registerSession(sessionId);
        LOGGER.info("SessionManagerImpl.createDefaultSession: default session created, sessionId={}", sessionId);
        return session;
    }

    /**
     * 销毁会话
     *
     * @param sessionId 会话ID
     * @return true 销毁成功，false 会话不存在
     */
    @Override
    public boolean destroySession(String sessionId) {
        Session session = sessions.remove(sessionId);
        if (session == null) {
            LOGGER.warn("SessionManagerImpl.destroySession: session not found, sessionId={}", sessionId);
            return false;
        }
        session.setStatus(SessionStatus.DESTROYED);
        LOGGER.info("SessionManagerImpl.destroySession: session destroyed, " +
                "sessionId={}, sessionType={}", sessionId, session.getSessionType());
        return true;
    }

    /**
     * 销毁所有会话
     */
    @Override
    public void destroyAllSessions() {
        for (String id : new ArrayList<>(sessions.keySet())) {
            destroySession(id);
        }
    }

    /**
     * 获取会话
     *
     * @param sessionId 会话ID
     * @return 会话对象，不存在返回 null
     */
    @Override
    public Session getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 列出所有会话
     *
     * @return 所有会话集合
     */
    @Override
    public Collection<Session> listSessions() {
        return new ArrayList<>(sessions.values());
    }

    /**
     * 获取当前会话数量（用于测试）
     *
     * @return 会话数量
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * 停止消息处理
     *
     * <p>调用后，消息处理线程暂停处理队列中的消息，
     * 新消息继续入队缓存。</p>
     */
    @Override
    public void stopMessageProcess() {
        MessageProcessor.getInstance().pause();
        LOGGER.info("SessionManager: message process stopped, queueSize={}",
                MessageProcessor.getInstance().getQueueSize());
    }

    /**
     * 恢复消息处理
     *
     * <p>恢复后，处理线程继续处理队列中缓存的消息。</p>
     */
    @Override
    public void resumeMessageProcess() {
        MessageProcessor.getInstance().resume();
        LOGGER.info("SessionManager: message process resumed, queueSize={}",
                MessageProcessor.getInstance().getQueueSize());
    }

    /**
     * 是否暂停消息处理
     *
     * @return true 暂停中，false 正常处理
     */
    @Override
    public boolean isMessageProcessPaused() {
        return MessageProcessor.getInstance().isPaused();
    }
}
