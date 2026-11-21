/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.login.connection.impl;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.component.eventbus.EventBusManager;
import com.huawei.aisfsdkv2.component.eventbus.EventHandleStrategyEnum;
import com.huawei.aisfsdkv2.component.eventbus.event.ConnectionOpenedEvent;
import com.huawei.aisfsdkv2.component.httpclient.HttpClientManager;
import com.huawei.aisfsdkv2.component.httpclient.callback.WebSocketCallback;
import com.huawei.aisfsdkv2.component.httpclient.websocket.WebSocketClient;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.logtrack.LogTrackManager;
import com.huawei.aisfsdkv2.component.logtrack.LogTrackType;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.constants.HttpStatusCode;
import com.huawei.aisfsdkv2.login.auth.TokenManager;
import com.huawei.aisfsdkv2.login.connection.ConnectionManager;
import com.huawei.aisfsdkv2.login.connection.connhandle.ConnectionCallback;
import com.huawei.aisfsdkv2.login.connection.connhandle.ConnectionParam;
import com.huawei.aisfsdkv2.login.connection.model.ConnectionState;
import com.huawei.aisfsdkv2.login.connection.msghandle.MsgHandler;
import com.huawei.aisfsdkv2.login.connection.msghandle.SendResultCodeEnum;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 连接管理器实现（基于 HttpClient）
 */
