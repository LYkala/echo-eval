/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.session.impl;

import com.huawei.aisfsdkv2.api.session.SessionCallback;
import com.huawei.aisfsdkv2.api.session.SessionAgingConfig;
import com.huawei.aisfsdkv2.api.session.NotifyInfo;
import com.huawei.aisfsdkv2.api.session.SessionRequest;
import com.huawei.aisfsdkv2.api.session.SessionRequest.OutputPersonality;
import com.huawei.aisfsdkv2.api.session.SessionResponseData;
import com.huawei.aisfsdkv2.api.session.StreamResponseTimeoutProperty;

import com.huawei.aisfsdkv2.api.session.SessionStatus;
import com.huawei.aisfsdkv2.api.session.DialogControl;
import com.huawei.aisfsdkv2.api.session.DialogInfo;
import com.huawei.aisfsdkv2.api.session.GuiTaskEnum;
import com.huawei.aisfsdkv2.api.session.GuiTaskInfo;
import com.huawei.aisfsdkv2.api.session.TaskInfo;
import com.huawei.aisfsdkv2.api.session.OutputTypeEnum;
import com.huawei.aisfsdkv2.api.session.TextInfo;
import com.huawei.aisfsdkv2.component.androidfunc.AndroidFuncManager;
import com.huawei.aisfsdkv2.component.androidfunc.screenshot.MediaScreenshotService;
import com.huawei.aisfsdkv2.component.androidfunc.screenshot.ScreenshotManager;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.message.MessageProcessor;
import com.huawei.aisfsdkv2.message.model.SessionMsgInfo;
import com.huawei.aisfsdkv2.session.Session;
import com.huawei.aisfsdkv2.session.SessionManager;
import com.huawei.aisfsdkv2.session.SessionType;
import com.huawei.aisfsdkv2.util.StringUtil;


import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话实现类
 * 实现会话状态管理和数据发送，使用 MessageProcess 发送消息
 */
public class SessionImpl implements Session {
    private static final Logger LOGGER = LoggerFactory.getLogger("SessionImpl");

    private static final String ERROR_SESSION_ID_NULL = "Session ID cannot be null or empty";

    private static final String JSON_KEY_HEADER = "Header";
    private static final String JSON_KEY_DATA = "Data";
    private static final String JSON_KEY_TEAM_ENVIRONMENT_ID = "TeamEnvironmentID";
    private static final String JSON_KEY_USER_OUTPUT_TEXT = "UserOutputText";
    private static final String JSON_KEY_USER_OUTPUT_OBJECT = "UserOutputObject";
    private static final String JSON_KEY_STREAM_STATUS = "StreamStatus";
    private static final String JSON_KEY_IS_VOICE_BROADCAST = "IsVoiceBroadcast";
    private static final String JSON_KEY_NOTIFY_CODE = "code";

    private static final String JSON_KEY_TASK_ID = "TaskID";

    private static final String STREAM_STATUS_COMPLETED = "Completed";

    private static final String DEFAULT_USER_SPECIFIED_AGENT = "-1";

    private static final int GUI_TASK_CODE = 300001;

    private static final String JSON_KEY_PAYLOAD = "Payload";
    private static final String JSON_KEY_TITLE = "Title";
    private static final String JSON_KEY_CONTROL_LIST = "ControlList";
    private static final String JSON_KEY_CONTENT = "Content";
    private static final String JSON_KEY_TYPE = "Type";

    private static final String HEADER_MSG_TASK_ASSIGNMENT = "UserTaskManagement";
    private static final String HEADER_MSG_DATA_EXCHANGE = "DataExchange";
    private static final String DATA_MSG_TASK_ASSIGNMENT = "TaskAssignment";
    private static final String DATA_MSG_USER_INPUT = "UserInput";
    private static final String DATA_MSG_TASK_STOP = "TaskStop";

    private final String sessionId;
    private volatile SessionStatus status;
    private final SessionCallback callback;
    private volatile String teamEnvironmentId;
    private volatile boolean guiTaskRunning = false;
    private final TaskStackManager taskStackManager;
    private final SessionType sessionType;
    private final ConcurrentHashMap<String, String> attributes = new ConcurrentHashMap<>();
    private volatile long lastActiveTime;
    private volatile long lastReceiveTime;
    private volatile long lastSendTime;
    private volatile SessionAgingConfig agingConfig;
    private volatile StreamResponseTimeoutProperty streamResponseTimeoutProperty;
    private volatile boolean isDefault = false;
    private volatile boolean isTemporary = false;

    /**
     * 创建主动会话
     *
     * @param sessionId 会话ID，不能为null或空
     * @param callback 会话回调接口，可以为null
     * @throws IllegalArgumentException 如果 sessionId 为 null 或空
     */
    public SessionImpl(String sessionId, SessionCallback callback) {
        this(sessionId, SessionType.ACTIVE, callback);
    }

