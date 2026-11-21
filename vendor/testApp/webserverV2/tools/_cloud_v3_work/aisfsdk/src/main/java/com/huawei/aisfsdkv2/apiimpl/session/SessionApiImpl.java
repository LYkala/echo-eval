/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.session;

import com.huawei.aisfsdkv2.api.session.SessionApi;
import com.huawei.aisfsdkv2.api.session.SessionCallback;
import com.huawei.aisfsdkv2.api.session.SessionRequest;
import com.huawei.aisfsdkv2.api.session.SessionStatus;
import com.huawei.aisfsdkv2.api.session.GuiTaskEnum;
import com.huawei.aisfsdkv2.api.session.StreamResponseTimeoutProperty;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.session.Session;
import com.huawei.aisfsdkv2.session.SessionManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 会话API实现类
 *
 * <p>实现对外暴露的会话能力，内部使用 SessionManager。</p>
 */
public class SessionApiImpl implements SessionApi {
    private static final Logger LOGGER = LoggerFactory.getLogger("SessionApiImpl");

    /**
     * 单例持有者类（延迟加载 + 线程安全）
     */
    private static class Holder {
        private static final SessionApiImpl INSTANCE = new SessionApiImpl();
    }

    /**
     * 获取 SessionApiImpl 单例实例
     *
     * @return SessionApiImpl 单例
     */
    public static SessionApiImpl getInstance() {
        return Holder.INSTANCE;
    }

    private final SessionManager sessionManager;

    /**
     * 私有构造函数，通过 SessionManagerImpl 初始化会话管理器
     */
    private SessionApiImpl() {
        this.sessionManager = SessionManager.getInstance();
    }

    /**
     * 创建新会话
     *
     * @param callback 会话回调接口，用于接收会话事件通知，可以为 null
     * @return 会话 ID，创建失败返回 null
     */
    @Override
    public String createSession(SessionCallback callback) {
        return createSession(callback, false);
    }

    /**
     * 创建新会话（支持临时会话模式）
     *
     * <p>临时会话在收到 taskCompleted 消息且任务栈为空时，会自动销毁。</p>
     *
     * @param callback 会话回调接口，用于接收会话事件通知，可以为 null
     * @param isTemporary 是否为临时会话，true 表示临时会话，false 表示普通会话
     * @return 会话 ID，创建失败返回 null
     * @since 2026-05-19
     */
    @Override
    public String createSession(SessionCallback callback, boolean isTemporary) {
        Session session = sessionManager.createSession(callback, isTemporary);
        return session != null ? session.getId() : null;
    }

    /**
     * 创建默认会话
     *
     * @param callback 会话回调接口，用于接收会话事件通知，可以为 null
     * @return 会话 ID，创建失败返回 null
     */
    @Override
    public String createDefaultSession(SessionCallback callback) {
        Session session = sessionManager.createDefaultSession(callback);
        return session != null ? session.getId() : null;
    }

    /**
     * 销毁指定会话
     *
     * @param sessionId 会话 ID
     * @return true 销毁成功，false 会话不存在或已销毁
     */
    @Override
    public boolean destroySession(String sessionId) {
        return sessionManager.destroySession(sessionId);
    }

    /**
     * 销毁所有会话
     */
    @Override
    public void destroyAllSessions() {
        sessionManager.destroyAllSessions();
    }

    /**
     * 发送消息（使用请求对象）
     *
     * @param sessionId 会话 ID
     * @param request   消息请求对象
     * @return true 发送成功，false 发送失败
     */
    @Override
    public boolean sendMessage(String sessionId, SessionRequest request) {
        Session session = sessionManager.getSession(sessionId);
        if (session == null) {
            LOGGER.warn("SessionApiImpl.sendMessage: session not found, sessionId={}", sessionId);
            return false;
        }
        return session.send(request);
    }

    /**
     * 发送消息（简化调用）
     *
     * @param sessionId 会话 ID
     * @param userInputText 用户输入文本
     * @param intentList 用户意图列表
     * @param targetAgent 目标代理
     * @param voiceId 语音ID
     * @return true 发送成功，false 发送失败
     */
    @Override
    public boolean sendMessage(String sessionId, String userInputText, List<String> intentList,
            String targetAgent, String voiceId) {
        Session session = sessionManager.getSession(sessionId);
        if (session == null) {
            LOGGER.warn("SessionApiImpl.sendMessage: session not found, sessionId={}", sessionId);
            return false;
        }
        SessionRequest request = new SessionRequest.Builder()
                .userInputText(userInputText)
                .userIntent(intentList)
                .userSpecifiedAgent(targetAgent)
                .voiceId(voiceId)
                .build();
        return session.send(request);
    }

