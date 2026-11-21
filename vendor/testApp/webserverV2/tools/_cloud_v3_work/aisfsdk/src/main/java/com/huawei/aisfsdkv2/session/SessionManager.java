/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.session;

import com.huawei.aisfsdkv2.api.session.SessionCallback;
import com.huawei.aisfsdkv2.session.impl.SessionManagerImpl;

import java.util.Collection;

/**
 * 会话管理器接口
 * 定义核心会话管理操作
 */
public interface SessionManager {
    /**
     * 获取 SessionManager 单例实例
     */
    static SessionManager getInstance() {
        return SessionManagerImpl.getInstance();
    }

    /**
     * 创建新会话（主动会话）
     * @param callback 会话回调接口，用于接收会话事件通知，可以为null
     * @return 新创建的主动会话
     */
    Session createSession(SessionCallback callback);

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
    Session createSession(SessionCallback callback, boolean isTemporary);

    /**
     * 创建默认会话
     *
     * <p>默认会话用于接收无明确目标会话的消息。
     * 同一时刻只有一个默认会话，创建新的默认会话会自动取消旧的默认会话标记。</p>
     *
     * @param callback 会话回调接口，用于接收会话事件通知，可以为null
     * @return 新创建的默认会话
     */
    Session createDefaultSession(SessionCallback callback);

    /**
     * 销毁会话
     * @param sessionId 会话ID
     * @return true 销毁成功，false 会话不存在
     */
    boolean destroySession(String sessionId);

    /**
     * 销毁所有会话
     */
    void destroyAllSessions();

    /**
     * 获取会话
     * @param sessionId 会话ID
     * @return 会话对象，不存在返回 null
     */
    Session getSession(String sessionId);

    /**
     * 列出所有会话
     * @return 所有会话集合
     */
    Collection<Session> listSessions();

    /**
     * 停止消息处理
     *
     * <p>调用后，消息处理线程暂停处理队列中的消息，
     * 新消息继续入队缓存。</p>
     */
    void stopMessageProcess();

    /**
     * 恢复消息处理
     *
     * <p>恢复后，处理线程继续处理队列中缓存的消息。</p>
     */
    void resumeMessageProcess();

    /**
     * 是否暂停消息处理
     *
     * @return true 暂停中，false 正常处理
     */
    boolean isMessageProcessPaused();
}
