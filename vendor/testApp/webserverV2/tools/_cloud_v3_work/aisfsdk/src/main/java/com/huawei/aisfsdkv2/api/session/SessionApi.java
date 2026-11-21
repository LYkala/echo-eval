/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

import com.huawei.aisfsdkv2.session.Session;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 会话API接口
 *
 * <p>定义会话模块对外提供的能力。</p>
 */
public interface SessionApi {

    /**
     * 创建新会话
     *
     * @param callback 会话回调接口，用于接收会话事件通知，可以为null
     * @return 会话ID，创建失败返回null
     */
    String createSession(SessionCallback callback);

    /**
     * 创建新会话（支持临时会话模式）
     *
     * <p>临时会话在收到 taskCompleted 消息且任务栈为空时，会自动销毁。</p>
     *
     * @param callback 会话回调接口，用于接收会话事件通知，可以为null
     * @param isTemporary 是否为临时会话，true 表示临时会话，false 表示普通会话
     * @return 会话ID，创建失败返回null
     * @since 2026-05-19
     */
    String createSession(SessionCallback callback, boolean isTemporary);

    /**
     * 创建默认会话
     *
     * <p>默认会话用于接收无明确目标会话的消息。
     * 同一时刻只有一个默认会话，创建新的默认会话会自动取消旧的默认会话标记。</p>
     *
     * @param callback 会话回调接口，用于接收会话事件通知，可以为null
     * @return 会话ID，创建失败返回null
     */
    String createDefaultSession(SessionCallback callback);

    /**
     * 销毁会话
     *
     * @param sessionId 会话ID
     * @return true 销毁成功，false 会话不存在或已销毁
     */
    boolean destroySession(String sessionId);

    /**
     * 销毁所有会话
     */
    void destroyAllSessions();

    /**
     * 发送消息（使用请求对象）
     *
     * <p>支持更丰富的消息参数，包括用户意图、指定代理、语音ID等。</p>
     *
     * <p>使用示例：</p>
     * <pre>
     * SessionRequest request = new SessionRequest.Builder()
     *     .userInputText("你好")
     *     .userIntent("greeting")
     *     .userSpecifiedAgent("agent-001")
     *     .voiceId("voice-123")
     *     .build();
     * boolean success = sessionApi.sendMessage(sessionId, request);
     * </pre>
     *
     * @param sessionId 会话ID
     * @param request 消息请求对象，不能为null
     * @return true 发送成功，false 发送失败（会话不存在、非ACTIVE状态或请求为null）
     * @since 2026-04-13
     */
    boolean sendMessage(String sessionId, SessionRequest request);

    /**
     * 发送消息（简化调用）
     *
     * <p>便捷方法，无需手动构建 SessionRequest 对象。</p>
     *
     * @param sessionId 会话ID
     * @param userInputText 用户输入文本
     * @param intentList 用户意图列表，可以为null
     * @param targetAgent 目标代理，可以为null
     * @param voiceId 语音ID，可以为null
     * @return true 发送成功，false 发送失败
     */
    boolean sendMessage(String sessionId, String userInputText, List<String> intentList, String targetAgent, String voiceId);

    /**
     * 发送RTF消息（带TTS）
     *
     * <p>发送包含TTS文件路径的消息，用于语音播报场景。</p>
     *
     * <p>UserInputObject 格式：</p>
     * <pre>
     * {"code":200006,"data":{"ttsInfo":"ttsPath值"}}
     * </pre>
     *
     * @param sessionId 会话ID
     * @param ttsPath TTS文件路径
     * @param userInputText 用户输入文本
     * @param intentList 用户意图列表，可以为null
     * @param userAgent 目标代理，可以为null
     * @return true 发送成功，false 发送失败
     */
    boolean sendRTFMessage(String sessionId, String ttsPath, String userInputText, List<String> intentList, String userAgent);

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
     * <p>发送的消息格式：</p>
     * <pre>
     * {
     *   "Header": {
     *     "MessageType": "DataExchange",
     *     "TaskID": "当前任务ID",
     *     ...
     *   },
     *   "Data": {
     *     "MessageType": "UserInput",
     *     "UserInputObject": {
     *       "code": 300001,
     *       "data": {
     *         "type": "STOPTAKEOVER"
     *       }
     *     }
     *   }
     * }
     * </pre>
     *
     * @param sessionId 会话ID
     * @param guiTask GUI任务类型，仅支持 START_TAKEOVER/STOP_TAKEOVER/CANCEL_TAKEOVER
     * @param params 附加参数，可以为null（部分任务类型可能需要额外参数）
     * @return true 发送成功，false 发送失败（会话不存在、非ACTIVE状态、无任务或不支持的任务类型）
     */
    boolean sendMessage(String sessionId, GuiTaskEnum guiTask, Map<String, String> params);