    /**
     * 创建会话
     *
     * @param sessionId 会话ID，不能为null或空
     * @param sessionType 会话类型，不能为null
     * @param callback 会话回调接口，可以为null
     * @throws IllegalArgumentException 如果 sessionId 为 null 或空
     */
    public SessionImpl(String sessionId, SessionType sessionType, SessionCallback callback) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException(ERROR_SESSION_ID_NULL);
        }
        this.sessionId = sessionId;
        this.status = SessionStatus.ACTIVE;
        this.callback = callback;
        this.taskStackManager = new TaskStackManager(sessionId);
        this.sessionType = sessionType != null ? sessionType : SessionType.ACTIVE;
        this.lastActiveTime = System.currentTimeMillis();
        this.lastReceiveTime = 0;
        this.lastSendTime = 0;
    }

    /**
     * 获取会话ID
     *
     * @return 会话标识符
     */
    @Override
    public String getId() {
        return sessionId;
    }

    /**
     * 获取会话类型
     *
     * @return 会话类型（ACTIVE/PASSIVE）
     */
    @Override
    public SessionType getSessionType() {
        return sessionType;
    }

    /**
     * 判断是否为默认会话
     *
     * @return true 是默认会话，false 不是
     */
    @Override
    public boolean isDefault() {
        return isDefault;
    }

    /**
     * 设置是否为默认会话
     *
     * @param isDefault true 设为默认会话，false 取消默认
     */
    @Override
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    /**
     * 获取当前会话状态
     *
     * @return 状态 (ACTIVE, PAUSED, DESTROYED)
     */
    @Override
    public SessionStatus getStatus() {
        return status;
    }

    /**
     * 设置会话状态
     *
     * @param newStatus 新状态
     * @return true 状态设置成功，false 设置失败（如已是 DESTROYED 状态或参数为null）
     */
    @Override
    public boolean setStatus(SessionStatus newStatus) {
        if (status == SessionStatus.DESTROYED) {
            return false;
        }
        if (newStatus == null) {
            return false;
        }
        status = newStatus;
        return true;
    }

    /**
     * 发送用户输入数据（使用请求对象）
     *
     * @param request 消息请求对象
     * @return true 发送成功，false 发送失败
     */
    @Override
    public boolean send(SessionRequest request) {
        if (status != SessionStatus.ACTIVE) {
            LOGGER.warn("SessionImpl.send: failed, session not active, sessionId={}, status={}", sessionId, status);
            return false;
        }
        if (request == null) {
            LOGGER.warn("SessionImpl.send: failed, request is null, sessionId={}", sessionId);
            return false;
        }
        String userInputText = request.getUserInputText();
        if (userInputText == null || userInputText.trim().isEmpty()) {
            LOGGER.warn("SessionImpl.send: failed, userInputText is null or empty, sessionId={}", sessionId);
            return false;
        }
        SessionMsgInfo sessionMsgInfo = buildMsgInfo(request);
        String jsonStr = sessionMsgInfo.toString();
        JSONObject jsonToSend = null;
        if (callback != null && callback.getSendPreHandler() != null) {
            try {
                JSONObject json = new JSONObject(jsonStr);
                JSONObject processedJson = callback.getSendPreHandler().handle(json, request.getParams());
                if (processedJson == null) {
                    LOGGER.warn("SessionImpl.send: cancelled by SendPreHandler, sessionId={}", sessionId);
                    return false;
                }
                jsonToSend = processedJson;
            } catch (Exception e) {
                LOGGER.error("SessionImpl.send: SendPreHandler handle error, " +
                        "sessionId={}, error={}", sessionId, e.getMessage());
                return false;
            }
        }
        String message = jsonToSend != null ? jsonToSend.toString() : jsonStr;
        boolean result = MessageProcessor.getInstance().send(message);
        if (!result) {
            LOGGER.error("SessionImpl.send: failed, MessageProcessor.send returned false, sessionId={}", sessionId);
            return false;
        }
        LOGGER.debug("SessionImpl.send: success with SessionRequest, sessionId={}", sessionId);
        lastActiveTime = System.currentTimeMillis();
        lastSendTime = System.currentTimeMillis();
        return true;
    }

    /**
     * 发送GUI任务消息
     *
     * <p>仅支持接管相关操作，其他类型会被忽略并打印warn日志：
     * <ul>
     *   <li>{@link GuiTaskEnum#START_TAKEOVER} - 开始接管</li>
     *   <li>{@link GuiTaskEnum#STOP_TAKEOVER} - 停止接管</li>
     *   <li>{@link GuiTaskEnum#CANCEL_TAKEOVER} - 取消接管</li>
     * </ul>
     *
     * @param guiTask GUI任务类型，仅支持 START_TAKEOVER/STOP_TAKEOVER/CANCEL_TAKEOVER
     * @param params 附加参数，可以为null
     * @return true 发送成功，false 发送失败（如会话非ACTIVE状态、无任务或不支持的任务类型）
     */
    @Override
    public boolean sendGuiTask(GuiTaskEnum guiTask, Map<String, String> params) {
        if (status != SessionStatus.ACTIVE) {
            LOGGER.warn("SessionImpl.sendGuiTask: failed, session not active, " +
                    "sessionId={}, status={}", sessionId, status);
            return false;
        }
        if (guiTask == null) {
            LOGGER.warn("SessionImpl.sendGuiTask: failed, guiTask is null, sessionId={}", sessionId);
            return false;
        }
        if (guiTask != GuiTaskEnum.START_TAKEOVER
                && guiTask != GuiTaskEnum.STOP_TAKEOVER
                && guiTask != GuiTaskEnum.CANCEL_TAKEOVER) {
            LOGGER.warn("SessionImpl.sendGuiTask: ignored, unsupported guiTask={}, sessionId={}", guiTask, sessionId);
            return false;
        }
        if (taskStackManager.getStackDepth() == 0) {
            LOGGER.warn("SessionImpl.sendGuiTask: failed, no active task, sessionId={}", sessionId);
            return false;
        }
        SessionMsgInfo sessionMsgInfo = buildGuiTaskMsgInfo(guiTask, params);
        boolean result = MessageProcessor.getInstance().send(sessionMsgInfo);
        if (!result) {
            LOGGER.error("SessionImpl.sendGuiTask: failed, MessageProcessor.send returned false, " +
                    "sessionId={}", sessionId);
            return false;
        }
        LOGGER.debug("SessionImpl.sendGuiTask: success, sessionId={}, guiTask={}", sessionId, guiTask.getServerType());
        lastActiveTime = System.currentTimeMillis();
        return true;
    }

    /**
     * 停止当前任务
     *
     * <p>用于停止当前会话中正在执行的任务。</p>
     *
     * @return true 发送成功，false 发送失败（如会话非ACTIVE状态或无任务）
     */
    @Override
    public boolean stopTask() {
        if (status != SessionStatus.ACTIVE) {
            LOGGER.warn("SessionImpl.stopTask: failed, session not active, sessionId={}, status={}", sessionId, status);
            return false;
        }
        if (taskStackManager.getStackDepth() == 0) {
            LOGGER.warn("SessionImpl.stopTask: failed, no active task, sessionId={}", sessionId);
            return false;
        }
        SessionMsgInfo sessionMsgInfo = buildStopTaskMsgInfo();
        boolean result = MessageProcessor.getInstance().send(sessionMsgInfo);
         
        LOGGER.debug("SessionImpl.stopTask: success, sessionId={}", sessionId);
        lastActiveTime = System.currentTimeMillis();
        return true;
    }

    /**
     * 停止算力主机任务
     *
     * <p>用于停止当前会话中正在执行的算力主机任务。</p>
     * <p>发送的消息中强制设置 DeviceCategory="3"，DeviceCategoryStr="COMPUTING_HOST"。</p>
     *
     * @return true 发送成功，false 发送失败（如会话非ACTIVE状态或无任务）
     */
    @Override
    public boolean stopOtherDeviceCategoryTask(String deviceCategory) {
        if (status != SessionStatus.ACTIVE) {
            LOGGER.warn("SessionImpl.stopOtherDeviceCategoryTask: failed, session not active, " +
                    "sessionId={}, status={}", sessionId, status);
            return false;
        }
        SessionMsgInfo sessionMsgInfo = buildStopOtherDeviceCategoryMsgInfo(deviceCategory);
        boolean result = MessageProcessor.getInstance().send(sessionMsgInfo);
        if (!result) {
            LOGGER.error("SessionImpl.stopOtherDeviceCategoryTask: failed, " +
                    "MessageProcessor.send returned false, sessionId={}", sessionId);
            return false;
        }
        LOGGER.debug("SessionImpl.stopOtherDeviceCategoryTask: success, sessionId={}", sessionId);
        lastActiveTime = System.currentTimeMillis();
        return true;
    }

    /**
     * 接收服务端响应数据
     *
     * <p>解析JSON数据，设置teamEnvironmentId（如果存在且为空），并触发回调。</p>
     *
     * <p>JSON格式示例：</p>
     * <pre>
     * {
     *   "Header": {
     *     "TeamEnvironmentID": "xxx"
     *   },
     *   "Data": {
     *     "MessageType": "UserOutput",
     *     "StreamStatus": "Completed",
     *     "UserOutputText": "你好！"
     *   }
     * }
     * </pre>
     *
     * @param json JSON格式的响应数据对象
     */
    @Override
    public void receive(JSONObject json) {
        if (!validateReceiveInput(json)) {
            return;
        }
        lastReceiveTime = System.currentTimeMillis();
        updateTeamEnvironmentId(json);
        updateCurrentTaskId(json);
        JSONObject data = json.optJSONObject(JSON_KEY_DATA);
        JSONObject header = json.optJSONObject(JSON_KEY_HEADER);
        JSONObject payload = json.optJSONObject(JSON_KEY_PAYLOAD);
        SessionResponseData responseData = parseResponseData(data, header, payload);
        if (responseData == null) {
            LOGGER.warn("SessionImpl.receive: failed to parse response data, sessionId={}", sessionId);
            return;
        }
        guiTaskHandle(responseData);
        taskEndHandle(responseData);
        if (callback != null) {
            callback.onReceived(0, "", responseData);
            lastActiveTime = System.currentTimeMillis();
            LOGGER.debug("SessionImpl.receive: callback triggered, sessionId={}, userOutputType={}",
                    sessionId, responseData.getOutputType().getDescription());
        } else {
            LOGGER.warn("SessionImpl.receive: callback is null, skip callback, sessionId={}", sessionId);
        }
    }

    /**
     * 验证接收输入参数
     *
     * @param json JSON数据对象
     * @return true 验证通过，false 验证失败
     */
    private boolean validateReceiveInput(JSONObject json) {
        if (callback == null) {
            return false;
        }
        if (json == null) {
            LOGGER.warn("SessionImpl.receive: json is null, sessionId={}", sessionId);
            return false;
        }
        if (!json.has(JSON_KEY_DATA)) {
            LOGGER.warn("SessionImpl.receive: Data field is missing, sessionId={}", sessionId);
            return false;
        }
        return true;
    }

    /**
     * 更新团队环境ID
     *
     * @param json JSON数据对象
     */
    private void updateTeamEnvironmentId(JSONObject json) {
        JSONObject header = json.optJSONObject(JSON_KEY_HEADER);
        if (header == null) {
            return;
        }
        String teamEnvId = header.optString(JSON_KEY_TEAM_ENVIRONMENT_ID);
        if (teamEnvId.isEmpty()) {
            return;
        }
        if (teamEnvironmentId != null && !teamEnvironmentId.isEmpty()) {
            return;
        }
        teamEnvironmentId = teamEnvId;
        LOGGER.debug("SessionImpl.receive: updated teamEnvironmentId, sessionId={}", sessionId);
    }

    /**
     * 更新当前任务ID
     *
     * <p>每次收到消息时调用，如果TaskID有变化才更新。
     * 新任务入栈时自动快照会话级超时配置。</p>
     *
     * @param json JSON数据对象
     */
    private void updateCurrentTaskId(JSONObject json) {
        JSONObject header = json.optJSONObject(JSON_KEY_HEADER);
        if (header == null) {
            return;
        }
        String taskId = header.optString(JSON_KEY_TASK_ID);
        if (taskId.isEmpty()) {
            return;
        }
        taskStackManager.pushTask(taskId);
    }

    /**
     * 解析响应数据
     *
     * <p>使用卫语句模式，按优先级解析：</p>
     * <ol>
     *   <li>ControlList 不为空 → 弹窗类型（最高优先级）</li>
     *   <li>UserOutputObject 为 GUI 任务（code=300001）→ GUI任务类型</li>
     *   <li>UserOutputText 不为空 → 文本类型</li>
     *   <li>UserOutputObject 有效 → 提醒类型</li>
     * </ol>
     *
     * @param data JSON数据对象中的Data字段
     * @param header JSON数据对象中的Header字段，用于获取TaskID
     * @param payload JSON数据对象中的Payload字段，用于获取弹窗控制列表
     * @return SessionResponseData 解析后的响应数据，解析失败返回null
     */
    private SessionResponseData parseResponseData(JSONObject data, JSONObject header, JSONObject payload) {
        if (data == null) {
            return null;
        }
        SessionResponseData responseData = new SessionResponseData();
        if (isDialogType(payload)) {
            parseDialogData(payload, responseData, header);
            return finalizeResponse(responseData);
        }
        if (isGuiTaskObject(data)) {
            parseGuiTaskData(data, responseData, header);
            return finalizeResponse(responseData);
        }
        String userOutputText = data.optString(JSON_KEY_USER_OUTPUT_TEXT, "");
        String userOutputObjectStr = data.optString(JSON_KEY_USER_OUTPUT_OBJECT, "");
        // 当userOutputText和userOutputObject都不为空时，不处理text
        if (!StringUtil.isNullOrEmpty(userOutputText) && !StringUtil.isNullOrEmpty(userOutputObjectStr)) {
            userOutputText = "";
        }
        if (!userOutputText.isEmpty()) {
            parseTextData(data, responseData, header);
            return finalizeResponse(responseData);
        }
        if (!parseNotifyData(data, responseData, header)) {
            LOGGER.warn("SessionImpl.parseResponseData: parseNotifyData failed, sessionId={}", sessionId);
            return null;
        }
        return finalizeResponse(responseData);
    }

    /**
     * 解析通知类型数据
     *
     * @param data JSON数据对象
     * @param responseData 响应数据对象
     * @param header JSON头部对象，用于获取TaskID
     */
    private boolean parseNotifyData(JSONObject data, SessionResponseData responseData, JSONObject header) {
        String userInputObjectStr = data.optString(JSON_KEY_USER_OUTPUT_OBJECT, "");
        if (userInputObjectStr.isEmpty()) {
            LOGGER.warn("SessionImpl.parseNotifyData: UserInputObject is empty, sessionId={}", sessionId);
            return false;
        }
        responseData.setOutputType(OutputTypeEnum.NOTIFY);
        NotifyInfo notifyInfo = new NotifyInfo();
        notifyInfo.setUserOutputObjectRawString(userInputObjectStr);
        try {
            JSONObject userInputObject = new JSONObject(userInputObjectStr);
            notifyInfo.setCode(userInputObject.optInt(JSON_KEY_NOTIFY_CODE, 0));
        } catch (Exception e) {
            LOGGER.error("SessionImpl.parseNotifyData: failed to parse UserInputObject, " +
                    "sessionId={}, error={}", sessionId, e.getMessage());
        }
        responseData.setNotifyInfo(notifyInfo);
        responseData.setTaskInfo(buildDefaultTaskInfo(header));
        return true;
    }

    /**
     * 解析文本类型数据
     *
     * <p>当 UserOutputObject 同时存在时，额外构建 NotifyInfo 附加到响应数据中。</p>
     *
     * @param data JSON数据对象
     * @param responseData 响应数据对象
     * @param header JSON头部对象，用于获取TaskID
     */
    private void parseTextData(JSONObject data, SessionResponseData responseData, JSONObject header) {
        responseData.setOutputType(OutputTypeEnum.TEXT);
        TextInfo textInfo = new TextInfo();
        textInfo.setText(data.optString(JSON_KEY_USER_OUTPUT_TEXT, ""));
        String streamStatus = data.optString(JSON_KEY_STREAM_STATUS, "");
        textInfo.setStreamFinishFlag(STREAM_STATUS_COMPLETED.equals(streamStatus));
        textInfo.setVoiceBroadcast(data.optInt(JSON_KEY_IS_VOICE_BROADCAST, 0) == 1);
        responseData.setTextInfo(textInfo);
        responseData.setTaskInfo(buildDefaultTaskInfo(header));
        String userOutputObjectStr = data.optString(JSON_KEY_USER_OUTPUT_OBJECT, "");
        if (!userOutputObjectStr.isEmpty()) {
            NotifyInfo notifyInfo = new NotifyInfo();
            notifyInfo.setUserOutputObjectRawString(userOutputObjectStr);
            try {
                JSONObject userOutputObject = new JSONObject(userOutputObjectStr);
                notifyInfo.setCode(userOutputObject.optInt(JSON_KEY_NOTIFY_CODE, 0));
            } catch (Exception e) {
                LOGGER.error("SessionImpl.parseTextData: failed to parse UserOutputObject, " +
                        "sessionId={}, error={}", sessionId, e.getMessage());
            }
            responseData.setNotifyInfo(notifyInfo);
        }
    }

    /**
     * 判断UserOutputObject是否为GUI任务
     *
     * <p>通过检查code字段是否为300001来判断</p>
     *
     * @param data JSON数据对象
     * @return true 是GUI任务，false 不是GUI任务
     */
    private boolean isGuiTaskObject(JSONObject data) {
        String objStr = data.optString(JSON_KEY_USER_OUTPUT_OBJECT, "");
        if (objStr.isEmpty()) {
            return false;
        }
        try {
            JSONObject obj = new JSONObject(objStr);
            int code = obj.optInt("code", 0);
            return code == GUI_TASK_CODE;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查是否为弹窗类型
     *
     * @param payload JSON数据对象中的Payload字段
     * @return true 是弹窗类型，false 不是
     */
    private boolean isDialogType(JSONObject payload) {
        return payload != null && payload.has(JSON_KEY_CONTROL_LIST);
    }

    /**
     * 完成响应数据解析，设置公共字段
     *
     * @param responseData 响应数据对象
     * @return 设置完成后的响应数据对象
     */
    private SessionResponseData finalizeResponse(SessionResponseData responseData) {
        TaskInfo taskInfo = responseData.getTaskInfo();
        if (taskInfo != null) {
            taskInfo.setGuiTaskRunning(guiTaskRunning);
        }
        return responseData;
    }

    /**
     * 解析GUI任务数据
     *
     * <p>解析包含code=300001的UserOutputObject，根据data.type设置GUI任务类型。
     * 同时解析UserOutputText字段，放入GuiTaskInfo中。</p>
     *
     * @param data JSON数据对象
     * @param responseData 响应数据对象
     * @param header JSON头部对象，用于获取TaskID
     */
    private void parseGuiTaskData(JSONObject data, SessionResponseData responseData, JSONObject header) {
        responseData.setOutputType(OutputTypeEnum.GUI_TASK);
        String objStr = data.optString(JSON_KEY_USER_OUTPUT_OBJECT, "");
        GuiTaskInfo guiTaskInfo = new GuiTaskInfo();
        String userOutputText = data.optString(JSON_KEY_USER_OUTPUT_TEXT, "");
        if (!userOutputText.isEmpty()) {
            guiTaskInfo.setText(userOutputText);
        }
        try {
            JSONObject obj = new JSONObject(objStr);
            JSONObject innerData = obj.optJSONObject("data");
            if (innerData != null) {
                String type = innerData.optString("type", "");
                GuiTaskEnum guiTaskType = GuiTaskEnum.fromType(type);
                if (guiTaskType != null) {
                    guiTaskInfo.setGuiTaskType(guiTaskType);
                    if (guiTaskType == GuiTaskEnum.START_GUI) {
                        guiTaskRunning = true;
                    } else if (guiTaskType == GuiTaskEnum.STOP_GUI) {
                        guiTaskRunning = false;
                    }
                } else {
                    LOGGER.warn("SessionImpl.parseGuiTaskData: unknown GUI task type '{}', " +
                            "sessionId={}", type, sessionId);
                }
            } else {
                LOGGER.warn("SessionImpl.parseGuiTaskData: data field is null, sessionId={}", sessionId);
            }
        } catch (Exception e) {
            LOGGER.warn("SessionImpl.parseGuiTaskData: failed to parse GUI task object, " +
                    "sessionId={}, error={}", sessionId, e.getMessage());
        }
        responseData.setGuiTaskInfo(guiTaskInfo);
        responseData.setTaskInfo(buildDefaultTaskInfo(header));
    }

    /**
     * 解析弹窗数据
     *
     * <p>解析包含 ControlList 的 Payload，构建弹窗信息</p>
     *
     * @param payload JSON数据对象中的Payload字段
     * @param responseData 响应数据对象
     * @param header JSON头部对象，用于获取TaskID
     */
    private void parseDialogData(JSONObject payload, SessionResponseData responseData, JSONObject header) {
        responseData.setOutputType(OutputTypeEnum.DIALOG);
        DialogInfo dialogInfo = new DialogInfo();
        dialogInfo.setTitle(payload.optString(JSON_KEY_TITLE, ""));
        org.json.JSONArray controlListArray = payload.optJSONArray(JSON_KEY_CONTROL_LIST);
        if (controlListArray != null) {
            java.util.List<DialogControl> controlList = new java.util.ArrayList<>();
            for (int i = 0; i < controlListArray.length(); i++) {
                org.json.JSONObject controlJson = controlListArray.optJSONObject(i);
                if (controlJson != null) {
                    DialogControl control = new DialogControl();
                    control.setContent(controlJson.optString(JSON_KEY_CONTENT, ""));
                    control.setType(controlJson.optString(JSON_KEY_TYPE, ""));
                    controlList.add(control);
                }
            }
            dialogInfo.setControlList(controlList);
        }
        responseData.setDialogInfo(dialogInfo);
        responseData.setTaskInfo(buildDefaultTaskInfo(header));
        LOGGER.debug("SessionImpl.parseDialogData: dialog parsed successfully, " +
                "sessionId={}, title={}, controlCount={}", sessionId,
                dialogInfo.getTitle(), dialogInfo.getControlList().size());
    }

    /**
     * 构建默认 TaskInfo
     *
     * @param header JSON头部对象
     * @return TaskInfo 包含默认值的TaskInfo对象
     */
    private TaskInfo buildDefaultTaskInfo(JSONObject header) {
        TaskInfo taskInfo = new TaskInfo();
        taskInfo.setTaskCompleted(false);
        taskInfo.setTaskStatusCode(200);
        if (header != null) {
            taskInfo.setTaskId(header.optString(JSON_KEY_TASK_ID, ""));
        }
        return taskInfo;
    }

    /**
     * 构建消息对象（使用 SessionRequest）
     *
     * @param request 消息请求对象
     * @return SessionMsgInfo 构建的消息对象
     */
    private SessionMsgInfo buildMsgInfo(SessionRequest request) {
        SessionMsgInfo sessionMsgInfo = new SessionMsgInfo();
        sessionMsgInfo.getHeader().setClientSessionId(sessionId);
        setupMessageType(sessionMsgInfo);
        setupTeamEnvironment(sessionMsgInfo);
        fillUserData(sessionMsgInfo, request);
        setupUserInputObject(sessionMsgInfo, request);
        return sessionMsgInfo;
    }

    private void setupMessageType(SessionMsgInfo sessionMsgInfo) {
        if (taskStackManager.getStackDepth() == 0) {
            sessionMsgInfo.getHeader().setMessageType(HEADER_MSG_TASK_ASSIGNMENT);
            sessionMsgInfo.getData().setMessageType(DATA_MSG_TASK_ASSIGNMENT);
        } else {
            sessionMsgInfo.getHeader().setMessageType(HEADER_MSG_DATA_EXCHANGE);
            sessionMsgInfo.getData().setMessageType(DATA_MSG_USER_INPUT);
            String currentTaskId = taskStackManager.getCurrentTaskId();
            if (currentTaskId != null && !currentTaskId.isEmpty()) {
                sessionMsgInfo.getHeader().setTaskId(currentTaskId);
            }
        }
    }

    private void setupTeamEnvironment(SessionMsgInfo sessionMsgInfo) {
        if (teamEnvironmentId != null && !teamEnvironmentId.isEmpty()) {
            sessionMsgInfo.getHeader().setTeamEnvironmentId(teamEnvironmentId);
        }
    }

    private void fillUserData(SessionMsgInfo sessionMsgInfo, SessionRequest request) {
        sessionMsgInfo.getData().setUserInputText(request.getUserInputText());
        String userSpecifiedAgent = request.getUserSpecifiedAgent();
        if (userSpecifiedAgent == null || userSpecifiedAgent.isEmpty()) {
            userSpecifiedAgent = DEFAULT_USER_SPECIFIED_AGENT;
        }
        sessionMsgInfo.getData().setUserSpecifiedAgent(userSpecifiedAgent);
        List<String> userIntent = request.getUserIntent();
        if (userIntent != null && !userIntent.isEmpty()) {
            sessionMsgInfo.getData().setUserIntent(userIntent);
        }
        String voiceId = request.getVoiceId();
        if (voiceId != null && !voiceId.isEmpty()) {
            sessionMsgInfo.getData().setVoiceId(voiceId);
        }
    }

    private void setupUserInputObject(SessionMsgInfo sessionMsgInfo, SessionRequest request) {
        String rawString = request.getUserInputObjectRawString();
        if (rawString != null && !rawString.isEmpty()) {
            sessionMsgInfo.getData().setUserInputObject(rawString);
            return;
        }
        OutputPersonality personality = request.getOutputPersonality();
        if (personality != null && !personality.isEmpty()) {
            buildPersonalityObject(sessionMsgInfo, personality);
            return;
        }
        int code = request.getUserInputObjectCode();
        Object data = request.getUserInputObjectData();
        if (code != -1 && data != null) {
            buildCodeDataObject(sessionMsgInfo, code, data);
        }
    }

    private void buildPersonalityObject(SessionMsgInfo sessionMsgInfo,
            OutputPersonality personality) {
        try {
            JSONObject dataObj = new JSONObject();
            if (personality.getEmotion() != null) {
                dataObj.put("emotion", personality.getEmotion());
            }
            if (personality.getAge() != null) {
                dataObj.put("age", personality.getAge());
            }
            if (personality.getGender() != null) {
                dataObj.put("gender", personality.getGender());
            }
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("code", 210000);
            jsonObject.put("data", dataObj);
            sessionMsgInfo.getData().setUserInputObject(jsonObject.toString());
        } catch (Exception e) {
            LOGGER.error("SessionImpl.buildMsgInfo: failed to build personality UserInputObject, " +
                    "sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    private void buildCodeDataObject(SessionMsgInfo sessionMsgInfo, int code, Object data) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("code", code);
            jsonObject.put("data", data);
            sessionMsgInfo.getData().setUserInputObject(jsonObject.toString());
        } catch (Exception e) {
            LOGGER.error("SessionImpl.buildMsgInfo: failed to build UserInputObject, " +
                    "sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 构建GUI任务消息对象
     *
     * @param guiTask GUI任务类型
     * @param params 附加参数
     * @return SessionMsgInfo 构建的消息对象
     */
    private SessionMsgInfo buildGuiTaskMsgInfo(GuiTaskEnum guiTask, Map<String, String> params) {
        SessionMsgInfo sessionMsgInfo = new SessionMsgInfo();
        sessionMsgInfo.getHeader().setClientSessionId(sessionId);
        sessionMsgInfo.getHeader().setMessageType(HEADER_MSG_DATA_EXCHANGE);
        sessionMsgInfo.getData().setMessageType(DATA_MSG_USER_INPUT);
        String currentTaskId = taskStackManager.getCurrentTaskId();
        if (currentTaskId != null && !currentTaskId.isEmpty()) {
            sessionMsgInfo.getHeader().setTaskId(currentTaskId);
        }
        if (teamEnvironmentId != null && !teamEnvironmentId.isEmpty()) {
            sessionMsgInfo.getHeader().setTeamEnvironmentId(teamEnvironmentId);
        }
        sessionMsgInfo.getData().setUserInputText("");
        sessionMsgInfo.getData().setUserSpecifiedAgent(DEFAULT_USER_SPECIFIED_AGENT);
        // 构建 UserInputObject
        try {
            JSONObject userInputObject = new JSONObject();
            userInputObject.put("code", GUI_TASK_CODE);
            JSONObject innerData = new JSONObject();
            if (params != null && !params.isEmpty()) {
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    innerData.put(entry.getKey(), entry.getValue());
                }
            }
            innerData.put("type", guiTask.getServerType());
            userInputObject.put("data", innerData);
            sessionMsgInfo.getData().setUserInputObject(userInputObject.toString());
        } catch (Exception e) {
            LOGGER.error("SessionImpl.buildGuiTaskMsgInfo: failed to build UserInputObject, " +
                    "sessionId={}, error={}", sessionId, e.getMessage());
        }
        return sessionMsgInfo;
    }

    /**
     * 构建停止任务消息对象
     *
     * @return SessionMsgInfo 构建的消息对象
     */
    private SessionMsgInfo buildStopTaskMsgInfo() {
        SessionMsgInfo sessionMsgInfo = new SessionMsgInfo();
        sessionMsgInfo.getHeader().setClientSessionId(sessionId);
        sessionMsgInfo.getHeader().setMessageType(HEADER_MSG_TASK_ASSIGNMENT);
        sessionMsgInfo.getData().setMessageType(DATA_MSG_TASK_STOP);
        String currentTaskId = taskStackManager.getCurrentTaskId();
        if (currentTaskId != null && !currentTaskId.isEmpty()) {
            sessionMsgInfo.getHeader().setTaskId(currentTaskId);
        }
        if (teamEnvironmentId != null && !teamEnvironmentId.isEmpty()) {
            sessionMsgInfo.getHeader().setTeamEnvironmentId(teamEnvironmentId);
        }
        return sessionMsgInfo;
    }

    /**
     * 构建停止算力主机任务消息对象
     *
     * <p>与 buildStopTaskMsgInfo 的区别：
     * <ul>
     *   <li>强制设置 DeviceCategory = "3" (算力主机)</li>
     *   <li>设置 DeviceID</li>
     * </ul>
     *
     * @return SessionMsgInfo 构建的消息对象
     */
    private SessionMsgInfo buildStopOtherDeviceCategoryMsgInfo(String deviceCategory) {
        SessionMsgInfo sessionMsgInfo = new SessionMsgInfo();
        sessionMsgInfo.getHeader().setClientSessionId(sessionId);
        sessionMsgInfo.getHeader().setMessageType(HEADER_MSG_TASK_ASSIGNMENT);
        sessionMsgInfo.getData().setMessageType(DATA_MSG_TASK_STOP);
        // 强制设置为算力主机类型
        sessionMsgInfo.getHeader().setDeviceCategory(deviceCategory);

        String deviceId = String.format("1.%s.%s@AISF.cu.gd.com.cn",
                deviceCategory,
                sessionMsgInfo.getHeader().getUserName());
        sessionMsgInfo.getHeader().setDeviceId(deviceId);
        return sessionMsgInfo;
    }

    /**
     * 接收已处理的响应数据并触发回调
     *
     * @param data 已处理的响应数据对象
     * @return true 成功触发回调，false 失败（如会话无回调）
     */
    @Override
    public boolean receive(SessionResponseData data) {
        if (callback == null) {
            LOGGER.warn("SessionImpl.receive: callback is null, sessionId={}", sessionId);
            return false;
        }
        lastReceiveTime = System.currentTimeMillis();
        lastActiveTime = System.currentTimeMillis();
        taskEndHandle(data);
        callback.onReceived(0, "", data);
        LOGGER.debug("SessionImpl.receive: callback triggered, sessionId={}", sessionId);
        return true;
    }

    /**
     * 处理GUI任务事件
     *
     * <p>当收到 START_GUI 或 STOP_GUI 事件时，控制截图服务的启动和停止。</p>
     *
     * @param responseData 响应数据
     */
    private void guiTaskHandle(SessionResponseData responseData) {
        if (responseData.getOutputType() != OutputTypeEnum.GUI_TASK) {
            return;
        }
        GuiTaskInfo guiTaskInfo = responseData.getGuiTaskInfo();
        if (guiTaskInfo == null) {
            return;
        }
        ScreenshotManager screenshotManager = AndroidFuncManager.getInstance().getScreenshotManager();
        if (screenshotManager == null) {
            LOGGER.warn("SessionImpl.guiTaskHandle: ScreenshotManager is null, sessionId={}", sessionId);
            return;
        }
        GuiTaskEnum guiTaskType = guiTaskInfo.getGuiTaskType();
        if (guiTaskType == GuiTaskEnum.START_GUI) {
            LOGGER.info("SessionImpl.guiTaskHandle: START_GUI, registering callback and starting " +
                    "screenshot service, sessionId={}", sessionId);
            try {
                com.huawei.aisfsdkv2.component.gui.GuiStepLatencyTracker.getInstance()
                        .reset("START_GUI");
            } catch (Throwable ignored) {
                // ignore
            }
            
            // 注册服务就绪回调（解决 Netty 异步延迟问题）
            MediaScreenshotService.registerServiceReadyCallback(new MediaScreenshotService.ServiceReadyCallback() {
                @Override
                public void onServiceReady(MediaScreenshotService service) {
                    LOGGER.info("SessionImpl: Screenshot service ready callback received, sessionId={}", sessionId);
                    // 服务就绪后，后续逻辑将由消息处理器继续处理
                    // 此处主要用于确保服务已启动，避免后续流程中断
                }

                @Override
                public void onServiceFailed(String error) {
                    LOGGER.error("SessionImpl: Screenshot service failed callback received, " +
                            "sessionId={}, error={}", sessionId, error);
                }
            });
            
            // 启动截图服务（异步）
            screenshotManager.startScreenshotService();
            
        } else if (guiTaskType == GuiTaskEnum.STOP_GUI) {
            LOGGER.info("SessionImpl.guiTaskHandle: STOP_GUI, stopping screenshot service, sessionId={}", sessionId);
            try {
                com.huawei.aisfsdkv2.component.gui.GuiStepLatencyTracker.getInstance()
                        .snapshotAndNotify("STOP_GUI");
            } catch (Throwable ignored) {
                // ignore
            }
            screenshotManager.stopScreenshotService();
        }
    }

    /**
     * 处理任务结束
     *
     * <p>判断是否为任务结束场景，如果是则从任务栈中移除该任务及其后代。</p>
     * <p>结束场景处理逻辑：</p>
     * <ol>
     *   <li>GUI任务停止（STOP_GUI）：判断后执行出栈</li>
     *   <li>任务状态报告完成（TASK_STATUS且completed=true）：判断后执行出栈</li>
     * </ol>
     *
     * @param responseData 响应数据
     */
    private void taskEndHandle(SessionResponseData responseData) {
        if (responseData.getOutputType() == OutputTypeEnum.GUI_TASK) {
            GuiTaskInfo guiTaskInfo = responseData.getGuiTaskInfo();
            if (guiTaskInfo != null && guiTaskInfo.getGuiTaskType() == GuiTaskEnum.STOP_GUI) {
                TaskInfo taskInfo = responseData.getTaskInfo();
                String taskId = taskInfo != null ? taskInfo.getTaskId() : null;
                if (taskId != null && !taskId.isEmpty()) {
                    taskStackManager.popTask(taskId);
                }
                return;
            }
        }
        if (responseData.getOutputType() == OutputTypeEnum.TASK_STATUS) {
            TaskInfo taskInfo = responseData.getTaskInfo();
            if (taskInfo != null && taskInfo.isTaskCompleted()) {
                String taskId = taskInfo.getTaskId();
                if (taskId != null && !taskId.isEmpty()) {
                    taskStackManager.popTask(taskId);
                }
                if (taskStackManager.getStackDepth() == 0) {
                    lastSendTime = 0;
                }
                if (isTemporary) {
                    LOGGER.info("SessionImpl.taskEndHandle: temporary session auto-destroy after " +
                            "task completed, sessionId={}", sessionId);
                    SessionManager.getInstance().destroySession(sessionId);
                }
            }
        }
    }

    /**
     * 获取当前任务ID
     *
     * @return 当前任务ID，可能为null
     */
    @Override
    public String getCurrentTaskId() {
        return taskStackManager.getCurrentTaskId();
    }

    /**
     * 获取团队环境ID
     *
     * @return 团队环境ID，未设置时返回null
     */
    @Override
    public String getTeamEnvironmentId() {
        return teamEnvironmentId;
    }

    /**
     * 设置会话老化配置
     *
     * @param config 老化配置，不能为null
     */
    @Override
    public void setAgingConfig(SessionAgingConfig config) {
        if (config == null) {
            LOGGER.warn("SessionImpl.setAgingConfig: config is null, sessionId={}", sessionId);
            return;
        }
        this.agingConfig = config;
    }

    /**
     * 获取老化配置
     *
     * @return 老化配置，未设置时返回null
     */
    @Override
    public SessionAgingConfig getAgingConfig() {
        return agingConfig;
    }

    /**
     * 获取最后活跃时间
     *
     * @return 最后活跃时间戳（毫秒）
     */
    @Override
    public long getLastActiveTime() {
        return lastActiveTime;
    }

    @Override
    public void setStreamResponseTimeoutProperty(StreamResponseTimeoutProperty property) {
        this.streamResponseTimeoutProperty = property;
        LOGGER.info("SessionImpl.setStreamResponseTimeoutProperty: sessionId={}, property={}", sessionId, property);
    }

    @Override
    public StreamResponseTimeoutProperty getStreamResponseTimeoutProperty() {
        return streamResponseTimeoutProperty;
    }

    @Override
    public long getLastReceiveTime() {
        return lastReceiveTime;
    }

    @Override
    public long getLastSendTime() {
        return lastSendTime;
    }

    /**
     * 获取会话回调接口
     *
     * <p>StreamResponseTimeoutManager 通过此方法获取回调，用于在超时时调用 onReceived。</p>
     *
     * @return 会话回调接口，可能为null
     */
    public SessionCallback getCallback() {
        return callback;
    }

    /**
     * 判断是否为临时会话
     *
     * <p>临时会话在收到 taskCompleted 消息且任务栈为空时，会自动销毁。</p>
     *
     * @return true 是临时会话，false 是普通会话
     * @since 2026-05-19
     */
    @Override
    public boolean isTemporary() {
        return isTemporary;
    }

    /**
     * 设置是否为临时会话
     *
     * <p>临时会话在收到 taskCompleted 消息且任务栈为空时，会自动销毁。</p>
     *
     * @param isTemporary true 设为临时会话，false 设为普通会话
     * @since 2026-05-19
     */
    @Override
    public void setTemporary(boolean isTemporary) {
        this.isTemporary = isTemporary;
    }

    /**
     * 获取任务栈（从顶层到当前层）
     *
     * @return 任务ID列表
     */
    public java.util.List<String> getTaskStack() {
        return taskStackManager.getTaskStack();
    }

    /**
     * 获取任务栈深度
     *
     * @return 0=无任务，1=只有顶层任务，以此类推
     */
    public int getTaskStackDepth() {
        return taskStackManager.getStackDepth();
    }

    /**
     * 获取会话属性
     *
     * <p>根据属性键获取对应的值。</p>
     *
     * @param key 属性键，不能为null或空
     * @return 属性值，属性不存在或参数非法返回null
     * @since 2026-04-21
     */
    @Override
    public String getAttribute(String key) {
        if (key == null || key.trim().isEmpty()) {
            LOGGER.warn("SessionImpl.getAttribute: failed, key is null or empty, sessionId={}", sessionId);
            return null;
        }
        return attributes.get(key);
    }

    /**
     * 设置会话属性
     *
     * <p>为当前会话添加或更新一个属性键值对。会话处于 DESTROYED 状态时拒绝操作。</p>
     *
     * @param key 属性键，不能为null或空
     * @param value 属性值，不能为null
     * @return true 设置成功，false 参数非法或会话已销毁
     * @since 2026-04-21
     */
    @Override
    public boolean setAttribute(String key, String value) {
        if (status == SessionStatus.DESTROYED) {
            LOGGER.warn("SessionImpl.setAttribute: failed, session destroyed, sessionId={}", sessionId);
            return false;
        }
        if (key == null || key.trim().isEmpty()) {
            LOGGER.warn("SessionImpl.setAttribute: failed, key is null or empty, sessionId={}", sessionId);
            return false;
        }
        if (value == null) {
            LOGGER.warn("SessionImpl.setAttribute: failed, value is null, sessionId={}", sessionId);
            return false;
        }
        attributes.put(key, value);
        return true;
    }

    /**
     * 删除会话属性
     *
     * <p>移除指定键的属性。会话处于 DESTROYED 状态时拒绝操作。</p>
     *
     * @param key 属性键，不能为null或空
     * @return true 删除成功，false 属性不存在、参数非法或会话已销毁
     * @since 2026-04-21
     */
    @Override
    public boolean removeAttribute(String key) {
        if (status == SessionStatus.DESTROYED) {
            LOGGER.warn("SessionImpl.removeAttribute: failed, session destroyed, sessionId={}", sessionId);
            return false;
        }
        if (key == null || key.trim().isEmpty()) {
            LOGGER.warn("SessionImpl.removeAttribute: failed, key is null or empty, sessionId={}", sessionId);
            return false;
        }
        return attributes.remove(key) != null;
    }
}
