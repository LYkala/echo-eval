/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.login;

import com.huawei.aisfsdkv2.api.base.DeviceInfo;
import com.huawei.aisfsdkv2.api.base.ServerEnvEnum;
import com.huawei.aisfsdkv2.api.base.ServerInfo;
import com.huawei.aisfsdkv2.apiimpl.base.BaseApiImpl;
import com.huawei.aisfsdkv2.api.login.LoginApi;
import com.huawei.aisfsdkv2.api.login.LoginCallback;
import com.huawei.aisfsdkv2.api.login.LoginErrorEnum;
import com.huawei.aisfsdkv2.api.login.LoginInfo;
import com.huawei.aisfsdkv2.api.login.LoginParam;
import com.huawei.aisfsdkv2.api.login.LoginStatus;
import com.huawei.aisfsdkv2.api.login.TicketProvider;
import com.huawei.aisfsdkv2.component.eventbus.EventBusManager;
import com.huawei.aisfsdkv2.component.eventbus.EventHandleStrategyEnum;
import com.huawei.aisfsdkv2.component.eventbus.event.LoginStatusEvent;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.login.auth.TokenManager;
import com.huawei.aisfsdkv2.login.auth.tokenrefresher.TokenRefresher;
import com.huawei.aisfsdkv2.login.auth.tokenrefresher.TokenResult;
import com.huawei.aisfsdkv2.util.HashUtil;
import com.huawei.aisfsdkv2.util.StringUtil;
import com.huawei.aisfsdkv2.util.ThreadUtil;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.message.MessageProcessor;
import com.huawei.aisfsdkv2.message.model.LogoutMsgInfo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 登录API实现类
 *
 * <p>对外提供登录能力，内部委托给各组件完成具体逻辑。</p>
 *
 * <p>组件架构：
 * <ul>
 *   <li>状态机（LoginStateMachine）：管理登录状态流转</li>
 *   <li>登录协调器（LoginCoordinator）：协调整个登录流程</li>
 *   <li>Token管理器（TokenManager）：处理Token获取和刷新</li>
 * </ul>
 */
public class LoginApiImpl implements LoginApi {
    private static final Logger LOGGER = LoggerFactory.getLogger("LoginApiImpl");
    private static final int MAX_RETRY_COUNT = 5;
    private static final long[] RETRY_DELAYS_MS = {5000, 10000, 20000, 40000, 80000};

    private static class Holder {
        private static final LoginApiImpl INSTANCE = new LoginApiImpl();
    }

    public static LoginApiImpl getInstance() {
        return Holder.INSTANCE;
    }

    private final LoginStateMachine stateMachine;
    private final LoginCoordinator coordinator;
    private final List<LoginCallback> globalLoginCallbacks = new CopyOnWriteArrayList<>();
    private final AtomicReference<LoginCallback> singleLoginCallback = new AtomicReference<>();
    private final AtomicBoolean isRelogging = new AtomicBoolean(false);

    private String currentAddress;
    private int currentPort;
    private String currentUserName;
    private String currentPassword;
    private String currentIdentifyId;

    private LoginApiImpl() {
        this.stateMachine = new LoginStateMachine();
        this.coordinator = new LoginCoordinator();
        this.stateMachine.addStateChangeListener((oldState, newState) -> {
            LOGGER.info("LoginApiImpl: State changed: {} -> {}", oldState, newState);
            LoginCallback singleCb = singleLoginCallback.get();
            executeCallbackAsync(singleCb, cb -> cb.onConnectStatusChanged(oldState, newState),
                    "Single callback status change error");
            for (LoginCallback cb : globalLoginCallbacks) {
                executeCallbackAsync(cb, callback -> callback.onConnectStatusChanged(oldState, newState),
                        "Global callback status change error");
            }
        });
    }