public class ConnectionManagerImpl implements ConnectionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("ConnectionManagerImpl");

    /** 用户主动断开时的原因标识 */
    private static final String DISCONNECT_REASON_USER = "User disconnect";

    /** 上次连接参数，用于重连时复用 */
    private ConnectionParam currentParam;

    /** 原子引用，确保状态一致性 */
    private final AtomicReference<ConnectionState> connectionState = new AtomicReference<>(ConnectionState.DISCONNECTED);

    /** 消息队列（有界队列，防止内存无限增长） */
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>(1000);

    /** Handler 列表（支持多个 Handler） */
    private final List<MsgHandler> msgHandlers = new CopyOnWriteArrayList<>();

    /** 发送任务运行标志 */
    private volatile boolean senderRunning = false;

    private static class Holder {
        private static final ConnectionManagerImpl INSTANCE = new ConnectionManagerImpl();
    }

    /**
     * 获取单例实例（供接口调用）
     */
    public static ConnectionManagerImpl getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 私有构造方法
     */
    private ConnectionManagerImpl() {
    }

    /**
     * 建立 WebSocket 连接
     *
     * <p>使用 HttpClient 发起 WebSocket 握手，请求头携带 Token、DeviceId 等认证信息。
     * 连接结果通过 {@link ConnectionParam#getCallback()} 异步回调。
     *
     * <p>执行流程：
     * 1. 校验参数
     * 2. 断开已有连接（如果存在）
     * 3. 创建 WebSocket 连接
     *
     * @param param 连接参数，包含 URL、Token、DeviceId、用户名及回调
     */
    @Override
    public synchronized void connect(ConnectionParam param) {
        ConnectionCallback callback = validateAndExtractCallback(param);
        if (callback == null) {
            return;
        }
        LOGGER.info("ConnectionManagerImpl.connect: Connecting to: {}", param.getUrl());
        ConnectionState currentState = connectionState.get();
        if (currentState.isConnected() || currentState.isConnecting()) {
            LOGGER.warn("ConnectionManagerImpl.connect: Already connected or connecting, state={}, ignore this request", currentState);
            return;
        }
        if (currentState.webSocketClient != null) {
            disconnectInternal("Reconnecting");
        }
        this.currentParam = param;
        String latestToken = TokenManager.getInstance().getCurrentToken();
        if (latestToken != null && !latestToken.isEmpty()) {
            param = ConnectionParam.builder()
                    .url(param.getUrl())
                    .identifyId(param.getIdentifyId())
                    .token(latestToken)
                    .userName(param.getUserName())
                    .callback(param.getCallback())
                    .build();
            LOGGER.info("ConnectionManagerImpl.connect: Token updated from TokenManager");
        } else {
            LOGGER.warn("ConnectionManagerImpl.connect: TokenManager returned empty token, using param token");
        }
        String sdkRoleName = AISFSDKClient.getBaseApi().getSdkRoleName();
        Map<String, String> headers = new HashMap<>();
        headers.put("Sender", sdkRoleName);
        headers.put("UserName", param.getUserName());
        headers.put("DeviceId", param.getIdentifyId());
        headers.put("IdentifyId", param.getIdentifyId());
        headers.put("DeviceCategory", AISFSDKClient.getBaseApi().getDeviceInfo().getDeviceType().getValue());
        headers.put("DeviceCategoryStr", AISFSDKClient.getBaseApi().getDeviceInfo().getDeviceType().getName());
        headers.put("Token", param.getToken());
        headers.put("AuthVersion", "1");
        LOGGER.debug("ConnectionManagerImpl.connect: Headers prepared, url={}", param.getUrl());
        WebSocketClient wsClient = HttpClientManager.getInstance().getWebSocketClient();
        wsClient.connect(param.getUrl(), headers, createWebSocketCallback(callback));
        connectionState.set(ConnectionState.connecting(wsClient));
    }

    /**
     * 校验参数并提取回调
     *
     * @param param 连接参数
     * @return 校验通过返回 callback，否则返回 null
     */
    private ConnectionCallback validateAndExtractCallback(ConnectionParam param) {
        if (param == null) {
            LOGGER.error("ConnectionManagerImpl.connect: param is null");
            return null;
        }
        LOGGER.info("ConnectionManagerImpl.validateAndExtractCallback: start validate, url={}", param.getUrl());
        ConnectionCallback callback = param.getCallback();
        if (!validateRequiredField(param.getUrl(), "url", callback)
                || !validateRequiredField(param.getToken(), "token", callback)
                || !validateRequiredField(param.getIdentifyId(), "identifyId", callback)
                || !validateRequiredField(param.getUserName(), "userName", callback)) {
            LOGGER.error("ConnectionManagerImpl.validateAndExtractCallback: validation failed");
            return null;
        }
        LOGGER.info("ConnectionManagerImpl.validateAndExtractCallback: validation passed");
        return callback;
    }

    /**
     * 校验必填字段
     *
     * @param field    字段值
     * @param name     字段名
     * @param callback 回调对象
     * @return 校验通过返回 true
     */
    private boolean validateRequiredField(String field, String name, ConnectionCallback callback) {
        if (field == null || field.isEmpty()) {
            LOGGER.error("ConnectionManagerImpl.connect: {} is null or empty", name);
            handleDisconnected(-1, name + " is required", callback);
            return false;
        }
        return true;
    }

    /**
     * 创建 WebSocket 回调
     *
     * @param callback 连接回调
     * @return WebSocketCallback 实例
     */
    private WebSocketCallback createWebSocketCallback(ConnectionCallback callback) {
        return new WebSocketCallback() {
            @Override
            public void onConnected() {
                LOGGER.info("ConnectionManagerImpl.onConnected: WebSocket connected");
                handleConnected(connectionState.get(), callback);
            }

            @Override
            public void onTextMessage(String message) {
                LOGGER.debug("ConnectionManagerImpl.onTextMessage: received text={}", message);
                LogTrackManager.getInstance().track(LogTrackType.RECEIVE, message);
                notifyReceiveMsg(SendResultCodeEnum.SUCCESS, null, message);
            }

            @Override
            public void onBinaryMessage(byte[] data) {
                LOGGER.debug("ConnectionManagerImpl.onBinaryMessage: received binary, length={}", data.length);
            }

            @Override
            public void onDisconnected(int code, String reason) {
                LOGGER.info("ConnectionManagerImpl.onDisconnected: code={}, reason={}", code, reason);
                handleDisconnected(code, reason, callback);
            }

            @Override
            public void onConnectionFailed(Exception error) {
                LOGGER.error("ConnectionManagerImpl.onConnectionFailed: {}", error.getMessage());
                int statusCode = extractStatusCodeFromException(error);
                if (isAuthenticationFailure(statusCode)) {
                    LOGGER.error("ConnectionManagerImpl.onConnectionFailed: Authentication failed, code: {}", statusCode);
                    handleAuthFailed(callback);
                } else {
                    String message = error.getMessage();
                    handleDisconnected(statusCode, message != null ? message : "Connection failure", callback);
                }
            }

            @Override
            public void onError(Exception error) {
                LOGGER.error("ConnectionManagerImpl.onError: {}", error.getMessage());
                String message = error.getMessage();
                handleDisconnected(-1, message != null ? message : "Runtime error", callback);
            }
        };
    }

    /**
     * 连接成功处理
     *
     * <p>状态流转：CONNECTING → CONNECTED。
     * 使用 CAS 确保只有连接中的连接才能转为已连接。
     *
     * @param current   当前状态
     * @param callback  连接回调
     */
    private void handleConnected(ConnectionState current, ConnectionCallback callback) {
        ConnectionState newState = ConnectionState.connected(current.webSocketClient);
        if (connectionState.compareAndSet(current, newState)) {
            LOGGER.info("ConnectionManagerImpl.handleConnected: WebSocket connected");
            if (callback != null) {
                callback.onConnected();
            }
            publishConnectionOpenedEvent();
        } else {
            LOGGER.warn("ConnectionManagerImpl.handleConnected: State changed by other thread, close websocket");
            current.webSocketClient.disconnect();
        }
    }

    /**
     * 从异常中提取 HTTP 状态码
     *
     * <p>WebSocketHandshakeException 通常包含 "Invalid handshake response status: XXX" 格式的消息，
     * 尝试从中提取状态码用于判断认证失败等场景。
     *
     * @param error 异常
     * @return HTTP 状态码，无法提取时返回 -1
     */
    private int extractStatusCodeFromException(Exception error) {
        if (error == null || error.getMessage() == null) {
            return -1;
        }
        String message = error.getMessage();
        try {
            int colonIdx = message.lastIndexOf(':');
            if (colonIdx >= 0 && colonIdx < message.length() - 1) {
                String codeStr = message.substring(colonIdx + 1).trim();
                int spaceIdx = codeStr.indexOf(' ');
                if (spaceIdx > 0) {
                    codeStr = codeStr.substring(0, spaceIdx);
                }
                return Integer.parseInt(codeStr);
            }
        } catch (NumberFormatException e) {
            LOGGER.debug("ConnectionManagerImpl.extractStatusCodeFromException: Cannot parse status code from: {}", message);
        }
        return -1;
    }

    /**
     * 主动断开 WebSocket 连接
     */
    @Override
    public void disconnect() {
        disconnectInternal(DISCONNECT_REASON_USER);
    }

    /**
     * 内部断开连接（带原因）
     *
     * <p>执行流程：
     * 1. 停止发送任务
     * 2. 清空消息队列，通知所有 Handler 消息被丢弃
     * 3. 关闭 WebSocket 连接
     *
     * @param reason 断开原因
     */
    private void disconnectInternal(String reason) {
        LOGGER.info("ConnectionManagerImpl.disconnect: reason={}", reason);
        senderRunning = false;
        String msg;
        while ((msg = messageQueue.poll()) != null) {
            notifyReceiveMsg(SendResultCodeEnum.DISCONNECTED, "connection disconnected: " + reason, msg);
        }
        ConnectionState oldState = connectionState.getAndSet(ConnectionState.DISCONNECTED);
        if (oldState.webSocketClient != null) {
            oldState.webSocketClient.disconnect();
        }
    }

    /**
     * 重新连接
     *
     * <p>先断开当前连接，再使用上次保存的 {@link ConnectionParam} 重新发起连接。
     * 若无历史连接参数（从未连接过），则直接返回。
     */
    @Override
    public synchronized void reconnect() {
        LOGGER.info("ConnectionManagerImpl.reconnect: Reconnecting...");
        ConnectionState currentState = connectionState.get();
        if (currentState.isConnected()) {
            LOGGER.info("ConnectionManagerImpl.reconnect: Already connected, ignore");
            return;
        }
        if (currentState.isConnecting()) {
            LOGGER.info("ConnectionManagerImpl.reconnect: Connection in progress, ignore");
            return;
        }
        if (currentParam == null) {
            LOGGER.error("ConnectionManagerImpl.reconnect: No previous connection params");
            return;
        }
        disconnectInternal("Reconnecting");
        connect(currentParam);
    }

    /**
     * 返回当前 WebSocket 是否处于已连接状态
     *
     * @return true 已连接，false 未连接或已断开
     */
    @Override
    public boolean isConnected() {
        return connectionState.get().isConnected();
    }

    /**
     * 返回当前 WebSocket 是否处于连接中状态
     *
     * @return true 正在连接中，false 未在连接
     */
    @Override
    public boolean isConnecting() {
        return connectionState.get().isConnecting();
    }

    /**
     * 发送字符串消息（异步、无阻塞、无返回值）
     *
     * <p>执行流程：
     * 1. 基础校验（null 或空字符串直接返回）
     * 2. 执行 beforeFilter 链，被过滤则通知 Handler
     * 3. 消息入队（非阻塞），队列满则通知 Handler
     * 4. 确保发送任务运行
     *
     * @param message 要发送的字符串消息，若为 null 或空则被忽略
     */
    @Override
    public void send(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        String filteredMsg = executeBeforeFilterChain(message);
        if (filteredMsg == null) {
            notifyReceiveMsg(SendResultCodeEnum.FILTERED, "filtered by beforeFilter", message);
            return;
        }
        boolean offered = messageQueue.offer(filteredMsg);
        if (!offered) {
            notifyReceiveMsg(SendResultCodeEnum.QUEUE_FULL, "message queue is full", message);
            return;
        }
        ensureSenderTask();
    }

    /**
     * 添加消息处理器
     *
     * @param handler 消息处理器，不能为 null
     */
    @Override
    public void addMsgHandler(MsgHandler handler) {
        if (handler != null) {
            msgHandlers.add(handler);
            LOGGER.info("MsgHandler added: {}", handler.getName());
        }
    }

    /**
     * 执行 beforeFilter 链
     *
     * <p>按 Handler 注册顺序链式调用，任一 Handler 返回 null 则终止链。
     *
     * @param message 原始消息
     * @return 处理后的消息，被过滤返回 null
     */
    private String executeBeforeFilterChain(String message) {
        String result = message;
        for (MsgHandler handler : msgHandlers) {
            result = handler.beforeFilter(result);
            if (result == null) {
                return null;
            }
        }
        return result;
    }

    /**
     * 通知所有 Handler 结果（逆序回调）
     *
     * <p>后注册的先回调。
     *
     * @param code    结果码枚举
     * @param error   错误描述
     * @param message 发送的消息内容
     */
    private void notifyReceiveMsg(SendResultCodeEnum code, String error, String message) {
        for (int i = msgHandlers.size() - 1; i >= 0; i--) {
            try {
                msgHandlers.get(i).onReceiveMsg(code, error, message);
            } catch (Exception e) {
                LOGGER.error("Handler onReceiveMsg error", e);
            }
        }
    }

    /**
     * 确保发送任务在 ThreadPoolManager 中运行
     *
     * <p>使用双重检查锁确保只启动一个发送任务。
     */
    private void ensureSenderTask() {
        if (!senderRunning) {
            synchronized (this) {
                if (!senderRunning) {
                    senderRunning = true;
                    ThreadPoolManager.getInstance().executeSharedThreadPool(this::senderLoop);
                    LOGGER.info("ConnectionManagerImpl.ensureSenderTask: sender task started");
                }
            }
        }
    }

    /**
     * 发送任务主循环
     *
     * <p>循环执行直到 senderRunning 为 false：
     * 1. 阻塞取消息（带超时）
     * 2. 等待连接就绪（最多 5 秒）
     * 3. 执行发送，通知 Handler 结果
     */
    private void senderLoop() {
        LOGGER.info("ConnectionManagerImpl.senderLoop: started");
        while (senderRunning) {
            try {
                String msg = messageQueue.poll(100, TimeUnit.MILLISECONDS);
                if (msg == null) {
                    continue;
                }
                ConnectionState state = connectionState.get();
                if (!state.canSend()) {
                    notifyReceiveMsg(SendResultCodeEnum.NOT_CONNECTED, "not connected", msg);
                    continue;
                }
                LOGGER.info("ConnectionManagerImpl.senderLoop: Sending raw message, length={}, content={}", msg.length(), msg);
                LogTrackManager.getInstance().track(LogTrackType.SEND, msg);
                WebSocketClient wsClient = state.getWebSocketClientForSend();
                boolean sent = wsClient.sendText(msg);
                if (sent) {
                    LOGGER.debug("ConnectionManagerImpl.senderLoop: Message sent successfully");
                } else {
                    LOGGER.error("ConnectionManagerImpl.senderLoop: WebSocket send returned false");
                    notifyReceiveMsg(SendResultCodeEnum.SEND_FAILED, "websocket send failed", msg);
                }
            } catch (InterruptedException e) {
                LOGGER.info("ConnectionManagerImpl.senderLoop: interrupted");
                break;
            } catch (Exception e) {
                LOGGER.error("ConnectionManagerImpl.senderLoop: error", e);
            }
        }
        LOGGER.info("ConnectionManagerImpl.senderLoop: stopped");
    }

    /**
     * 判断是否为认证失败
     *
     * @param code HTTP 状态码
     * @return true 认证失败
     */
    private boolean isAuthenticationFailure(int code) {
        return code == HttpStatusCode.UNAUTHORIZED || code == HttpStatusCode.PROXY_AUTH_REQUIRED;
    }

    /**
     * 发布连接打开事件
     * 
     * <p>使用异步并行策略，避免阻塞连接流程，同时保证各订阅者独立执行。</p>
     */
    private void publishConnectionOpenedEvent() {
        try {
            ConnectionOpenedEvent event = new ConnectionOpenedEvent();
            EventBusManager.getInstance().publish(
                event, 
                EventHandleStrategyEnum.ASYNC_PARALLEL
            );
            LOGGER.debug("ConnectionManagerImpl: ConnectionOpenedEvent published");
        } catch (Exception e) {
            LOGGER.error("ConnectionManagerImpl: Failed to publish ConnectionOpenedEvent", e);
            // 事件发布失败不影响主业务流程
        }
    }

    /**
     * 认证失败处理
     *
     * <p>关闭WebSocket连接并通知上层认证失败。
     * 认证失败时WebSocket已建立但无法使用，需要主动关闭释放资源。
     *
     * @param callback 连接回调
     */
    private void handleAuthFailed(ConnectionCallback callback) {
        LOGGER.error("ConnectionManagerImpl.handleAuthFailed: Authentication failed");
        ConnectionState oldState = connectionState.getAndSet(ConnectionState.DISCONNECTED);
        if (oldState.webSocketClient != null) {
            oldState.webSocketClient.disconnect();
        }
        if (callback != null) {
            callback.onAuthFailed();
        }
    }

    /**
     * 连接断开处理
     *
     * @param code     断开码
     * @param reason   断开原因
     * @param callback 回调
     */
    private void handleDisconnected(int code, String reason, ConnectionCallback callback) {
        LOGGER.info("ConnectionManagerImpl.handleDisconnected: code={}, reason={}", code, reason);
        ConnectionState oldState = connectionState.getAndSet(ConnectionState.DISCONNECTED);
        if (oldState.webSocketClient != null) {
            oldState.webSocketClient.disconnect();
        }
        if (callback != null) {
            callback.onDisconnected(code, reason);
        }
    }

    /**
     * 立即发送消息（同步）
     *
     * <p>不经过内部队列，直接通过 WebSocket 发送。
     * <strong>警告：此方法会阻塞调用线程，直到发送完成或失败。
     * 仅用于高优先级消息（如 Logout），普通消息请使用 {@link #send(String)}。</strong></p>
     *
     * @param message 要发送的字符串消息
     * @return 是否发送成功
     */
    @Override
    public boolean sendImmediately(String message) {
        if (message == null || message.isEmpty()) {
            LOGGER.error("ConnectionManagerImpl.sendImmediately: message is null or empty");
            return false;
        }
        ConnectionState state = connectionState.get();
        if (!state.canSend()) {
            LOGGER.error("ConnectionManagerImpl.sendImmediately: Not connected or WebSocket is null");
            return false;
        }
        try {
            LOGGER.debug("ConnectionManagerImpl.sendImmediately: Sending message immediately, length={}", message.length());
            WebSocketClient wsClient = state.getWebSocketClientForSend();
            boolean sent = wsClient.sendText(message);
            if (sent) {
                LOGGER.debug("ConnectionManagerImpl.sendImmediately: Message sent successfully");
            } else {
                LOGGER.error("ConnectionManagerImpl.sendImmediately: WebSocket send returned false");
            }
            return sent;
        } catch (Exception e) {
            LOGGER.error("ConnectionManagerImpl.sendImmediately: Exception while sending message", e);
            return false;
        }
    }
}
