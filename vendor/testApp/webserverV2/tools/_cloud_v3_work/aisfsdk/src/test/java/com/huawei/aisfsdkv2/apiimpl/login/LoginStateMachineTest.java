/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.login;

import com.huawei.aisfsdkv2.api.login.LoginStatus;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * LoginStateMachine 状态机测试
 */
public class LoginStateMachineTest {

    private LoginStateMachine machine;

    @Before
    public void setUp() {
        machine = new LoginStateMachine();
    }

    // ——— 初始状态 ———

    @Test
    public void testInitialStateIsIdle() {
        assertEquals(LoginStatus.IDLE, machine.getCurrentState());
    }

    @Test
    public void testIsIdleInitially() {
        assertTrue(machine.isIdle());
    }

    @Test
    public void testIsNotConnectingInitially() {
        assertFalse(machine.isConnecting());
    }

    @Test
    public void testIsNotConnectedInitially() {
        assertFalse(machine.isConnected());
    }

    @Test
    public void testIsDisconnectedWhenIdle() {
        assertTrue(machine.isDisconnected());
    }

    // ——— 合法转换：IDLE ———

    @Test
    public void testIdleToAuthing() {
        assertTrue(machine.transition(LoginStatus.AUTHING));
        assertEquals(LoginStatus.AUTHING, machine.getCurrentState());
    }

    @Test
    public void testIdleToConnectedIsInvalid() {
        assertFalse(machine.transition(LoginStatus.CONNECTED));
        assertEquals(LoginStatus.IDLE, machine.getCurrentState());
    }

    @Test
    public void testIdleToDisconnectedIsInvalid() {
        assertFalse(machine.transition(LoginStatus.DISCONNECTED));
    }

    // ——— 合法转换：AUTHING ———

    @Test
    public void testAuthingToConnecting() {
        machine.transition(LoginStatus.AUTHING);
        assertTrue(machine.transition(LoginStatus.CONNECTING));
        assertEquals(LoginStatus.CONNECTING, machine.getCurrentState());
    }

    @Test
    public void testAuthingToDisconnected() {
        machine.transition(LoginStatus.AUTHING);
        assertTrue(machine.transition(LoginStatus.DISCONNECTED));
        assertEquals(LoginStatus.DISCONNECTED, machine.getCurrentState());
    }

    @Test
    public void testAuthingToConnectedIsInvalid() {
        machine.transition(LoginStatus.AUTHING);
        assertFalse(machine.transition(LoginStatus.CONNECTED));
    }

    // ——— 合法转换：CONNECTING ———

    @Test
    public void testConnectingToConnected() {
        machine.transition(LoginStatus.AUTHING);
        machine.transition(LoginStatus.CONNECTING);
        assertTrue(machine.transition(LoginStatus.CONNECTED));
        assertEquals(LoginStatus.CONNECTED, machine.getCurrentState());
    }

    @Test
    public void testConnectingToDisconnected() {
        machine.transition(LoginStatus.AUTHING);
        machine.transition(LoginStatus.CONNECTING);
        assertTrue(machine.transition(LoginStatus.DISCONNECTED));
    }

    @Test
    public void testConnectingToAuthingIsInvalid() {
        machine.transition(LoginStatus.AUTHING);
        machine.transition(LoginStatus.CONNECTING);
        assertFalse(machine.transition(LoginStatus.AUTHING));
    }

    // ——— 合法转换：CONNECTED ———

    @Test
    public void testConnectedToDisconnected() {
        goToConnected();
        assertTrue(machine.transition(LoginStatus.DISCONNECTED));
        assertEquals(LoginStatus.DISCONNECTED, machine.getCurrentState());
    }

    @Test
    public void testConnectedToAuthingIsInvalid() {
        goToConnected();
        assertFalse(machine.transition(LoginStatus.AUTHING));
    }

    // ——— 合法转换：DISCONNECTED ———

    @Test
    public void testDisconnectedToAuthing() {
        goToDisconnected();
        assertTrue(machine.transition(LoginStatus.AUTHING));
    }

    @Test
    public void testDisconnectedToIdle() {
        goToDisconnected();
        assertTrue(machine.transition(LoginStatus.IDLE));
    }

    @Test
    public void testDisconnectedToConnected() {
        goToDisconnected();
        assertTrue(machine.transition(LoginStatus.CONNECTED));
    }

    @Test
    public void testDisconnectedToConnectingIsInvalid() {
        goToDisconnected();
        assertFalse(machine.transition(LoginStatus.CONNECTING));
    }

    // ——— isConnecting 检查 ———