    /**
     * 用户登录（异步）
     *
     * <p>处理流程：
     * <ol>
     *   <li>参数校验：检查 username/password 是否为空</li>
     *   <li>状态检查：如果正在连接中，回调失败</li>
     *   <li>重连判断：如果服务器地址/端口/用户变化，执行重新登录</li>
     *   <li>重复登录检查：如果已登录且凭证相同，回调成功</li>
     *   <li>执行登录：启动异步登录流程</li>
     * </ol>
     *
     * <p>异步登录流程（doLogin）：
     * <ul>
     *   <li>状态流转：IDLE → AUTHING → CONNECTING → CONNECTED</li>
     *   <li>Token获取：调用ACM服务获取临时Token</li>
     *   <li>WebSocket连接：建立长连接并发送认证消息</li>
     *   <li>成功回调：启动Token刷新监控，回调通知业务层</li>
     * </ul>
     *
     * @param param 登录参数（包含用户名、Token及可选配置）
     */
    @Override
    public synchronized void login(LoginParam param) {
        String username = param.getUsername();
        String password = param.getPassword();
        LOGGER.info("LoginApiImpl.login: Login start: username={}, timeoutMs={}, autoReconnect={}, maxRetryCount={}",
                username, param.getTimeoutMs(), param.isAutoReconnect(), param.getMaxRetryCount());
        // 设置 TicketProvider（如果有）
        TicketProvider ticketProvider = param.getTicketProvider();
        if (ticketProvider != null) {
            TokenRefresher.getInstance().setTicketProvider(ticketProvider);
            LOGGER.info("LoginApiImpl.login: TicketProvider set, will use ticket for authentication");
        }
        // 校验 username（必须非空）
        if (StringUtil.isNullOrEmpty(username)) {
            LOGGER.error("LoginApiImpl.login: Login param empty: username={}", username);
            notifyLoginResult(param.getCallback(), false, LoginErrorEnum.ERR_PARAM, "Username cannot be empty");
            return;
        }
        // 只有无 TicketProvider 时才校验 password（有 Ticket 认证方式不需要 password）
        if (ticketProvider == null && StringUtil.isNullOrEmpty(password)) {
            LOGGER.error("LoginApiImpl.login: Login param empty: password is null and no TicketProvider set");
            notifyLoginResult(param.getCallback(), false, LoginErrorEnum.ERR_PARAM, "Password cannot be empty when TicketProvider is not set");
            return;
        }
        if (stateMachine.isConnecting()) {
            LOGGER.warn("LoginApiImpl.login: Already connecting, reject new request, currentState={}", stateMachine.getCurrentState());
            notifyLoginResult(param.getCallback(), false, LoginErrorEnum.ERR_LOGIN_IN_PROGRESS, "Login already in progress");
            return;
        }
        if (shouldReLogin(username)) {
            LOGGER.info("LoginApiImpl.login: Address or user changed, need reLogin");
            singleLoginCallback.set(param.getCallback());
            reLogin(param);
            return;
        }
        if (stateMachine.isConnected()
                || (stateMachine.isDisconnected() && !stateMachine.isIdle())) {
            LOGGER.warn("LoginApiImpl.login: Already logged in, reject new request, currentState={}", stateMachine.getCurrentState());
            notifyLoginResult(param.getCallback(), false, LoginErrorEnum.ERR_ALREADY_LOGGED_IN, "Already logged in");
            return;
        }
        singleLoginCallback.set(param.getCallback());
        doLogin(param);
    }

