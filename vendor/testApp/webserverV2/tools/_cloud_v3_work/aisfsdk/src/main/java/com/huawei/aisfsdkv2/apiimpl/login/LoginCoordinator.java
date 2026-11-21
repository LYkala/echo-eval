/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.login;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.base.ServerAuthModeEnum;
import com.huawei.aisfsdkv2.api.base.ServerInfo;
import com.huawei.aisfsdkv2.api.login.LoginErrorEnum;
import com.huawei.aisfsdkv2.api.login.LoginStatus;
import com.huawei.aisfsdkv2.apiimpl.base.BaseApiImpl;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.network.NetworkChangeCallback;
import com.huawei.aisfsdkv2.component.network.NetworkMonitor;
import com.huawei.aisfsdkv2.component.network.NetworkStatusInfo;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.util.ThreadUtil;

import com.huawei.aisfsdkv2.login.auth.TokenManager;

import java.util.Random;
import com.huawei.aisfsdkv2.login.connection.connhandle.ConnectionCallback;
import com.huawei.aisfsdkv2.login.connection.ConnectionManager;
import com.huawei.aisfsdkv2.login.connection.connhandle.ConnectionParam;

import java.util.Locale;

/**
 * 登录流程协调器
 * 协调Token获取和WebSocket连接的整个流程
 * 实现NetworkChangeCallback接口，监听网络变化进行自动重连
 */
