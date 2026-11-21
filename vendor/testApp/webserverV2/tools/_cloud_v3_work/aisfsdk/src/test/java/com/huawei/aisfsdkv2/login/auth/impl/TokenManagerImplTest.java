/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.login.auth.impl;

import com.huawei.aisfsdkv2.login.auth.TokenManager;
import com.huawei.aisfsdkv2.login.auth.tokenrefresher.TokenRefresher;
import com.huawei.aisfsdkv2.login.auth.tokenrefresher.TokenResult;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * TokenManagerImpl 单元测试
 */
public class TokenManagerImplTest {

    private MockTokenRefresher mockRefresher;
    private TokenManagerImpl tokenManager;

    @Before
    public void setUp() {
        mockRefresher = new MockTokenRefresher();
        tokenManager = new TokenManagerImpl(mockRefresher);
    }

    @Test
    public void testGetInstance_NotNull() {
        assertNotNull(TokenManager.getInstance());
    }

    @Test
    public void testFetchToken_Success() {
        mockRefresher.setFetchResult(TokenResult.success("test-token", 3600));
        String token = tokenManager.fetchToken("identify-123", "user", "pass");
        assertEquals("test-token", token);
        assertEquals("identify-123", mockRefresher.getLastFetchIdentifyId());
        assertEquals("user", mockRefresher.getLastFetchUsername());
        assertEquals("pass", mockRefresher.getLastFetchPassword());
    }

    @Test
    public void testFetchToken_Failure() {
        mockRefresher.setFetchResult(TokenResult.failure("Network error"));
        String token = tokenManager.fetchToken("identify-123", "user", "pass");
        assertNull(token);
    }

    @Test
    public void testFetchToken_RetryOnFailure() {
        mockRefresher.setFailCount(3);
        mockRefresher.setFetchResult(TokenResult.success("retry-token", 3600));
        String token = tokenManager.fetchToken("identify-456", "user", "pass");
        assertEquals("retry-token", token);
        assertTrue(mockRefresher.getFetchCallCount() >= 3);
    }

    @Test
    public void testGetCurrentToken_AfterFetch() {
        // Use a long expire time (24 hours) to avoid near-expired check triggering refresh
        mockRefresher.setFetchResult(TokenResult.success("current-token", 24 * 3600));
        tokenManager.fetchToken("id", "user", "pass");
        String token = tokenManager.getCurrentToken();
        assertEquals("current-token", token);
    }

    @Test
    public void testGetCurrentToken_NoToken() {
        String token = tokenManager.getCurrentToken();
        assertNull(token);
    }

    @Test
    public void testClearToken() {
        // Use a long expire time (24 hours) to avoid near-expired check
        mockRefresher.setFetchResult(TokenResult.success("clear-test-token", 24 * 3600));
        tokenManager.fetchToken("id", "user", "pass");
        assertNotNull(tokenManager.getCurrentToken());
        tokenManager.clearToken();
        assertNull(tokenManager.getCurrentToken());
    }

    @Test
    public void testRefreshToken() {
        mockRefresher.setFetchResult(TokenResult.success("original-token", 3600));
        mockRefresher.setRefreshResult(TokenResult.success("refreshed-token", 3600));
        tokenManager.fetchToken("id-refresh", "user", "pass");
        tokenManager.refreshToken();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertEquals("id-refresh", mockRefresher.getLastRefreshIdentifyId());
    }

    @Test
    public void testRefreshToken_NoToken() {
        tokenManager.refreshToken();
        assertNull(mockRefresher.getLastRefreshIdentifyId());
    }

    @Test
    public void testFetchToken_NullIdentifyId() {
        mockRefresher.setFetchResult(TokenResult.success("token", 3600));
        String token = tokenManager.fetchToken(null, "user", "pass");
        assertEquals("token", token);
    }

    @Test
    public void testFetchToken_EmptyCredential() {
        mockRefresher.setFetchResult(TokenResult.success("token", 3600));
        String token = tokenManager.fetchToken("id", "", "");
        assertEquals("token", token);
    }

    /**
     * Mock TokenRefresher implementation for testing
     */
    private static class MockTokenRefresher implements TokenRefresher {
        private TokenResult fetchResult;
        private TokenResult refreshResult;
        private int failCount = 0;
        private int fetchCallCount = 0;
        private String lastFetchIdentifyId;
        private String lastFetchUsername;
        private String lastFetchPassword;
        private String lastRefreshIdentifyId;

        void setFetchResult(TokenResult result) {
            this.fetchResult = result;
        }

        void setRefreshResult(TokenResult result) {
            this.refreshResult = result;
        }

        void setFailCount(int count) {
            this.failCount = count;
        }

        int getFetchCallCount() {
            return fetchCallCount;
        }

        String getLastFetchIdentifyId() {
            return lastFetchIdentifyId;
        }

        String getLastFetchUsername() {
            return lastFetchUsername;
        }

        String getLastFetchPassword() {
            return lastFetchPassword;
        }

        String getLastRefreshIdentifyId() {
            return lastRefreshIdentifyId;
        }

        @Override
        public TokenResult fetch(String identifyId, String username, String password) {
            lastFetchIdentifyId = identifyId;
            lastFetchUsername = username;
            lastFetchPassword = password;
            fetchCallCount++;
            if (failCount > 0 && fetchCallCount <= failCount) {
                return TokenResult.failure("Simulated failure " + fetchCallCount);
            }
            return fetchResult;
        }

        @Override
        public TokenResult refresh(String identifyId) {
            lastRefreshIdentifyId = identifyId;
            return refreshResult != null ? refreshResult : TokenResult.success("refreshed", 3600);
        }

        @Override
        public void setTicketProvider(com.huawei.aisfsdkv2.api.login.TicketProvider ticketProvider) {
        }
    }
}