    /**
     * 执行实际登录流程（异步）
     *
     * <p>执行步骤：
     * <ol>
     *   <li>构建设备标识（IdentifyId）并保存</li>
     *   <li>状态流转：IDLE → AUTHING</li>
     *   <li>启动异步线程执行登录协调器</li>
     * </ol>
     *
     * <p>登录协调器流程（LoginCoordinator）：
     * <ul>
     *   <li>调用 ACM 服务获取 Token（AUTHING 状态）</li>
     *   <li>使用 Token 建立 WebSocket 连接（CONNECTING 状态）</li>
     *   <li>连接成功 → CONNECTED 状态，启动 Token 刷新监控</li>
     * </ul>
     *
     * @param param 登录参数
     */
    private void doLogin(LoginParam param) {
        String username = param.getUsername();
        String password = param.getPassword();
        LoginCallback callback = param.getCallback();
        this.currentUserName = username;
        this.currentPassword = password;
        this.currentIdentifyId = buildIdentifyId(username);
        stateMachine.transition(LoginStatus.AUTHING);
        ThreadPoolManager.getInstance().executeSharedThreadPool(() -> {
            LOGGER.info("LoginApiImpl.doLogin: Starting login flow, username={}, identifyId={}", username, currentIdentifyId);
            coordinator.startLoginFlow(currentIdentifyId, username, password,
                    new LoginFlowCallback() {
                        @Override
                        public void onTokenAcquired(String identifyId, String token) {
                            stateMachine.transition(LoginStatus.CONNECTING);
                        }
                        @Override
                        public void onTokenFailed(LoginErrorEnum error, String message) {
                            stateMachine.transition(LoginStatus.IDLE);
                            resetComponents();
                            notifyLoginResult(callback, false, error, message);
                        }
                        @Override
                        public void onConnected() {
                            onLoginSuccess(username, callback);
                        }
                        @Override
                        public void onDisconnected(int code, String reason) {
                            handleDisconnection(code, reason);
                        }
                        @Override
                        public void onAuthFailed() {
                            TokenManager.getInstance().refreshToken();
                            stateMachine.transition(LoginStatus.IDLE);
                            resetComponents();
                            notifyLoginResult(callback, false, LoginErrorEnum.ERR_LOGIN_AUTH_FAILED, "Auth failed");
                        }
                    });
        });
    }

    /**
     * 构建身份标识ID
     *
     * @param username 用户名
     * @return 身份标识ID
     */
    private String buildIdentifyId(String username) {
        DeviceInfo devInfo = BaseApiImpl.getInstance().getDeviceInfo();
        return String.format("1.%s.%s@AISF.cu.gd.com.cn",
                devInfo.getDeviceType().getValue(),
                username
        );
    }

    /**
     * 登录成功处理
     *
     * <p>处理内容：
     * <ol>
     *   <li>重置重连计数器</li>
     *   <li>记录当前连接信息（服务器地址、端口、用户名）</li>
     *   <li>状态流转：CONNECTING → CONNECTED</li>
     *   <li>发布登录成功事件</li>
     *   <li>回调通知业务层登录成功</li>
     * </ol>
     *
     * @param username 登录成功的用户名
     */
    private void onLoginSuccess(String username, LoginCallback callback) {
        ServerInfo serverInfo = BaseApiImpl.getInstance().getServerInfo();
        currentAddress = serverInfo.getServerIp();
        currentPort = serverInfo.getWebsocketPort();
        currentUserName = username;
        stateMachine.transition(LoginStatus.CONNECTED);
        // 发布登录成功事件
        EventBusManager.getInstance().publish(new LoginStatusEvent(LoginStatus.CONNECTED), EventHandleStrategyEnum.SYNC);
        notifyLoginResult(callback, true, LoginErrorEnum.NO_ERROR, "");
    }

    /**
     * 处理连接断开
     *
     * <p>处理逻辑：
     * <ol>
     *   <li>如果已经是断开状态，直接返回</li>
     *   <li>状态流转到 DISCONNECTED</li>
     *   <li>判断断开原因：
     *     <ul>
     *       <li>正常登出：回调失败（无需重试），重置组件</li>
     *       <li>异常断开：回调失败（需要重试），5秒后自动重连</li>
     *     </ul>
     *   </li>
     *   <li>通知状态变化</li>
     * </ol>
     *
     * @param code   错误码
     * @param reason 断开原因
     */
    private void handleDisconnection(int code, String reason) {
        if (stateMachine.isIdle()) {
            LOGGER.info("LoginApiImpl.handleDisconnection: already idle (user logout), skip reconnect, code={}, reason={}", code, reason);
            return;
        }
        stateMachine.transition(LoginStatus.DISCONNECTED);
        if ("STATUS_NORMAL_DISCONNECTED".equals(reason)) {
            notifyLoginResult(singleLoginCallback.getAndSet(null), false, LoginErrorEnum.LOGIN_FAILURE_NO_RETRY, "Normal logout");
            resetComponents();
        } else {
            notifyLoginResult(singleLoginCallback.getAndSet(null), false, LoginErrorEnum.LOGIN_FAILURE_NEED_RETRY,
                    "Disconnected: " + reason + " (" + code + ")");
            ThreadPoolManager.getInstance().executeIndependentThreadPool(() -> coordinator.reconnect(false));
        }
    }

