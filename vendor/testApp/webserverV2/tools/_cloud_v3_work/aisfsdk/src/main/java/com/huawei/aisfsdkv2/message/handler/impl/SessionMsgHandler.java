/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler.impl;

import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.message.handler.MessageHandler;
import com.huawei.aisfsdkv2.session.Session;
import com.huawei.aisfsdkv2.session.SessionManager;

import org.json.JSONObject;

/**
 * 会话消息处理器
 *
 * <p>处理服务端推送的 UserOutput 类型消息。</p>
 *
 * <p>处理流程：</p>
 * <ol>
 *   <li>检查 Header.MessageType 是否为 "DataExchange"</li>
 *   <li>检查 Data.MessageType 是否为 "UserOutput"</li>
 *   <li>从 Header 提取 ClientSessionId</li>
 *   <li>将完整 JSON 数据传递给 session.receive() 处理</li>
 * </ol>
 */
public class SessionMsgHandler implements MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("SessionMsgHandler");

    private static final String JSON_KEY_HEADER = "Header";
    private static final String JSON_KEY_DATA = "Data";
    private static final String JSON_KEY_MESSAGE_TYPE = "MessageType";
    private static final String JSON_KEY_CLIENT_SESSION_ID = "ClientSessionId";

    private static final String MESSAGE_TYPE_DATA_EXCHANGE = "DataExchange";
    private static final String MESSAGE_TYPE_USER_OUTPUT = "UserOutput";

    private final SessionManager sessionManager;

    /**
     * 构造方法，初始化会话管理器
     */
    public SessionMsgHandler() {
        this.sessionManager = SessionManager.getInstance();
    }

    /**
     * 获取处理器名称
     *
     * @return 处理器名称
     */
    @Override
    public String getName() {
        return "SessionMsgHandler";
    }

    /**
     * 获取处理器优先级
     *
     * @return 优先级 20
     */
    @Override
    public int getPriority() {
        return 20;
    }

    /**
     * 处理接收到的消息
     *
     * <p>仅处理满足以下条件的消息：</p>
     * <ul>
     *   <li>Header.MessageType = "DataExchange"</li>
     *   <li>Data.MessageType = "UserOutput"</li>
     * </ul>
     *
     * <p>找到对应会话后将数据传递给 session.receive() 方法处理。</p>
     *
     * @param json JSON消息对象
     * @return true 表示消息已消费；false 表示不处理，继续传递
     */
    @Override
    public boolean handleReceived(JSONObject json) {
        JSONObject header = json.optJSONObject(JSON_KEY_HEADER);
        if (header == null) {
            return false;
        }
        String headerMsgType = header.optString(JSON_KEY_MESSAGE_TYPE);
        if (!MESSAGE_TYPE_DATA_EXCHANGE.equals(headerMsgType)) {
            return false;
        }
        JSONObject data = json.optJSONObject(JSON_KEY_DATA);
        if (data == null) {
            return false;
        }
        String dataMsgType = data.optString(JSON_KEY_MESSAGE_TYPE);
        if (!MESSAGE_TYPE_USER_OUTPUT.equals(dataMsgType)) {
            return false;
        }
        String clientSessionId = header.optString(JSON_KEY_CLIENT_SESSION_ID);
        Session targetSession = null;
        if (!clientSessionId.isEmpty()) {
            targetSession = sessionManager.getSession(clientSessionId);
        }
        if (targetSession == null) {
            targetSession = findDefaultSession();
        }
        if (targetSession != null) {
            targetSession.receive(json);
        } else {
            LOGGER.warn("SessionMsgHandler.handleReceived: no session matched for clientSessionId={}", clientSessionId);
        }
        return true;
    }

    /**
     * 查找默认会话
     *
     * <p>遍历所有会话，返回标记为默认的会话。</p>
     *
     * @return 默认会话，未找到返回 null
     */
    private Session findDefaultSession() {
        for (Session session : sessionManager.listSessions()) {
            if (session.isDefault()) {
                return session;
            }
        }
        return null;
    }
}
