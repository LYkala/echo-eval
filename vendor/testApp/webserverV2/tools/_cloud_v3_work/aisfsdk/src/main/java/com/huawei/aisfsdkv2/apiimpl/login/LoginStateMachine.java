/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.login;

import com.huawei.aisfsdkv2.api.login.LoginStatus;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 登录状态机
 * 管理登录状态的转换和校验
 */
class LoginStateMachine {

    /**
     * 状态变更监听器
     */
    interface StateChangeListener {
        /**
         * 状态变更回调
         *
         * @param oldState 变更前的状态
         * @param newState 变更后的状态
         */
        void onStateChanged(LoginStatus oldState, LoginStatus newState);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("LoginStateMachine");

    /**
     * 状态转换矩阵（定义合法的状态转换）
     */
    private static final Map<LoginStatus, Set<LoginStatus>> VALID_TRANSITIONS;

    static {
        Map<LoginStatus, Set<LoginStatus>> map = new HashMap<>();
        map.put(LoginStatus.IDLE, EnumSet.of(LoginStatus.AUTHING));
        map.put(LoginStatus.AUTHING, EnumSet.of(LoginStatus.CONNECTING, LoginStatus.DISCONNECTED, LoginStatus.IDLE));
        map.put(LoginStatus.CONNECTING, EnumSet.of(LoginStatus.CONNECTED, LoginStatus.DISCONNECTED, LoginStatus.IDLE));
        map.put(LoginStatus.CONNECTED, EnumSet.of(LoginStatus.DISCONNECTED, LoginStatus.IDLE));
        map.put(LoginStatus.DISCONNECTED, EnumSet.of(LoginStatus.AUTHING, LoginStatus.IDLE, LoginStatus.CONNECTED));
        VALID_TRANSITIONS = Collections.unmodifiableMap(map);
    }

    private final AtomicReference<LoginStatus> currentState = new AtomicReference<>(LoginStatus.IDLE);
    private final List<StateChangeListener> stateChangeListeners = new CopyOnWriteArrayList<>();

    /**
     * 注册状态变更监听器
     *
     * @param listener 监听器，为 null 时忽略
     */
    void addStateChangeListener(StateChangeListener listener) {
        if (listener != null) {
            stateChangeListeners.add(listener);
        }
    }

    /**
     * 状态转换
     *
     * @param newState 目标状态
     * @return 是否转换成功
     */
    boolean transition(LoginStatus newState) {
        LoginStatus oldState = currentState.get();

        if (!isValidTransition(oldState, newState)) {
            LOGGER.warn("LoginStateMachine.transition: Invalid state transition: {} -> {}", oldState, newState);
            return false;
        }

        if (currentState.compareAndSet(oldState, newState)) {
            LOGGER.info("LoginStateMachine.transition: State changed: {} -> {}", oldState, newState);
            for (StateChangeListener listener : stateChangeListeners) {
                listener.onStateChanged(oldState, newState);
            }
            return true;
        }
        return false;
    }

    /**
     * 检查状态转换是否合法
     *
     * <p>允许同状态转换（幂等），其他转换需符合状态转换矩阵。</p>
     */
    private boolean isValidTransition(LoginStatus from, LoginStatus to) {
        if (from == to) {
            return true;
        }
        Set<LoginStatus> validTargets = VALID_TRANSITIONS.get(from);
        return validTargets != null && validTargets.contains(to);
    }

    /**
     * 获取当前状态
     */
    LoginStatus getCurrentState() {
        return currentState.get();
    }

    /**
     * 是否正在连接中（AUTHING 或 CONNECTING）
     */
    boolean isConnecting() {
        LoginStatus s = currentState.get();
        return s == LoginStatus.AUTHING || s == LoginStatus.CONNECTING;
    }

    /**
     * 是否已连接
     */
    boolean isConnected() {
        return currentState.get() == LoginStatus.CONNECTED;
    }

    /**
     * 是否已断开
     */
    boolean isDisconnected() {
        LoginStatus s = currentState.get();
        return s == LoginStatus.DISCONNECTED || s == LoginStatus.IDLE;
    }

    /**
     * 是否为初始状态（从未登录）
     */
    boolean isIdle() {
        return currentState.get() == LoginStatus.IDLE;
    }
}