    /**
     * 用户登出
     *
     * <p>处理流程（同步执行，优先处理）：
     * <ol>
     *   <li>如果已经是空闲状态，直接返回</li>
     *   <li>重置组件（清除连接信息）</li>
     *   <li>同步发送登出消息（不经过队列，直接发送）</li>
     *   <li>状态流转到 IDLE</li>
     *   <li>发布登出事件（TokenManager 订阅此事件清除 Token）</li>
     *   <li>断开 WebSocket 连接</li>
     * </ol>
     *
     * <p><strong>注意：Logout 是优先级最高的操作，必须同步立即执行，
     * 不能进入线程池队列等待。</strong></p>
     */
    @Override
    public void logout() {
        LOGGER.info("LoginApiImpl.logout: Logout start");
        if (stateMachine.isIdle()) {
            LOGGER.info("LoginApiImpl.logout: Already disconnected, skip");
            return;
        }
        LOGGER.info("LoginApiImpl.logout: Resetting components...");
        resetComponents();
        LOGGER.info("LoginApiImpl.logout: Sending logout message...");
        sendLogoutMessageSync();
        LOGGER.info("LoginApiImpl.logout: Transitioning state to IDLE");
        stateMachine.transition(LoginStatus.IDLE);
        // 发布登出事件，TokenManager 订阅此事件清除 Token
        EventBusManager.getInstance().publish(new LoginStatusEvent(LoginStatus.IDLE), EventHandleStrategyEnum.SYNC);
        LOGGER.info("LoginApiImpl.logout: Disconnecting...");
        coordinator.disconnect();
        LOGGER.info("LoginApiImpl.logout: Logout completed successfully");
    }

    /**
     * 发送登出消息（同步直接发送，不经过队列）
     *
     * <p>创建 LogoutMsgInfo 消息并直接通过 MessageProcessor 立即发送，
     * 不经过异步队列，确保 Logout 消息优先立即发送。
     * 发送失败不影响登出流程，仅记录错误日志。
     */
    private void sendLogoutMessageSync() {
        try {
            LogoutMsgInfo logoutMsg = new LogoutMsgInfo();
            boolean sent = MessageProcessor.getInstance().sendImmediately(logoutMsg);
            if (sent) {
                LOGGER.info("LoginApiImpl.sendLogoutMessageSync: Logout message sent immediately");
            } else {
                LOGGER.warn("LoginApiImpl.sendLogoutMessageSync: Failed to send logout message immediately");
            }
        } catch (Exception e) {
            LOGGER.error("LoginApiImpl.sendLogoutMessageSync: Exception while sending logout message", e);
        }
    }

    /**
     * 注册全局登录回调
     *
     * <p>全局回调在每次登录状态变化时都会触发，适用于需要持续监听登录状态的场景。
     * 同一个回调对象不会被重复添加。
     *
     * @param callback 登录回调，为 null 时忽略
     */
    @Override
    public void addLoginCallback(LoginCallback callback) {
        if (callback != null && !globalLoginCallbacks.contains(callback)) {
            globalLoginCallbacks.add(callback);
        }
    }

    /**
     * 注销全局登录回调
     *
     * <p>移除指定的全局回调，移除后该回调将不再接收任何登录状态通知。
     * 若回调不存在则忽略。
     *
     * @param callback 需要移除的登录回调
     */
    @Override
    public void removeLoginCallback(LoginCallback callback) {
        globalLoginCallbacks.remove(callback);
    }