    @Test
    public void testIsConnectingWhenAuthing() {
        machine.transition(LoginStatus.AUTHING);
        assertTrue(machine.isConnecting());
    }

    @Test
    public void testIsConnectingWhenConnecting() {
        machine.transition(LoginStatus.AUTHING);
        machine.transition(LoginStatus.CONNECTING);
        assertTrue(machine.isConnecting());
    }

    @Test
    public void testIsNotConnectingWhenConnected() {
        goToConnected();
        assertFalse(machine.isConnecting());
    }

    // ——— isConnected 检查 ———

    @Test
    public void testIsConnectedWhenConnected() {
        goToConnected();
        assertTrue(machine.isConnected());
    }

    @Test
    public void testIsNotConnectedWhenDisconnected() {
        goToDisconnected();
        assertFalse(machine.isConnected());
    }

    // ——— isDisconnected 检查 ———

    @Test
    public void testIsDisconnectedAfterDisconnect() {
        goToDisconnected();
        assertTrue(machine.isDisconnected());
    }

    @Test
    public void testIsNotDisconnectedWhenConnected() {
        goToConnected();
        assertFalse(machine.isDisconnected());
    }

    // ——— 状态变更监听器 ———

    @Test
    public void testListenerCalledOnTransition() {
        List<LoginStatus> oldStates = new ArrayList<>();
        List<LoginStatus> newStates = new ArrayList<>();
        machine.addStateChangeListener((oldState, newState) -> {
            oldStates.add(oldState);
            newStates.add(newState);
        });
        machine.transition(LoginStatus.AUTHING);
        assertEquals(1, oldStates.size());
        assertEquals(LoginStatus.IDLE, oldStates.get(0));
        assertEquals(LoginStatus.AUTHING, newStates.get(0));
    }

    @Test
    public void testListenerNotCalledOnInvalidTransition() {
        List<LoginStatus> called = new ArrayList<>();
        machine.addStateChangeListener((oldState, newState) -> called.add(newState));
        machine.transition(LoginStatus.CONNECTED); // IDLE->CONNECTED 非法
        assertTrue(called.isEmpty());
    }

    @Test
    public void testMultipleListeners() {
        List<String> log = new ArrayList<>();
        machine.addStateChangeListener((o, n) -> log.add("listener1"));
        machine.addStateChangeListener((o, n) -> log.add("listener2"));
        machine.transition(LoginStatus.AUTHING);
        assertEquals(2, log.size());
    }

    @Test
    public void testNullListenerIgnored() {
        machine.addStateChangeListener(null);
        // 不应抛异常
        assertTrue(machine.transition(LoginStatus.AUTHING));
    }

    @Test
    public void testListenerReceivesCorrectStates() {
        List<LoginStatus[]> events = new ArrayList<>();
        machine.addStateChangeListener((o, n) -> events.add(new LoginStatus[]{o, n}));
        machine.transition(LoginStatus.AUTHING);
        machine.transition(LoginStatus.CONNECTING);
        machine.transition(LoginStatus.CONNECTED);
        assertEquals(3, events.size());
        assertEquals(LoginStatus.IDLE, events.get(0)[0]);
        assertEquals(LoginStatus.AUTHING, events.get(0)[1]);
        assertEquals(LoginStatus.AUTHING, events.get(1)[0]);
        assertEquals(LoginStatus.CONNECTING, events.get(1)[1]);
        assertEquals(LoginStatus.CONNECTING, events.get(2)[0]);
        assertEquals(LoginStatus.CONNECTED, events.get(2)[1]);
    }

    // ——— 完整登录流程 ———

    @Test
    public void testFullLoginFlow() {
        assertTrue(machine.transition(LoginStatus.AUTHING));
        assertTrue(machine.transition(LoginStatus.CONNECTING));
        assertTrue(machine.transition(LoginStatus.CONNECTED));
        assertTrue(machine.isConnected());
    }

    @Test
    public void testReconnectFlow() {
        goToConnected();
        assertTrue(machine.transition(LoginStatus.DISCONNECTED));
        assertTrue(machine.transition(LoginStatus.AUTHING));
        assertTrue(machine.transition(LoginStatus.CONNECTING));
        assertTrue(machine.transition(LoginStatus.CONNECTED));
    }

    // ——— 辅助方法 ———

    private void goToConnected() {
        machine.transition(LoginStatus.AUTHING);
        machine.transition(LoginStatus.CONNECTING);
        machine.transition(LoginStatus.CONNECTED);
    }

    private void goToDisconnected() {
        goToConnected();
        machine.transition(LoginStatus.DISCONNECTED);
    }
}