class LoginCoordinator implements NetworkChangeCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger("LoginCoordinator");
    private static final String WSS_URL = "/ws/acm/v1/ai";

    // 重连延时常量（单位：毫秒）
    private static final long IMMEDIATE_RECONNECT_DELAY_MS = 2000;
    private static final int RANDOM_DELAY_MIN_SECONDS = 1;
    private static final int RANDOM_DELAY_MAX_SECONDS = 30;

    // 自适应重连策略常量
    private static final long RECONNECT_WINDOW_MS = 5 * 60 * 1000;
    private static final int FAST_RECONNECT_THRESHOLD = 2;
    private static final long FAST_RECONNECT_DELAY_MS = 2000;

    private final TokenManager tokenManager;
    private final ConnectionManager connectionManager;
    private volatile long reconnectWindowStartTime = 0;
    private volatile int reconnectCountInWindow = 0;

    /**
     * 默认构造函数，使用单例 TokenManager
     */
    LoginCoordinator() {
        this(TokenManager.getInstance());
    }

    /**
     * 构造函数，支持依赖注入（便于测试）
     *
     * @param tokenManager TokenManager 实例
     */
    LoginCoordinator(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
        this.connectionManager = ConnectionManager.getInstance();
        // 订阅网络变化通知
        NetworkMonitor.getInstance().subscribe(this);
        LOGGER.info("LoginCoordinator: Subscribed to network changes");
    }

    /**
     * 网络状态变化回调
     * <p>
     * 当网络从无网络变为有网络时，触发立即重连（无延时）
     *
     * @param oldStatus 变化前的网络状态
     * @param newStatus 变化后的网络状态
     */
    @Override
    public void onNetworkChanged(NetworkStatusInfo oldStatus, NetworkStatusInfo newStatus) {
        LOGGER.info("LoginCoordinator.onNetworkChanged: Network changed from {} to {}", oldStatus, newStatus);
        // 从无网络变为有网络时触发立即重连
        if (!oldStatus.isNetworkAvailable() && newStatus.isNetworkAvailable()) {
            LOGGER.info("LoginCoordinator.onNetworkChanged: Network became available, triggering immediate reconnect");
            // 在独立线程中执行重连，避免阻塞回调
            ThreadPoolManager.getInstance().executeIndependentThreadPool(() -> reconnect(true));
        }
    }

    /**
     * 启动登录流程
     *
     * @param identifyId 设备标识ID
     * @param username   用户名
     * @param password   用户密码
     * @param callback   流程回调
     */
    void startLoginFlow(String identifyId, String username, String password, LoginFlowCallback callback) {
        // 步骤1: 获取Token（同步阻塞，最长可能3分钟）
        String acmToken = tokenManager.fetchToken(identifyId, username, password);
        if (acmToken == null || acmToken.isEmpty()) {
            LOGGER.error("LoginCoordinator.startLoginFlow: Token acquisition failed");
            callback.onTokenFailed(LoginErrorEnum.LOGIN_FAILURE_NEED_RETRY, "Get token failed");
            return;
        }
        LOGGER.info("LoginCoordinator.startLoginFlow: Token acquired successfully");
        callback.onTokenAcquired(identifyId, acmToken);
        // 步骤2: 建立WebSocket连接
        connectWebSocket(identifyId, acmToken, username, callback);
    }

    /**
     * 建立WebSocket连接
     *
     * @param identifyId 身份标识ID
     * @param token      ACM Token
     * @param username   用户名
     * @param callback   流程回调
     */
    private void connectWebSocket(String identifyId, String token,
                                  String username, LoginFlowCallback callback) {
        String websocketUrl = buildWebsocketUrl();
        ConnectionParam param = ConnectionParam.builder()
                .url(websocketUrl)
                .identifyId(identifyId)
                .token(token)
                .userName(username)
                .callback(new ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        LOGGER.info("LoginCoordinator.connectWebSocket: WebSocket connected, identifyId={}", identifyId);
                        callback.onConnected();
                    }

                    @Override
                    public void onDisconnected(int code, String reason) {
                        LOGGER.warn("LoginCoordinator.connectWebSocket: WebSocket disconnected, code={}, reason={}", code, reason);
                        callback.onDisconnected(code, reason);
                    }

                    @Override
                    public void onAuthFailed() {
                        LOGGER.error("LoginCoordinator.connectWebSocket: WebSocket auth failed, identifyId={}", identifyId);
                        callback.onAuthFailed();
                    }
                })
                .build();
        connectionManager.connect(param);
    }

    /**
     * 构建WebSocket URL
     *
     * @return WebSocket 连接地址
     */
    private String buildWebsocketUrl() {
        ServerInfo serverInfo = BaseApiImpl.getInstance().getServerInfo();
        if (serverInfo == null) {
            throw new IllegalStateException("ServerInfo not set");
        }
        ServerAuthModeEnum authMode = serverInfo.getServerAuthMode();
        int port = authMode == ServerAuthModeEnum.APIGW
                ? serverInfo.getApigwPort()
                : serverInfo.getWebsocketPort();
        return String.format(Locale.ENGLISH, "wss://%s:%d%s",
                serverInfo.getServerIp(),
                port,
                WSS_URL
        );
    }

    /**
     * 断开连接
     * <p>
     * 主动断开当前 WebSocket 连接
     */
    void disconnect() {
        LOGGER.info("LoginCoordinator.disconnect: start");
        connectionManager.disconnect();
        LOGGER.info("LoginCoordinator.disconnect: done");
    }

    /**
     * 重新连接
     *
     * @param immediate true表示立即重连（网络变化场景，会重置计数器），
     *                  false表示使用自适应延时策略（普通重连场景）
     */
    void reconnect(boolean immediate) {
        LoginStatus loginStatus = AISFSDKClient.getLoginApi().getLoginInfo().getStatus();
        if (loginStatus == LoginStatus.IDLE) {
            LOGGER.info("LoginCoordinator.reconnect: Login status is IDLE, skip reconnect");
            return;
        }
        if (connectionManager.isConnecting()) {
            LOGGER.info("LoginCoordinator.reconnect: Connection is in progress, skip reconnect");
            return;
        }
        if (connectionManager.isConnected()) {
            LOGGER.info("LoginCoordinator.reconnect: Already connected, skip reconnect");
            return;
        }
        if (immediate) {
            resetReconnectWindow();
            executeImmediateReconnectDelay();
        } else {
            executeAdaptiveReconnectDelay();
        }
        LOGGER.info("LoginCoordinator.reconnect: Attempting reconnect after delay");
        connectionManager.reconnect();
    }

    private void resetReconnectWindow() {
        reconnectWindowStartTime = System.currentTimeMillis();
        reconnectCountInWindow = 0;
        LOGGER.info("LoginCoordinator: Reconnect window reset due to network change");
    }

    private void executeImmediateReconnectDelay() {
        LOGGER.info("LoginCoordinator.reconnect: Immediate reconnect, sleep {}ms for network ready",
                IMMEDIATE_RECONNECT_DELAY_MS);
        ThreadUtil.sleepNoExcept(IMMEDIATE_RECONNECT_DELAY_MS);
    }

    private void executeAdaptiveReconnectDelay() {
        long now = System.currentTimeMillis();
        boolean useFastReconnect = isFastReconnectStrategy(now);
        if (useFastReconnect) {
            LOGGER.info("LoginCoordinator.reconnect: Fast reconnect #{} in window, delay={}ms",
                    reconnectCountInWindow, FAST_RECONNECT_DELAY_MS);
            ThreadUtil.sleepNoExcept(FAST_RECONNECT_DELAY_MS);
        } else {
            int sleepSeconds = new Random().nextInt(RANDOM_DELAY_MAX_SECONDS - RANDOM_DELAY_MIN_SECONDS + 1)
                    + RANDOM_DELAY_MIN_SECONDS;
            LOGGER.info("LoginCoordinator.reconnect: Slow reconnect #{} in window, delay={}s",
                    reconnectCountInWindow, sleepSeconds);
            ThreadUtil.sleepNoExcept(sleepSeconds * 1000L);
        }
    }

    private boolean isFastReconnectStrategy(long currentTime) {
        if (currentTime - reconnectWindowStartTime > RECONNECT_WINDOW_MS) {
            reconnectWindowStartTime = currentTime;
            reconnectCountInWindow = 1;
            LOGGER.debug("LoginCoordinator: New reconnect window started");
            return true;
        }
        reconnectCountInWindow++;
        return reconnectCountInWindow <= FAST_RECONNECT_THRESHOLD;
    }
}
