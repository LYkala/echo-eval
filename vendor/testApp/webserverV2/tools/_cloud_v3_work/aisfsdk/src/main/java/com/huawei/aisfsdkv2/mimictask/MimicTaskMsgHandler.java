/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.mimictask;

import com.huawei.aisfsdkv2.component.androidfunc.accessibility.AccessibilityManager;
import com.huawei.aisfsdkv2.component.androidfunc.AndroidFuncManager;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.message.handler.MessageHandler;
import com.huawei.aisfsdkv2.apiimpl.session.SessionApiImpl;
import com.huawei.aisfsdkv2.session.Session;
import com.huawei.aisfsdkv2.session.SessionManager;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 模仿任务消息处理器
 *
 * <p>处理特定类型的 UserOutput 消息，过滤条件：</p>
 * <ul>
 *   <li>Header.MessageType = "DataExchange"</li>
 *   <li>Data.MessageType = "UserOutput"</li>
 *   <li>Data.UserOutputFlag = 1</li>
 *   <li>Data.UserOutputObject.code = 210001/210003/210006</li>
 * </ul>
 *
 * <p>支持的 code 类型：</p>
 * <ul>
 *   <li>210001 - START_SKILL 开始技能</li>
 *   <li>210003 - STOP_SKILL 停止技能</li>
 *   <li>210006 - STOP_ERROR_SKILL 异常停止技能</li>
 * </ul>
 *
 * @since 2026-04-08
 */
