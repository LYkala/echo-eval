/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.login;

import com.huawei.aisfsdkv2.api.login.LoginErrorEnum;
import com.huawei.aisfsdkv2.login.auth.TokenManager;
import com.huawei.aisfsdkv2.login.auth.tokenrefresher.TokenResult;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * LoginCoordinator 单元测试
 */
public class LoginCoordinatorTest {

    private MockTokenManager mockTokenManager;

    @Before
    public void setUp() {
        mockTokenManager = new MockTokenManager();
    }

    @Test
    public void testConstructor_WithTokenManager() {
        LoginCoordinator coordinator = new LoginCoordinator(mockTokenManager);
        assertNotNull(coordinator);
    }

    @Test
    public void testStartLoginFlow_TokenFailed() {
        mockTokenManager.setToken(null);
        MockLoginFlowCallback callback = new MockLoginFlowCallback();
        LoginCoordinator coordinator = new LoginCoordinator(mockTokenManager);
        coordinator.startLoginFlow("identify-456", "user", "pass", callback);
        assertFalse(callback.tokenAcquired);
        assertTrue(callback.tokenFailed);
        assertEquals(LoginErrorEnum.LOGIN_FAILURE_NEED_RETRY, callback.lastError);
    }

    @Test
    public void testStartLoginFlow_EmptyToken() {
        mockTokenManager.setToken("");
        MockLoginFlowCallback callback = new MockLoginFlowCallback();
        LoginCoordinator coordinator = new LoginCoordinator(mockTokenManager);
        coordinator.startLoginFlow("identify-789", "user", "pass", callback);
        assertFalse(callback.tokenAcquired);
        assertTrue(callback.tokenFailed);
    }

    /**
     * Mock TokenManager
     */
    private static class MockTokenManager implements TokenManager {
        private String token;

        void setToken(String token) {
            this.token = token;
        }

        @Override
        public String fetchToken(String identifyId, String username, String password) {
            return token;
        }

        @Override
        public void refreshToken() {
        }

        @Override
        public String getCurrentToken() {
            return token;
        }

        @Override
        public void clearToken() {
            this.token = null;
        }

        @Override
        public TokenResult getTokenResult() {
            if (token == null || token.isEmpty()) {
                return TokenResult.failure("No token available");
            }
            return TokenResult.success(token, 3600); // 3600秒 = 1小时
        }
    }

    /**
     * Mock LoginFlowCallback
     */
    private static class MockLoginFlowCallback implements LoginFlowCallback {
        boolean tokenAcquired = false;
        boolean tokenFailed = false;
        boolean connected = false;
        boolean disconnected = false;
        boolean authFailed = false;
        String lastIdentifyId;
        String lastToken;
        LoginErrorEnum lastError;
        String lastMessage;

        @Override
        public void onTokenAcquired(String identifyId, String token) {
            this.tokenAcquired = true;
            this.lastIdentifyId = identifyId;
            this.lastToken = token;
        }

        @Override
        public void onTokenFailed(LoginErrorEnum error, String message) {
            this.tokenFailed = true;
            this.lastError = error;
            this.lastMessage = message;
        }

        @Override
        public void onConnected() {
            this.connected = true;
        }

        @Override
        public void onDisconnected(int code, String reason) {
            this.disconnected = true;
        }

        @Override
        public void onAuthFailed() {
            this.authFailed = true;
        }
    }
}