    /**
     * 发送RTF消息（带TTS）
     *
     * @param sessionId 会话 ID
     * @param ttsPath TTS文件路径
     * @param userInputText 用户输入文本
     * @param intentList 用户意图列表
     * @param userAgent 目标代理
     * @return true 发送成功，false 发送失败
     */
    @Override
    public boolean sendRTFMessage(String sessionId, String ttsPath, String userInputText,
            List<String> intentList, String userAgent) {
        Session session = sessionManager.getSession(sessionId);
        if (session == null) {
            LOGGER.warn("SessionApiImpl.sendRTFMessage: session not found, sessionId={}", sessionId);
            return false;
        }
        // 构造 UserInputObject data: {"ttsInfo":"ttsPath值"}
        String userInputObjectData;
        try {
            JSONObject dataObj = new JSONObject();
            dataObj.put("ttsInfo", ttsPath);
            userInputObjectData = dataObj.toString();
        } catch (Exception e) {
            LOGGER.error("SessionApiImpl.sendRTFMessage: failed to build userInputObjectData, " +
                    "sessionId={}, error={}", sessionId, e.getMessage());
            return false;
        }
        SessionRequest request = new SessionRequest.Builder()
                .userInputText(userInputText)
                .userIntent(intentList)
                .userSpecifiedAgent(userAgent)
                .userInputObjectCode(200006)
                .userInputObjectData(userInputObjectData)
                .build();
        return session.send(request);
    }

    /**
     * 发送GUI任务消息
     *
     * <p>仅支持接管相关操作，其他类型会被忽略并打印warn日志：
     * <ul>
     *   <li>{@link com.huawei.aisfsdkv2.api.session.GuiTaskEnum#START_TAKEOVER} - 开始接管</li>
     *   <li>{@link com.huawei.aisfsdkv2.api.session.GuiTaskEnum#STOP_TAKEOVER} - 停止接管</li>
     *   <li>{@link com.huawei.aisfsdkv2.api.session.GuiTaskEnum#CANCEL_TAKEOVER} - 取消接管</li>
     * </ul>
     *
     * @param sessionId 会话 ID
     * @param guiTask   GUI任务类型，仅支持 START_TAKEOVER/STOP_TAKEOVER/CANCEL_TAKEOVER
     * @param params    附加参数
     * @return true 发送成功，false 会话不存在、非活跃状态、无任务或不支持的任务类型
     */
    @Override
    public boolean sendMessage(String sessionId, GuiTaskEnum guiTask, Map<String, String> params) {
        Session session = sessionManager.getSession(sessionId);
        if (session == null) {
            LOGGER.warn("SessionApiImpl.sendMessage: session not found, sessionId={}", sessionId);
            return false;
        }
        return session.sendGuiTask(guiTask, params);
    }

    /**
     * 停止当前任务
     *
     * @param sessionId 会话 ID
     * @return true 发送成功，false 会话不存在、非活跃状态或无任务
     */
    @Override
    public boolean stopTask(String sessionId) {
        Session session = sessionManager.getSession(sessionId);
        if (session == null) {
            return false;
        }
        return session.stopTask();
    }

    /**
     * 停止算力主机任务
     *
     * @param sessionId 会话 ID
     * @param deviceCategory 设备类型
     * @return true 发送成功，false 会话不存在、非活跃状态或无任务
     */
    @Override
    public boolean stopOtherDeviceCategoryTask(String sessionId, String deviceCategory) {
        Session session = sessionManager.getSession(sessionId);
        if (session == null) {
            return false;
        }
        return session.stopOtherDeviceCategoryTask(deviceCategory);
    }

    /**
     * 获取会话状态
     *
     * @param sessionId 会话 ID
     * @return 会话状态，会话不存在返回 null
     */
    @Override
    public SessionStatus getSessionStatus(String sessionId) {
        Session session = sessionManager.getSession(sessionId);
        if (session == null) {
            return null;
        }
        return session.getStatus();
    }

    /**
     * 设置会话状态
     *
     * @param sessionId 会话 ID
     * @param status    新状态
     * @return true 设置成功，false 会话不存在或状态无效
     */
    @Override
    public boolean setSessionStatus(String sessionId, SessionStatus status) {
        Session session = sessionManager.getSession(sessionId);
        if (session == null) {
            return false;
        }
        return session.setStatus(status);
    }

    /**
     * 获取所有活跃会话的 ID 列表
     *
     * @return 活跃会话 ID 列表（不包含已销毁的会话）
     */
    @Override
    public List<String> getAllSessionIds() {
        List<String> result = new ArrayList<>();
        for (Session session : sessionManager.listSessions()) {
            if (session.getStatus() != SessionStatus.DESTROYED) {
                result.add(session.getId());
            }
        }
        return result;
    }

