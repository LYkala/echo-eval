/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.session.timeout;

import com.huawei.aisfsdkv2.api.session.SessionResponseData;
import com.huawei.aisfsdkv2.api.session.SessionStatus;
import com.huawei.aisfsdkv2.api.session.StreamResponseTimeoutProperty;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.api.session.NotifyInfo;
import com.huawei.aisfsdkv2.session.Session;
import com.huawei.aisfsdkv2.api.session.SessionCallback;
import com.huawei.aisfsdkv2.session.SessionManager;
import com.huawei.aisfsdkv2.session.impl.SessionImpl;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

/**
 * 流式响应超时管理器
 *
 * <p>定时检查所有会话的响应间隔是否超过配置的超时时间，
 * 超过时通过 {@link SessionCallback#onReceived} 回调通知用户。</p>
 *
 * <h3>超时判定规则</h3>
 * <ul>
 *   <li>响应间隔 = 当前时间 - 最后一次receive时间（{@link Session#getLastReceiveTime()}）</li>
 *   <li>timeoutMillis = -1 表示不启用超时，检查时跳过</li>
 *   <li>未设置超时配置的会话不参与检查</li>
 *   <li>已销毁的会话不参与检查</li>
 *   <li>无活跃任务的会话不参与检查</li>
 *   <li>超时后通知用户，不销毁会话</li>
 * </ul>
 *
 * <h3>检查机制</h3>
 * <p>每5秒执行一次检查任务，由 {@link com.huawei.aisfsdkv2.session.impl.SessionManagerImpl} 初始化时显式启动。</p>
 *
 * @since 2026-06-18
 */