    /**
     * 获取当前登录信息
     *
     * <p>返回当前的登录状态快照，包含：
     * <ul>
     *   <li>loginStatus：当前登录状态（IDLE/AUTHING/CONNECTING/CONNECTED/DISCONNECTED）</li>
     *   <li>username：当前登录的用户名</li>
     *   <li>identifyId：当前会话标识ID</li>
     *   <li>passwordSha256：当前登录密码的SHA256摘要</li>
     * </ul>
     *
     * @return 当前登录信息快照，未登录时各字段为 null
     */
    @Override
    public LoginInfo getLoginInfo() {
        return new LoginInfo(stateMachine.getCurrentState(), currentUserName, currentIdentifyId, currentPassword);
    }

    /**
     * 获取当前有效的 ACM Token
     *
     * <p>执行逻辑：
     * <ol>
     *   <li>若 Token 未过期，直接返回缓存值</li>
     *   <li>若 Token 即将过期或已过期（剩余时间 &le; 3小时），同步执行刷新</li>
     *   <li>刷新成功返回新 Token，刷新失败返回 null</li>
     * </ol>
     *
     * <p><strong>注意：此方法可能阻塞等待 HTTP 请求完成（Token 刷新时），
     * 建议在后台线程调用，避免阻塞主线程。</strong></p>
     *
     * @return 当前有效的 ACM Token；未登录或刷新失败时返回 null
     */
    @Override
    public String getCurrentToken() {
        return TokenManager.getInstance().getCurrentToken();
    }

    /**
     * 获取当前 TokenResult（包含完整 Token 信息）
     *
     * <p>返回完整的 TokenResult 对象，包括：
     * <ul>
     *   <li>token - ACM Token 字符串</li>
     *   <li>expireTime - 有效期（秒）</li>
     *   <li>extraData - ACM 返回的额外数据（Map）</li>
     * </ul>
     *
     * <p>此方法仅返回当前缓存的 Token 信息，不触发刷新操作。
     * 若需要确保获取最新 Token，请使用 {@link #getCurrentToken()}。</p>
     *
     * @return TokenResult 对象；未登录或无 Token 时返回失败的 TokenResult
     */
    @Override
    public TokenResult getTokenResult() {
        return TokenManager.getInstance().getTokenResult();
    }

    /**
     * 判断是否需要重新登录
     *
     * <p>触发重新登录的条件（当已连接时）：
     * <ol>
     *   <li>服务器地址（IP）发生变化</li>
     *   <li>WebSocket 端口发生变化</li>
     *   <li>用户名发生变化</li>
     * </ol>
     *
     * @param username 新的用户名
     * @return true 需要重新登录，false 不需要
     */
    private boolean shouldReLogin(String username) {
        if (!stateMachine.isConnected()) {
            return false;
        }
        ServerInfo serverInfo = BaseApiImpl.getInstance().getServerInfo();
        if (serverInfo == null) {
            return false;
        }
        ServerEnvEnum serverEnv = serverInfo.getServerEnv();
        if (serverEnv == null) {
            return false;
        }
        String newAddress = serverInfo.getServerIp();
        int newPort = serverInfo.getWebsocketPort();
        return !newAddress.equals(currentAddress)
                || newPort != currentPort
                || !username.equals(currentUserName);
    }

    /**
     * 重新登录
     *
     * <p>处理流程：
     * <ol>
     *   <li>非阻塞判定：使用 AtomicBoolean 确保只有一个重登录任务在执行</li>
     *   <li>先执行登出（logout），断开当前连接</li>
     *   <li>等待断开完成（轮询检查状态，最多60秒超时）</li>
     *   <li>重置组件状态（清除配置）</li>
     *   <li>执行新的登录流程（doLogin）</li>
     * </ol>
     *
     * @param param 登录参数
     */
    private void reLogin(LoginParam param) {
        if (!isRelogging.compareAndSet(false, true)) {
            LOGGER.info("LoginApiImpl.reLogin: Another reLogin is running, skip");
            notifyLoginResult(param.getCallback(), false, LoginErrorEnum.ERR_LOGIN_IN_PROGRESS, "ReLogin already in progress");
            return;
        }
        ThreadPoolManager.getInstance().executeIndependentThreadPool(() -> {
            try {
                logout();
                waitForDisconnected();
                resetComponents();
                doLogin(param);
            } finally {
                isRelogging.set(false);
            }
        });
    }