    /**
     * 停止当前任务
     *
     * <p>用于停止当前会话中正在执行的任务。</p>
     *
     * <p>发送的消息格式：</p>
     * <pre>
     * {
     *   "Header": {
     *     "MessageType": "UserTaskManagement",
     *     "TaskID": "当前任务ID",
     *     ...
     *   },
     *   "Data": {
     *     "MessageType": "TaskStop"
     *   }
     * }
     * </pre>
     *
     * @param sessionId 会话ID
     * @return true 发送成功，false 发送失败（会话不存在、非ACTIVE状态或无任务）
     */
    boolean stopTask(String sessionId);

    /**
     * 停止算力主机任务
     *
     * <p>用于停止当前会话中正在执行的算力主机任务。</p>
     * <p>发送的消息中强制设置 DeviceCategory="3"</p>
     * @param sessionId 会话ID
     * @param deviceCategory 设备类型，1云机，2物理机，3算力主机
     * @return true 发送成功，false 发送失败（会话不存在、非ACTIVE状态或无任务）
     */
    boolean stopOtherDeviceCategoryTask(String sessionId, String deviceCategory);

    /**
     * 获取会话状态
     *
     * @param sessionId 会话ID
     * @return 会话状态，会话不存在返回null
     */
    SessionStatus getSessionStatus(String sessionId);

    /**
     * 设置会话状态
     *
     * @param sessionId 会话ID
     * @param status 新状态
     * @return true 设置成功，false 会话不存在或已是DESTROYED状态
     */
    boolean setSessionStatus(String sessionId, SessionStatus status);

    /**
     * 获取所有活动会话ID列表
     *
     * @return 会话ID列表，不包含DESTROYED状态的会话
     */
    List<String> getAllSessionIds();

    /**
     * 停止消息处理
     *
     * <p>调用后，消息处理线程暂停处理队列中的消息，
     * 新消息继续入队缓存。调用 resumeMessageHandle 恢复处理。</p>
     */
    void stopMessageHandle();

    /**
     * 恢复消息处理
     *
     * <p>恢复后，处理线程继续处理队列中缓存的消息。</p>
     */
    void resumeMessageHandle();

    /**
     * 是否暂停消息处理
     *
     * @return true 暂停中，false 正常处理
     */
    boolean isMessageHandlePaused();

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
    String getAttribute(String sessionId, String key);

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
    boolean setAttribute(String sessionId, String key, String value);

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
    boolean removeAttribute(String sessionId, String key);

    /**
     * 根据自定义条件过滤会话
     *
     * <p>遍历所有会话，返回满足过滤条件的会话列表。</p>
     *
     * <p>使用示例：</p>
     * <pre>
     * // 过滤处于模仿任务模式的会话
     * List&lt;Session&gt; mimicSessions = sessionApi.getSessions(
     *     session -&gt; "true".equals(session.getAttribute("MIMIC"))
     * );
     * </pre>
     *
     * @param filter 过滤函数，返回true表示匹配，不能为null
     * @return 满足条件的会话列表，filter为null返回空列表
     * @since 2026-04-21
     */
    List<Session> getSessions(Predicate<Session> filter);

    /**
     * 设置流式响应超时属性
     *
     * <p>为指定会话设置两条响应消息之间的最大间隔超时时间。
     * 超时后通过 {@link SessionCallback#onReceived} 回调通知用户（errorCode=408），
     * 不会销毁会话。</p>
     *
     * <p>{@link StreamResponseTimeoutProperty#getTimeoutMillis()} = -1 表示不启用超时检查（默认），
     * 正值表示超时毫秒数。</p>
     *
     * @param sessionId 会话ID
     * @param property 流式响应超时属性，null表示移除超时配置
     * @return true 设置成功，false 会话不存在或已销毁
     * @since 2026-06-22
     */
    boolean setStreamResponseTimeoutProperty(String sessionId, StreamResponseTimeoutProperty property);

    /**
     * 获取流式响应超时属性
     *
     * @param sessionId 会话ID
     * @return 超时属性，会话不存在或未设置时返回null
     * @since 2026-06-22
     */
    StreamResponseTimeoutProperty getStreamResponseTimeoutProperty(String sessionId);
}