public class StreamResponseTimeoutManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("StreamResponseTimeoutManager");

    /** 检查间隔：5秒（毫秒） */
    private static final long CHECK_INTERVAL_MS = 5000L;

    /** 超时通知错误码 */
    private static final int TIMEOUT_NOTIFY_CODE = 408;

    /** 单例Holder */
    private static class Holder {
        private static final StreamResponseTimeoutManager INSTANCE = new StreamResponseTimeoutManager();
    }

    /**
     * 获取单例实例
     *
     * @return StreamResponseTimeoutManager 单例
     */
    public static StreamResponseTimeoutManager getInstance() {
        return Holder.INSTANCE;
    }

    /** 是否已启动检查任务 */
    private volatile boolean started = false;

    /** 检查轮次计数器，用于心跳日志 */
    private int checkRound = 0;

    /** 心跳日志间隔：每6次检查（30秒）打印一次 */
    private static final int HEARTBEAT_INTERVAL = 6;

    private StreamResponseTimeoutManager() {
    }

    /**
     * 初始化流式响应超时管理器
     *
     * <p>启动定时检查任务，由 {@link com.huawei.aisfsdkv2.session.impl.SessionManagerImpl} 调用。</p>
     */
    public void init() {
        startCheckTask();
    }

    /**
     * 启动定时检查任务
     */
    private synchronized void startCheckTask() {
        if (started) {
            return;
        }
        ThreadPoolManager.getInstance().addScheduleTaskSharedThreadPool(
            this::checkAndNotify,
            CHECK_INTERVAL_MS,
            CHECK_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        started = true;
        LOGGER.info("StreamResponseTimeoutManager: timeout check task started, interval={}s",
            CHECK_INTERVAL_MS / 1000);
    }

    /**
     * 检查并通知超时会话
     */
    private void checkAndNotify() {
        long now = System.currentTimeMillis();
        int checkedCount = 0;
        int timeoutCount = 0;
        Collection<Session> sessions = SessionManager.getInstance().listSessions();
        for (Session session : sessions) {
            if (session.getStatus() == SessionStatus.DESTROYED) {
                continue;
            }
            StreamResponseTimeoutProperty property = session.getStreamResponseTimeoutProperty();
            if (property == null || !property.isTimeoutEnabled()) {
                continue;
            }
            String currentTaskId = session.getCurrentTaskId();
            long timeoutMillis = property.getTimeoutMillis();
            if (currentTaskId != null && !currentTaskId.isEmpty()) {
                checkedCount++;
                long receiveGap = now - session.getLastReceiveTime();
                if (receiveGap > timeoutMillis) {
                    LOGGER.info("StreamResponseTimeoutManager.checkAndNotify: stream response timeout, "
                        + "sessionId={}, taskId={}, receiveGap={}ms, timeout={}ms",
                        session.getId(), currentTaskId, receiveGap, timeoutMillis);
                    notifyTimeout(session, currentTaskId, receiveGap, property);
                    timeoutCount++;
                }
                continue;
            }
            long lastSendTime = session.getLastSendTime();
            if (lastSendTime > 0) {
                checkedCount++;
                long sendGap = now - lastSendTime;
                if (sendGap > timeoutMillis) {
                    LOGGER.info("StreamResponseTimeoutManager.checkAndNotify: first response timeout, "
                        + "sessionId={}, sendGap={}ms, timeout={}ms",
                        session.getId(), sendGap, timeoutMillis);
                    notifyTimeout(session, null, sendGap, property);
                    timeoutCount++;
                }
            }
        }
        if (timeoutCount > 0) {
            LOGGER.info("StreamResponseTimeoutManager.checkAndNotify: checked={}, timeout={}",
                checkedCount, timeoutCount);
        }
        checkRound++;
        if (checkRound >= HEARTBEAT_INTERVAL) {
            checkRound = 0;
            LOGGER.info("StreamResponseTimeoutManager.checkAndNotify: heartbeat, totalSessions={}, timeoutSessions={}",
                sessions.size(), checkedCount);
        }
    }

    /**
     * 通知会话超时
     *
     * <p>构造超时响应数据并通过 {@link SessionCallback#onReceived} 回调通知用户。</p>
     *
     * @param session 超时会话
     * @param taskId 当前任务ID
     * @param receiveGap 等待间隔（毫秒）
     * @param property 超时属性
     */
    private void notifyTimeout(Session session, String taskId, long receiveGap,
            StreamResponseTimeoutProperty property) {
        SessionCallback callback = ((SessionImpl) session).getCallback();
        if (callback == null) {
            LOGGER.warn("StreamResponseTimeoutManager.notifyTimeout: callback is null, skip, sessionId={}",
                session.getId());
            return;
        }
        SessionResponseData timeoutData = buildTimeoutResponseData(
            session.getId(), taskId, receiveGap, property);
        callback.onReceived(TIMEOUT_NOTIFY_CODE, "StreamResponseTimeout", timeoutData);
    }

    /**
     * 构造超时响应数据
     *
     * @param sessionId 会话ID
     * @param taskId 超时的任务ID
     * @param receiveGap 等待间隔（毫秒）
     * @param property 超时属性
     * @return 超时响应数据
     */
    private SessionResponseData buildTimeoutResponseData(
            String sessionId, String taskId, long receiveGap,
            StreamResponseTimeoutProperty property) {
        SessionResponseData data = new SessionResponseData();
        data.setOutputType(com.huawei.aisfsdkv2.api.session.OutputTypeEnum.NOTIFY);
        NotifyInfo notifyInfo = new NotifyInfo();
        notifyInfo.setCode(TIMEOUT_NOTIFY_CODE);
        try {
            JSONObject timeoutDetail = new JSONObject();
            timeoutDetail.put("TimeoutType", "StreamResponseTimeout");
            timeoutDetail.put("SessionId", sessionId);
            timeoutDetail.put("TaskId", taskId);
            timeoutDetail.put("ReceiveGapMillis", receiveGap);
            timeoutDetail.put("ConfiguredTimeoutMillis", property.getTimeoutMillis());
            timeoutDetail.put("Timestamp", System.currentTimeMillis());
            notifyInfo.setUserOutputObjectRawString(timeoutDetail.toString());
        } catch (org.json.JSONException e) {
            LOGGER.error("StreamResponseTimeoutManager.buildTimeoutResponseData: JSONException, sessionId={}, error={}",
                sessionId, e.getMessage());
            notifyInfo.setUserOutputObjectRawString("{\"TimeoutType\":\"StreamResponseTimeout\",\"SessionId\":\""
                + sessionId + "\",\"TaskId\":\"" + taskId + "\",\"ReceiveGapMillis\":"
                + receiveGap + ",\"ConfiguredTimeoutMillis\":"
                + property.getTimeoutMillis() + ",\"Timestamp\":"
                + System.currentTimeMillis() + "}");
        }
        data.setNotifyInfo(notifyInfo);
        return data;
    }
}