    /**
     * 等待断开完成（轮询检查）
     *
     * <p>使用场景：重新登录时，需要等待旧连接完全断开后再建立新连接。
     *
     * <p>超时机制：最大等待 60 秒，每秒检查一次状态。
     */
    private void waitForDisconnected() {
        long maxWaitTime = 60000;
        long startTime = System.currentTimeMillis();
        while (!stateMachine.isDisconnected()) {
            if (System.currentTimeMillis() - startTime > maxWaitTime) {
                LOGGER.error("LoginApiImpl.waitForDisconnected: Timeout waiting for disconnected");
                break;
            }
            ThreadUtil.sleepNoExcept(1000);
        }
    }

    /**
     * 重置组件状态
     *
     * <p>清理内容：当前连接信息（地址、端口、用户名、Token、IdentifyId）
     *
     * <p>使用场景：
     * <ul>
     *   <li>正常登出后清理资源</li>
     *   <li>重新登录前重置状态</li>
     * </ul>
     */
    private void resetComponents() {
        LOGGER.info("LoginApiImpl.resetComponents: resetComponents start");
        currentAddress = null;
        currentPort = 0;
        currentUserName = null;
        currentPassword = null;
        currentIdentifyId = null;
    }

    /**
     * 异步执行回调操作（带异常捕获）
     *
     * <p>为什么用异步：
     * <ol>
     *   <li>回调执行不应阻塞登录流程</li>
     *   <li>多个回调之间应独立执行，互不等待</li>
     *   <li>防止业务层回调耗时操作影响SDK内部状态流转</li>
     * </ol>
     *
     * @param callback 回调对象（可能为null）
     * @param action   执行的操作
     * @param errorMsg 错误日志前缀
     */
    private void executeCallbackAsync(LoginCallback callback, java.util.function.Consumer<LoginCallback> action, String errorMsg) {
        if (callback == null) {
            return;
        }
        ThreadPoolManager.getInstance().executeSharedThreadPool(() -> {
            try {
                action.accept(callback);
            } catch (Exception e) {
                LOGGER.error("LoginApiImpl.executeCallbackAsync: {}: {}", errorMsg, e.getMessage());
            }
        });
    }

    /**
     * 调用登录回调
     *
     * <p>回调触发时机：
     * <ul>
     *   <li>登录成功：onSuccess(TokenResult)</li>
     *   <li>登录失败：onFailure(code, message)</li>
     * </ul>
     *
     * <p>优先级：
     * <ol>
     *   <li>单次回调（singleCb）- 仅对本次登录有效，由调用方直接传入</li>
     *   <li>全局回调（globalLoginCallbacks）- 所有监听者都会收到</li>
     * </ol>
     *
     * @param singleCb  本次登录的单次回调，可为 null
     * @param isSuccess true 成功，false 失败
     * @param error     错误码枚举
     * @param message   错误信息
     */
    private void notifyLoginResult(LoginCallback singleCb, boolean isSuccess, LoginErrorEnum error, String message) {
        // 登录成功时获取 TokenResult 透传给用户端
        TokenResult tokenResult = isSuccess
                ? TokenManager.getInstance().getTokenResult()
                : TokenResult.failure(message);
        executeCallbackAsync(singleCb, cb -> {
            if (isSuccess) {
                cb.onSuccess(tokenResult);
            } else {
                cb.onFailure(error, message);
            }
        }, "Single login callback error");
        if (globalLoginCallbacks.isEmpty()) {
            LOGGER.warn("LoginApiImpl.notifyLoginResult: No global login callbacks registered");
            return;
        }
        for (LoginCallback cb : globalLoginCallbacks) {
            executeCallbackAsync(cb, callback -> {
                if (isSuccess) {
                    callback.onSuccess(tokenResult);
                } else {
                    callback.onFailure(error, message);
                }
            }, "Global login callback error");
        }
    }
}