    /**
     * 停止消息处理
     *
     * <p>调用后，消息处理线程暂停处理队列中的消息，
     * 新消息继续入队缓存。</p>
     */
    @Override
    public void stopMessageHandle() {
        sessionManager.stopMessageProcess();
    }

    /**
     * 恢复消息处理
     *
     * <p>恢复后，处理线程继续处理队列中缓存的消息。</p>
     */
    @Override
    public void resumeMessageHandle() {
        sessionManager.resumeMessageProcess();
    }

    /**
     * 是否暂停消息处理
     *
     * @return true 暂停中，false 正常处理
     */
    @Override
    public boolean isMessageHandlePaused() {
        return sessionManager.isMessageProcessPaused();
    }

    /**
     * 获取会话属性
     *
     * <p>根据属性键获取指定会话的属性值。</p>
     *
     * @param sessionId 会话ID
     * @param key 属性键，不能为null或空
     * @return 属性值，会话不存在或属性不存在返回null
     * @since 2026-04-21
     */
    @Override
    public String getAttribute(String sessionId, String key) {
        Session session = sessionManager.getSession(sessionId);
        if (session == null) {
            LOGGER.warn("SessionApiImpl.getAttribute: session not found, sessionId={}", sessionId);
            return null;
        }
        return session.getAttribute(key);
    }

    /**
     * 设置会话属性
     *
     * <p>为指定会话添加或更新一个属性键值对。会话不存在或已销毁时操作失败。</p>
     *
     * @param sessionId 会话ID
     * @param key 属性键，不能为null或空
     * @param value 属性值，不能为null
     * @return true 设置成功，false 会话不存在、已销毁或参数非法
     * @since 2026-04-21
     */
    @Override
    public boolean setAttribute(String sessionId, String key, String value) {
        Session session = sessionManager.getSession(sessionId);
        if (session == null) {
            LOGGER.warn("SessionApiImpl.setAttribute: session not found, sessionId={}", sessionId);
            return false;
        }
        return session.setAttribute(key, value);
    }

    /**
     * 删除会话属性
     *
     * <p>移除指定会话中某个键的属性。会话不存在或已销毁时操作失败。</p>
     *
     * @param sessionId 会话ID
     * @param key 属性键，不能为null或空
     * @return true 删除成功，false 会话不存在、属性不存在或参数非法
     * @since 2026-04-21
     */
    @Override
    public boolean removeAttribute(String sessionId, String key) {
        Session session = sessionManager.getSession(sessionId);
        if (session == null) {
            LOGGER.warn("SessionApiImpl.removeAttribute: session not found, sessionId={}", sessionId);
            return false;
        }
        return session.removeAttribute(key);
    }

    /**
     * 根据自定义条件过滤会话
     *
     * <p>遍历所有会话，返回满足过滤条件的会话列表。</p>
     *
     * @param filter 过滤函数，返回true表示匹配，不能为null
     * @return 满足条件的会话列表，filter为null返回空列表
     * @since 2026-04-21
     */
    @Override
    public List<Session> getSessions(Predicate<Session> filter) {
        List<Session> result = new ArrayList<>();
        if (filter == null) {
            LOGGER.warn("SessionApiImpl.getSessions: filter is null");
            return result;
        }
        for (Session session : sessionManager.listSessions()) {
            try {
                if (filter.test(session)) {
                    result.add(session);
                }
            } catch (Exception e) {
                LOGGER.warn("SessionApiImpl.getSessions: filter exception, sessionId={}, error={}",
                        session.getId(), e.getMessage());
            }
        }
        return result;
    }

    /**
     * 设置流式响应超时属性
     *
     * @param sessionId 会话ID
     * @param property 流式响应超时属性，null表示移除超时配置
     * @return true 设置成功，false 会话不存在或已销毁
     */
    @Override
    public boolean setStreamResponseTimeoutProperty(String sessionId, StreamResponseTimeoutProperty property) {
        Session session = sessionManager.getSession(sessionId);
        if (session == null) {
            LOGGER.warn("SessionApiImpl.setStreamResponseTimeoutProperty: session not found, sessionId={}", sessionId);
            return false;
        }
        session.setStreamResponseTimeoutProperty(property);
        return true;
    }

    /**
     * 获取流式响应超时属性
     *
     * @param sessionId 会话ID
     * @return 超时属性，会话不存在或未设置时返回null
     */
    @Override
    public StreamResponseTimeoutProperty getStreamResponseTimeoutProperty(String sessionId) {
        Session session = sessionManager.getSession(sessionId);
        if (session == null) {
            LOGGER.warn("SessionApiImpl.getStreamResponseTimeoutProperty: session not found, sessionId={}", sessionId);
            return null;
        }
        return session.getStreamResponseTimeoutProperty();
    }
}