public class MimicTaskMsgHandler implements MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("MimicTaskMsgHandler");

    private static final String JSON_KEY_HEADER = "Header";
    private static final String JSON_KEY_DATA = "Data";
    private static final String JSON_KEY_MESSAGE_TYPE = "MessageType";
    private static final String JSON_KEY_USER_OUTPUT_FLAG = "UserOutputFlag";
    private static final String JSON_KEY_USER_OUTPUT_OBJECT = "UserOutputObject";
    private static final String JSON_KEY_CODE = "code";

    private static final String MESSAGE_TYPE_DATA_EXCHANGE = "DataExchange";
    private static final String MESSAGE_TYPE_USER_OUTPUT = "UserOutput";
    private static final int USER_OUTPUT_FLAG_VALUE = 1;

    /** 会话属性键：标记会话处于模仿任务模式 */
    private static final String ATTRIBUTE_KEY_MIMIC = "MIMIC";

    private static final String JSON_KEY_CLIENT_SESSION_ID = "ClientSessionId";

    /**
     * 获取处理器名称
     *
     * @return 处理器名称
     */
    @Override
    public String getName() {
        return "MimicTaskMsgHandler";
    }

    /**
     * 获取处理器优先级
     *
     * @return 优先级 5（最高优先级，优先处理模仿任务消息）
     */
    @Override
    public int getPriority() {
        return 5;
    }

    /**
     * 处理接收到的消息
     *
     * <p>仅处理满足以下条件的消息：</p>
     * <ul>
     *   <li>Header.MessageType = "DataExchange"</li>
     *   <li>Data.MessageType = "UserOutput"</li>
     *   <li>Data.UserOutputFlag = 1</li>
     *   <li>Data.UserOutputObject.code = 210001/210003/210006</li>
     * </ul>
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
        int userOutputFlag = data.optInt(JSON_KEY_USER_OUTPUT_FLAG, 0);
        if (userOutputFlag != USER_OUTPUT_FLAG_VALUE) {
            return false;
        }
        String userOutputObjectStr = data.optString(JSON_KEY_USER_OUTPUT_OBJECT, "");
        if (userOutputObjectStr.isEmpty()) {
            return false;
        }
        int code = parseCodeFromUserOutputObject(userOutputObjectStr);
        MimicActionType actionType = MimicActionType.fromCode(code);
        if (actionType == MimicActionType.UNKNOWN) {
            return false;
        }
        LOGGER.info("MimicTaskMsgHandler.handleReceived: matched message, code={}, actionType={}", code, actionType);
        String sessionId = header.optString(JSON_KEY_CLIENT_SESSION_ID, "");
        handleMimicTask(actionType, sessionId);
        // START_SKILL 时返回 false，让后续处理器继续处理
        return actionType != MimicActionType.START_SKILL;
    }

    /**
     * 从 UserOutputObject JSON 字符串中解析 code 字段
     *
     * @param userOutputObjectStr JSON 字符串
     * @return code 值，解析失败返回 -1
     */
    private int parseCodeFromUserOutputObject(String userOutputObjectStr) {
        try {
            JSONObject userOutputObject = new JSONObject(userOutputObjectStr);
            return userOutputObject.optInt(JSON_KEY_CODE, -1);
        } catch (JSONException e) {
            LOGGER.warn("MimicTaskMsgHandler: failed to parse UserOutputObject, str={}", userOutputObjectStr);
            return -1;
        }
    }

    /**
     * 处理匹配的模仿任务消息
     *
     * @param actionType 动作类型枚举
     * @param sessionId 会话ID，来自 Header.ClientSessionId
     */
    private void handleMimicTask(MimicActionType actionType, String sessionId) {
        LOGGER.info("MimicTaskMsgHandler.handleMimicTask: actionType={}, sessionId={}", actionType, sessionId);
        AccessibilityManager accessibilityManager = AndroidFuncManager.getInstance().getAccessibilityManager();
        if (accessibilityManager == null) {
            LOGGER.warn("MimicTaskMsgHandler.handleMimicTask: AccessibilityManager is null");
            return;
        }
        switch (actionType) {
            case START_SKILL:
                startMimicSkill(accessibilityManager, sessionId);
                break;
            case STOP_SKILL:
            case STOP_ERROR_SKILL:
                stopMimicSkill(accessibilityManager, actionType);
                break;
            default:
                LOGGER.warn("MimicTaskMsgHandler.handleMimicTask: unknown actionType={}", actionType);
                break;
        }
    }

    /**
     * 开始模仿技能
     *
     * <p>执行流程：回桌面 → 注册无障碍事件处理器 → 设置MIMIC属性 → 启动定时截图。</p>
     *
     * @param accessibilityManager 无障碍管理器
     * @param sessionId 会话ID
     */
    private void startMimicSkill(AccessibilityManager accessibilityManager, String sessionId) {
        cleanupExistingMimicSession();
        boolean goHomeResult = accessibilityManager.goHome();
        if (!goHomeResult) {
            LOGGER.warn("MimicTaskMsgHandler.startMimicSkill: goHome failed, abort mimic task");
            return;
        }
        MimicAccessibilityEventHandler eventHandler = MimicAccessibilityEventHandler.getInstance();
        accessibilityManager.registerAccessibilityHandler(eventHandler);
        eventHandler.resetStatus();
        setMimicAttribute(sessionId);
        ScheduleScreenshotManager.getInstance().start();
        LOGGER.info("MimicTaskMsgHandler.startMimicSkill: mimic skill started, sessionId={}", sessionId);
    }

    /**
     * 停止模仿技能
     *
     * <p>执行流程：注销无障碍事件处理器 → 重置状态 → 停止定时截图。</p>
     *
     * @param accessibilityManager 无障碍管理器
     * @param actionType 动作类型（STOP_SKILL 或 STOP_ERROR_SKILL）
     */
    private void stopMimicSkill(AccessibilityManager accessibilityManager, MimicActionType actionType) {
        MimicAccessibilityEventHandler eventHandler = MimicAccessibilityEventHandler.getInstance();
        accessibilityManager.unregisterAccessibilityHandler(eventHandler);
        eventHandler.resetStatus();
        ScheduleScreenshotManager.getInstance().stop();
        LOGGER.info("MimicTaskMsgHandler.stopMimicSkill: mimic skill stopped, actionType={}", actionType);
    }

    /**
     * 清理已有的 MIMIC 会话
     *
     * <p>如果存在残留的 MIMIC 属性会话，销毁它们，防止状态不一致。</p>
     */
    private void cleanupExistingMimicSession() {
        java.util.List<Session> sessions = SessionApiImpl.getInstance().getSessions(
                session -> Boolean.TRUE.toString().equals(session.getAttribute(ATTRIBUTE_KEY_MIMIC)));
        for (Session session : sessions) {
            LOGGER.warn("MimicTaskMsgHandler.cleanupExistingMimicSession: destroying stale MIMIC session, " +
                    "sessionId={}", session.getId());
            SessionManager.getInstance().destroySession(session.getId());
        }
    }

    /**
     * 为会话设置 MIMIC 属性
     *
     * <p>标记会话处于模仿任务模式，供后续 sendMimicMsg 等场景使用。</p>
     *
     * @param sessionId 会话ID
     */
    private void setMimicAttribute(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            LOGGER.warn("MimicTaskMsgHandler.setMimicAttribute: sessionId is empty, skip setting MIMIC attribute");
            return;
        }
        Session session = SessionManager.getInstance().getSession(sessionId);
        if (session == null) {
            LOGGER.warn("MimicTaskMsgHandler.setMimicAttribute: session not found, sessionId={}", sessionId);
            return;
        }
        session.setAttribute(ATTRIBUTE_KEY_MIMIC, Boolean.TRUE.toString());
        LOGGER.info("MimicTaskMsgHandler.setMimicAttribute: MIMIC attribute set, sessionId={}", sessionId);
    }
}
