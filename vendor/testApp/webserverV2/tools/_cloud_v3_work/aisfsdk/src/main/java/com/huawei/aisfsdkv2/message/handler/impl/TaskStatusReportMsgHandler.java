/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler.impl;

import com.huawei.aisfsdkv2.api.session.NotifyInfo;
import com.huawei.aisfsdkv2.api.session.OutputTypeEnum;
import com.huawei.aisfsdkv2.api.session.SessionResponseData;
import com.huawei.aisfsdkv2.api.session.TaskInfo;
import com.huawei.aisfsdkv2.session.Session;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.message.handler.MessageHandler;
import com.huawei.aisfsdkv2.session.SessionManager;

import org.json.JSONObject;

/**
 * 任务状态报告消息处理器
 *
 * <p>处理服务端推送的任务状态报告消息。</p>
 */
public class TaskStatusReportMsgHandler implements MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("TaskStatusReportMsgHandler");

    private static final String JSON_KEY_HEADER = "Header";
    private static final String JSON_KEY_DATA = "Data";
    private static final String JSON_KEY_MESSAGE_TYPE = "MessageType";
    private static final String JSON_KEY_CLIENT_SESSION_ID = "ClientSessionId";
    private static final String JSON_KEY_TASK_ID = "TaskID";
    private static final String JSON_KEY_TASK_STATUS_REPORT = "TaskStatusReport";
    private static final String TASK_STATUS_COMPLETED = "TaskCompleted";
    private static final String JSON_KEY_TASK_STATUS_CODE = "TaskStatusCode";
    private static final String JSON_KEY_USER_OUTPUT_OBJECT = "UserOutputObject";
    private static final String JSON_KEY_NOTIFY_CODE = "code";

    private static final String MESSAGE_TYPE_TASK_STATUS_MANAGEMENT = "TaskStatusManagement";
    private static final String MESSAGE_TYPE_TASK_STATUS_REPORT = "TaskStatusReport";

    /**
     * 获取处理器名称
     *
     * @return 处理器名称 "TaskStatusReportMsgHandler"
     */
    @Override
    public String getName() {
        return "TaskStatusReportMsgHandler";
    }

    /**
     * 获取处理器优先级
     *
     * @return 优先级 30
     */
    @Override
    public int getPriority() {
        return 30;
    }

    /**
     * 处理接收到的任务状态报告消息
     *
     * <p>仅处理满足以下条件的消息：</p>
     * <ul>
     *   <li>Header.MessageType = "TaskStatusManagement"</li>
     *   <li>Data.MessageType = "TaskStatusReport"</li>
     *   <li>Data.TaskStatusReport = "TaskCompleted"（任务完成时才触发回调）</li>
     * </ul>
     *
     * @param json JSON消息对象
     * @return true 表示消息已消费；false 表示不处理，继续传递
     */
    @Override
    public boolean handleReceived(JSONObject json) {
        if (json == null) {
            LOGGER.warn("TaskStatusReportMsgHandler.handleReceived: json is null, skip");
            return false;
        }
        if (!isValidTaskStatusMessage(json)) {
            return false;
        }
        LOGGER.info("TaskStatusReportMsgHandler.handleReceived: receive TaskStatusReport message");
        JSONObject data = json.optJSONObject(JSON_KEY_DATA);
        JSONObject header = json.optJSONObject(JSON_KEY_HEADER);
        String taskStatusReport = data.optString(JSON_KEY_TASK_STATUS_REPORT, "");
        if (!TASK_STATUS_COMPLETED.equals(taskStatusReport)) {
            LOGGER.info("TaskStatusReportMsgHandler.handleReceived: task not completed, skip callback");
            return true;
        }
        triggerTaskCompletedCallback(header, data);
        return true;
    }

    /**
     * 验证消息是否为有效的任务状态报告消息
     *
     * @param json JSON消息对象
     * @return true 如果是有效的任务状态报告消息
     */
    private boolean isValidTaskStatusMessage(JSONObject json) {
        JSONObject header = json.optJSONObject(JSON_KEY_HEADER);
        if (header == null) {
            return false;
        }
        String headerMsgType = header.optString(JSON_KEY_MESSAGE_TYPE);
        if (!MESSAGE_TYPE_TASK_STATUS_MANAGEMENT.equals(headerMsgType)) {
            return false;
        }
        JSONObject data = json.optJSONObject(JSON_KEY_DATA);
        if (data == null) {
            return false;
        }
        String dataMsgType = data.optString(JSON_KEY_MESSAGE_TYPE);
        return MESSAGE_TYPE_TASK_STATUS_REPORT.equals(dataMsgType);
    }

    /**
     * 触发任务完成回调
     *
     * @param header JSON头部对象
     * @param data JSON数据对象
     */
    private void triggerTaskCompletedCallback(JSONObject header, JSONObject data) {
        int taskStatusCode = data.optInt(JSON_KEY_TASK_STATUS_CODE, 0);
        String taskId = header.optString(JSON_KEY_TASK_ID, "");
        String clientSessionId = header.optString(JSON_KEY_CLIENT_SESSION_ID, "");
        String userOutputObjectStr = data.optString(JSON_KEY_USER_OUTPUT_OBJECT, "");
        LOGGER.info("TaskStatusReportMsgHandler.handleReceived: task completed, taskId={}, taskStatusCode={}", taskId, taskStatusCode);
        SessionResponseData responseData = buildTaskCompletedResponse(taskStatusCode, taskId, userOutputObjectStr);
        Session session = SessionManager.getInstance().getSession(clientSessionId);
        boolean sent = session != null && session.receive(responseData);
        if (sent) {
            LOGGER.info("TaskStatusReportMsgHandler.handleReceived: task finish callback triggered, " +
                    "taskId={}, taskStatusCode={}", taskId, taskStatusCode);
        }
    }

    /**
     * 构建任务完成响应数据
     *
     * <p>当 UserOutputObject 不为空时，同时构建 NotifyInfo 附加到响应数据中。</p>
     *
     * @param taskStatusCode 任务状态码，200表示正常
     * @param taskId 任务ID
     * @param userOutputObjectStr UserOutputObject原始字符串，可为空
     * @return SessionResponseData 包含任务信息的响应数据
     */
    private SessionResponseData buildTaskCompletedResponse(int taskStatusCode, String taskId,
                                                           String userOutputObjectStr) {
        TaskInfo taskInfo = new TaskInfo();
        taskInfo.setTaskCompleted(true);
        taskInfo.setTaskStatusCode(taskStatusCode);
        taskInfo.setTaskId(taskId);
        SessionResponseData responseData = new SessionResponseData();
        responseData.setOutputType(OutputTypeEnum.TASK_STATUS);
        responseData.setTaskInfo(taskInfo);
        if (!userOutputObjectStr.isEmpty()) {
            NotifyInfo notifyInfo = new NotifyInfo();
            notifyInfo.setUserOutputObjectRawString(userOutputObjectStr);
            try {
                JSONObject userOutputObject = new JSONObject(userOutputObjectStr);
                notifyInfo.setCode(userOutputObject.optInt(JSON_KEY_NOTIFY_CODE, 0));
            } catch (Exception e) {
                LOGGER.warn("buildTaskCompletedResponse: failed to parse UserOutputObject, error={}", e.getMessage());
            }
            responseData.setNotifyInfo(notifyInfo);
        }
        return responseData;
    }
}
