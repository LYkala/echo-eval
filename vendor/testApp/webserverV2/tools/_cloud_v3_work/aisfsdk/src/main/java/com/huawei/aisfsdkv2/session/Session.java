/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.session;

import com.huawei.aisfsdkv2.api.session.GuiTaskEnum;
import com.huawei.aisfsdkv2.api.session.SessionAgingConfig;
import com.huawei.aisfsdkv2.api.session.SessionRequest;
import com.huawei.aisfsdkv2.api.session.SessionResponseData;
import com.huawei.aisfsdkv2.api.session.SessionStatus;
import com.huawei.aisfsdkv2.api.session.StreamResponseTimeoutProperty;

import org.json.JSONObject;

import java.util.Map;

/**
 * 会话接口
 * 定义会话的基本能力和数据发送
 */
public interface Session {
    /**
     * 获取会话ID
     * @return 会话标识符
     */
    String getId();

    /**
     * 获取会话类型
     *
     * @return 会话类型（ACTIVE/PASSIVE）
     */
    SessionType getSessionType();

    /**
     * 获取当前会话状态
     * @return 状态 (ACTIVE, PAUSED, DESTROYED)
     */
    SessionStatus getStatus();

    /**
     * 设置会话状态
     * @param status 新状态
     * @return true 状态设置成功，false 设置失败（如已是 DESTROYED 状态或非法状态转换）
     */
    boolean setStatus(SessionStatus status);

    /**
     * 发送用户输入数据（使用请求对象）
     *
     * <p>支持更丰富的消息参数，包括用户意图、指定代理、语音ID等。</p>
     *
     * @param request 消息请求对象
     * @return true 发送成功，false 发送失败（如会话非 ACTIVE 状态或请求为 null）
     * @since 2026-04-13
     */
    boolean send(SessionRequest request);

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
    boolean sendGuiTask(GuiTaskEnum guiTask, Map<String, String> params);

    /**
     * 停止当前任务
     *
     * <p>用于停止当前会话中正在执行的任务。</p>
     *
     * @return true 发送成功，false 发送失败（如会话非ACTIVE状态或无任务）
     */
    boolean stopTask();

    /**
     * 停止算力主机任务
     *
     * <p>用于停止当前会话中正在执行的算力主机任务。</p>
     * <p>发送的消息中强制设置 DeviceCategory="3"，DeviceCategoryStr="COMPUTING_HOST"。</p>
     *
     * @return true 发送成功，false 发送失败（如会话非ACTIVE状态或无任务）
     */
    boolean stopOtherDeviceCategoryTask(String deviceCategory);

    /**
     * 接收服务端响应数据
     *
     * <p>解析JSON数据并触发回调。</p>
     *
     * @param json JSON格式的响应数据对象
     */
    void receive(JSONObject json);

    /**
     * 接收已处理的响应数据并触发回调
     *
     * @param data 已处理的响应数据对象
     * @return true 成功触发回调，false 失败（如会话无回调）
     */
    boolean receive(SessionResponseData data);

    /**
     * 获取会话属性
     *
     * <p>根据属性键获取对应的值。</p>
     *
     * @param key 属性键，不能为null或空
     * @return 属性值，属性不存在或参数非法返回null
     * @since 2026-04-21
     */
    String getAttribute(String key);

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
    boolean setAttribute(String key, String value);

    /**
     * 删除会话属性
     *
     * <p>移除指定键的属性。会话处于 DESTROYED 状态时拒绝操作。</p>
     *
     * @param key 属性键，不能为null或空
     * @return true 删除成功，false 属性不存在、参数非法或会话已销毁
     * @since 2026-04-21
     */
    boolean removeAttribute(String key);

    /**
     * 判断是否为默认会话
     *
     * @return true 是默认会话，false 不是
     */
    boolean isDefault();

    /**
     * 设置是否为默认会话
     *
     * @param isDefault true 设为默认会话，false 取消默认
     */
    void setDefault(boolean isDefault);

    /**
     * 获取当前任务ID
     *
     * @return 当前任务ID，无任务时返回null
     */
    String getCurrentTaskId();

    /**
     * 获取团队环境ID
     *
     * @return 团队环境ID，未设置时返回null
     */
    String getTeamEnvironmentId();

    /**
     * 设置会话老化配置
     *
     * <p>当会话空闲时间超过配置的最大空闲时间时，自动销毁该会话。
     * 默认不启用老化（{@link SessionAgingConfig#NEVER_AGING}）。</p>
     *
     * <p>老化判定基于最后一次消息收发时间，包括 send 和 receive 操作。</p>
     *
     * @param config 老化配置，不能为null
     * @since 2026-04-22
     */
    void setAgingConfig(SessionAgingConfig config);

    /**
     * 获取会话老化配置
     *
     * @return 老化配置，未设置时返回null
     * @since 2026-04-22
     */
    SessionAgingConfig getAgingConfig();

    /**
     * 获取最后活跃时间
     *
     * <p>最后一次消息收发（send/receive）的时间戳。</p>
     *
     * @return 最后活跃时间戳（毫秒），会话创建时初始化
     * @since 2026-04-22
     */
    long getLastActiveTime();

    /**
     * 设置流式响应超时属性
     *
     * <p>设置两条响应消息之间的最大间隔超时时间。
     * 超时后通过 {@link SessionCallback#onReceived} 回调通知用户，
     * 不会销毁会话。</p>
     *
     * <p>{@link StreamResponseTimeoutProperty#getTimeoutMillis()} = -1 表示不启用超时检查（默认），
     * 正值表示超时毫秒数。</p>
     *
     * @param property 流式响应超时属性，null表示不启用超时检查
     * @since 2026-06-18
     */
    void setStreamResponseTimeoutProperty(StreamResponseTimeoutProperty property);

    /**
     * 获取流式响应超时属性
     *
     * @return 流式响应超时属性，未设置时返回null
     * @since 2026-06-18
     */
    StreamResponseTimeoutProperty getStreamResponseTimeoutProperty();

    /**
     * 获取最后一次接收响应的时间
     *
     * <p>最后一次 receive() 的时间戳，用于超时判定。
     * 与 {@link #getLastActiveTime()} 不同，此字段仅在收到
     * 服务端响应时更新，不包含 send 操作的时间。</p>
     *
     * @return 最后接收时间戳（毫秒），从未接收过时返回0
     * @since 2026-06-18
     */
    long getLastReceiveTime();

    /**
     * 获取最后一次发送请求的时间
     *
     * <p>最后一次 send() 的时间戳，用于"未收到首条响应"的超时判定。
     * 当服务端未返回任何响应时，通过此字段计算发送等待间隔。</p>
     *
     * @return 最后发送时间戳（毫秒），从未发送过时返回0
     * @since 2026-06-18
     */
    long getLastSendTime();

    /**
     * 判断是否为临时会话
     *
     * <p>临时会话在收到 taskCompleted 消息且任务栈为空时，会自动销毁。</p>
     *
     * @return true 是临时会话，false 是普通会话
     * @since 2026-05-19
     */
    boolean isTemporary();

    /**
     * 设置是否为临时会话
     *
     * <p>临时会话在收到 taskCompleted 消息且任务栈为空时，会自动销毁。</p>
     *
     * @param isTemporary true 设为临时会话，false 设为普通会话
     * @since 2026-05-19
     */
    void setTemporary(boolean isTemporary);
}
