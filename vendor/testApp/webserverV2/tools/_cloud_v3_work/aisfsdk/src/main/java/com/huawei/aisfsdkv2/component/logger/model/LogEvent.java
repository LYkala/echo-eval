/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logger.model;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.log.LogLevelEnum;
import com.huawei.aisfsdkv2.api.login.LoginApi;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * 日志事件对象，封装了SDK日志的完整信息。
 * <p>
 * 包含用户信息、时间戳、日志级别、任务信息以及详细的事件消息内容。
 * 可用于构建标准化的日志记录，支持转换为JSON格式进行传输或存储。
 * </p>
 *
 * @author l00504561
 * @since 2026-02-07
 */
public class LogEvent {
    /** 用户ID，标识当前登录用户 */
    private final String userId;

    /** 时间戳，ISO 8601格式 */
    private String timestamp;

    /** 日志级别，标识日志的严重程度 */
    private final LogLevelEnum logLevel;

    /** 任务ID，用于标识关联的任务 */
    private final String taskId;

    /** 日志事件消息对象，包含详细的事件信息 */
    private final LogEventMessage logEventMessage;

    /**
     * 构造日志事件对象。
     * <p>
     * 自动获取当前登录用户信息、当前时间戳，并创建事件消息对象。
     * </p>
     *
     * @param logLevel 日志级别，不能为空
     * @param tag 事件标签，用于构建事件名称
     * @param message 详细的消息内容
     */
    public LogEvent(LogLevelEnum logLevel, String tag, String message) {
        this(logLevel, tag, message, "");
    }

    /**
     * 构造日志事件对象（带任务ID）。
     * <p>
     * 自动获取当前登录用户信息、当前时间戳，并创建事件消息对象。
     * </p>
     *
     * @param logLevel 日志级别，不能为空
     * @param tag 事件标签，用于构建事件名称
     * @param message 详细的消息内容
     * @param taskId 关联的任务ID，无任务时传空字符串
     */
    public LogEvent(LogLevelEnum logLevel, String tag, String message, String taskId) {
        LoginApi loginApi = AISFSDKClient.getLoginApi();
        this.userId = (loginApi != null) ? loginApi.getLoginInfo().getUsername() : "";
        this.timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        this.logLevel = logLevel;
        this.taskId = taskId != null ? taskId : "";
        this.logEventMessage = new LogEventMessage(tag, message);
    }

    /**
     * 获取用户ID。
     *
     * @return 用户ID，如果未登录则返回空字符串
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 获取时间戳。
     *
     * @return ISO 8601格式的时间戳字符串
     */
    public String getTimestamp() {
        return timestamp;
    }

    /**
     * 设置时间戳。
     *
     * @param timestamp ISO 8601格式的时间戳字符串
     */
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 获取日志级别。
     *
     * @return 日志级别枚举值
     */
    public LogLevelEnum getLogLevel() {
        return logLevel;
    }

    /**
     * 获取任务ID。
     *
     * @return 任务ID字符串
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * 获取日志事件消息对象。
     *
     * @return LogEventMessage对象，包含详细的事件信息
     */
    public LogEventMessage getLogEventMessage() {
        return logEventMessage;
    }

    /**
     * 将日志事件转换为JSON字符串。
     * <p>
     * 生成的JSON包含以下字段：
     * <ul>
     *   <li>UserID - 用户ID</li>
     *   <li>TimeStamp - 时间戳</li>
     *   <li>LogLevel - 日志级别名称</li>
     *   <li>EventType - 事件类型（固定为"EM"）</li>
     *   <li>TaskId - 任务ID</li>
     *   <li>TaskIdr - 任务ID（重复字段）</li>
     *   <li>Message - 包含服务名、事件名、详情和状态的对象</li>
     * </ul>
     * </p>
     *
     * @return JSON格式的字符串表示
     * @throws RuntimeException 如果JSON序列化过程中发生错误
     */
    public String toJSONString() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("UserID", this.userId);
            jsonObject.put("TimeStamp", this.timestamp);
            jsonObject.put("LogLevel", this.logLevel.name());
            jsonObject.put("EventType", "EM");
            jsonObject.put("TaskId", this.taskId);
            jsonObject.put("TaskIdr", this.taskId);
            JSONObject messageJsonObject = new JSONObject();
            messageJsonObject.put("ServiceName", this.logEventMessage.getServiceName());
            messageJsonObject.put("EventName", this.logEventMessage.getEventName());
            messageJsonObject.put("Details", this.logEventMessage.getDetail());
            messageJsonObject.put("Status", this.logEventMessage.getStatus());
            jsonObject.put("Message", messageJsonObject);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return jsonObject.toString();
    }
}
