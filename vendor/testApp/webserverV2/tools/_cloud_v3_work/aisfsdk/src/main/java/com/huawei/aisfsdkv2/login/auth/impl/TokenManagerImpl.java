/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.login.auth.impl;

import com.huawei.aisfsdkv2.component.eventbus.EventBusManager;
import com.huawei.aisfsdkv2.component.eventbus.event.LoginStatusEvent;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.util.ThreadUtil;
import com.huawei.aisfsdkv2.login.auth.TokenManager;
import com.huawei.aisfsdkv2.login.auth.tokenrefresher.TokenRefresher;
import com.huawei.aisfsdkv2.login.auth.tokenrefresher.TokenResult;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Token 管理器实现类
 *
 * <p>负责 ACM Token 的完整生命周期管理：
 * <ul>
 *   <li>首次获取：登录时通过 {@link #fetchToken} 带重试地获取 Token</li>
 *   <li>定时检查：构造时启动定时任务，每小时检查 Token 剩余有效期</li>
 *   <li>主动刷新：剩余有效期不足 {@value #EXPIRE_THRESHOLD_MS}ms 时自动刷新</li>
 *   <li>清理：登出时通过 {@link #clearToken} 清除所有 Token 信息</li>
 * </ul>
 *
 * <p>单例通过 {@link #getInstance()} 获取，也支持通过构造函数注入 {@link TokenRefresher} 用于测试。
 */
public class TokenManagerImpl implements TokenManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("TokenManagerImpl");

    // 配置常量
    private static final int MAX_RETRY_TIMES = 10;
    private static final long RETRY_INTERVAL_MS = 3000;
    private static final long CHECK_INTERVAL_MS = 60 * 60 * 1000; // 1小时
    private static final long EXPIRE_THRESHOLD_MS = 3 * 60 * 60 * 1000; // 3小时
    private static final long INITIAL_DELAY_MS = 20 * 60 * 1000; // 20分钟

    private static class Holder {
        private static final TokenManager INSTANCE =
                new TokenManagerImpl(TokenRefresher.getInstance());
    }

    /**
     * 获取单例实例
     *
     * @return TokenManager 实例
     */
    public static TokenManager getInstance() {
        return Holder.INSTANCE;
    }

    private final TokenRefresher refresher;

    private volatile String acmToken;
    private volatile int expireTime;
    private volatile long recordTimestamp;
    private volatile String identifyId;
    private volatile Map<String, String> extraData;

    /**
     * 构造函数，注入TokenRefresher依赖，并自动启动Token刷新监控定时任务
     *
     * @param refresher Token刷新器
     */
    public TokenManagerImpl(TokenRefresher refresher) {
        this.refresher = refresher;
        ThreadPoolManager.getInstance().addScheduleTaskIndependentThreadPool(
                this::checkAndRefresh, INITIAL_DELAY_MS, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        LOGGER.info("TokenManagerImpl: Token refresh monitor started");
        // 订阅登出事件，收到事件时清除 Token
        EventBusManager.getInstance().subscribeEvent(event -> {
            if (event instanceof LoginStatusEvent && ((LoginStatusEvent) event).isLoggedOut()) {
                clearToken();
            }
        }, new LoginStatusEvent(com.huawei.aisfsdkv2.api.login.LoginStatus.IDLE));
    }

    /**
     * 获取 ACM Token（同步，带重试）
     *
     * <p>保存 identifyId 供后续 {@link #refreshToken()} 使用，
     * 然后调用 {@link #doFetchWithRetry} 最多重试 {@value #MAX_RETRY_TIMES} 次。
     *
     * @param identifyId 设备标识 ID，格式为 "1.{deviceType}.{username}_{uuid}@AISF.cu.gd.com.cn"
     * @param username   用户名
     * @param password   密码
     * @return 获取成功的 ACM Token；超出最大重试次数或线程被中断时返回 null
     */
    @Override
    public String fetchToken(String identifyId, String username, String password) {
        // 保存 identifyId 用于后续刷新
        this.identifyId = identifyId;
        return doFetchWithRetry(identifyId, username, password);
    }

    /**
     * 带重试的Token获取
     *
     * @param identifyId 设备标识ID
     * @param username   用户名
     * @param password   密码
     * @return ACM Token，获取失败返回 null
     */
    private String doFetchWithRetry(String identifyId, String username, String password) {
        for (int attempt = 1; attempt <= MAX_RETRY_TIMES; attempt++) {
            TokenResult result = refresher.fetch(identifyId, username, password);
            if (result.isSuccess()) {
                saveToken(result);
                LOGGER.info("TokenManagerImpl.doFetchWithRetry: Get token success, expireTime: {}s", expireTime);
                return acmToken;
            }
            LOGGER.warn("TokenManagerImpl.doFetchWithRetry: Get token failed, attempt {}/{}: {}",
                    attempt, MAX_RETRY_TIMES, result.getMessage());
            if (attempt < MAX_RETRY_TIMES) {
                ThreadUtil.sleepNoExcept(RETRY_INTERVAL_MS);
            }
        }
        LOGGER.error("TokenManagerImpl.doFetchWithRetry: Get token over max retry times");
        return null;
    }

    /**
     * 保存Token及相关信息
     *
     * @param result 获取成功的 TokenResult
     */
    private void saveToken(TokenResult result) {
        this.acmToken = result.getToken();
        this.expireTime = result.getExpireTime();
        this.extraData = result.getExtraData();
        this.recordTimestamp = System.currentTimeMillis();
    }

    /**
     * 检查并刷新 Token（定时任务入口）
     *
     * <p>执行逻辑：
     * <ol>
     *   <li>若 identifyId 为 null，说明 SDK 尚未完成登录初始化，跳过</li>
     *   <li>若当前无 Token，说明未登录或登录失败，由登录流程负责，跳过</li>
     *   <li>若 Token 即将过期（剩余时间 &le; {@value #EXPIRE_THRESHOLD_MS}ms），触发 {@link #refreshToken()}</li>
     * </ol>
     *
     * <p>由构造函数注册的定时任务驱动，首次延迟 {@value #INITIAL_DELAY_MS}ms，
     * 之后每 {@value #CHECK_INTERVAL_MS}ms 执行一次。
     */
    private void checkAndRefresh() {
        if (identifyId == null) {
            LOGGER.debug("TokenManagerImpl.checkAndRefresh: SDK not initialized (identifyId is null), skip");
            return;
        }
        if (acmToken == null || acmToken.isEmpty()) {
            LOGGER.debug("TokenManagerImpl.checkAndRefresh: No token available, skip (wait for login)");
            return;
        }
        if (isNearExpired()) {
            LOGGER.info("TokenManagerImpl.checkAndRefresh: Token will expire soon, refreshing...");
            refreshToken();
        }
    }

    /**
     * 判断 Token 是否即将过期
     *
     * <p>根据 Token 获取时间（{@code recordTimestamp}）和服务端返回的有效期（{@code expireTime}）
     * 计算剩余时间，若剩余时间不足 {@value #EXPIRE_THRESHOLD_MS}ms 则认为即将过期。
     *
     * <p>以下情况直接返回 true（视为已过期）：
     * <ul>
     *   <li>{@code recordTimestamp} 为 0（未记录）</li>
     *   <li>{@code expireTime} &le; 0</li>
     * </ul>
     *
     * @return true 表示即将过期或无法判断，false 表示仍有足够有效期
     */
    private boolean isNearExpired() {
        if (recordTimestamp == 0 || expireTime <= 0) {
            return true;
        }
        long expireTimestamp = recordTimestamp + expireTime * 1000L;
        long remaining = expireTimestamp - System.currentTimeMillis();
        LOGGER.debug("TokenManagerImpl.isNearExpired: Token remaining time: {}ms", remaining);
        return remaining <= EXPIRE_THRESHOLD_MS;
    }

    /**
     * 异步刷新 Token
     *
     * <p>在独立线程池中执行，避免阻塞调用方。流程：
     * <ol>
     *   <li>校验当前 Token 和 identifyId 不为空</li>
     *   <li>调用 {@link TokenRefresher#refresh} 换取新 Token</li>
     *   <li>成功则通过 {@link #saveToken} 更新本地缓存，并发送新 Token 到服务端</li>
     *   <li>失败则记录错误日志，不影响当前连接</li>
     * </ol>
     */
    @Override
    public void refreshToken() {
        ThreadPoolManager.getInstance().executeIndependentThreadPool(() -> {
            String currentToken = this.acmToken;
            if (currentToken == null || currentToken.isEmpty()) {
                LOGGER.error("TokenManagerImpl.refreshToken: No token to refresh");
                return;
            }
            String currentIdentifyId = this.identifyId;
            if (currentIdentifyId == null || currentIdentifyId.isEmpty()) {
                LOGGER.error("TokenManagerImpl.refreshToken: No identifyId available for refresh");
                return;
            }
            TokenResult result = refresher.refresh(currentIdentifyId);
            if (result.isSuccess()) {
                saveToken(result);
                LOGGER.info("TokenManagerImpl.refreshToken: Token refreshed successfully");
            } else {
                LOGGER.error("TokenManagerImpl.refreshToken: Token refresh failed: {}", result.getMessage());
            }
        });
    }

    /**
     * 获取当前有效的 ACM Token（同步）
     *
     * <p>执行逻辑：
     * <ol>
     *   <li>若 Token 未过期，直接返回缓存值</li>
     *   <li>若 Token 即将过期或已过期，同步执行刷新</li>
     *   <li>刷新成功返回新 Token，刷新失败返回 null</li>
     * </ol>
     *
     * @return 有效的 ACM Token；无 Token 或刷新失败时返回 null
     */
    @Override
    public String getCurrentToken() {
        if (acmToken == null || acmToken.isEmpty()) {
            LOGGER.warn("TokenManagerImpl.getCurrentToken: No token available");
            return null;
        }
        if (!isNearExpired()) {
            return acmToken;
        }
        LOGGER.info("TokenManagerImpl.getCurrentToken: Token near expired, refreshing synchronously");
        String currentIdentifyId = this.identifyId;
        if (currentIdentifyId == null || currentIdentifyId.isEmpty()) {
            LOGGER.error("TokenManagerImpl.getCurrentToken: No identifyId available for refresh");
            return null;
        }
        TokenResult result = refresher.refresh(currentIdentifyId);
        if (result.isSuccess()) {
            saveToken(result);
            LOGGER.info("TokenManagerImpl.getCurrentToken: Token refreshed successfully");
            return acmToken;
        }
        LOGGER.error("TokenManagerImpl.getCurrentToken: Token refresh failed: {}", result.getMessage());
        return null;
    }

    /**
     * 获取当前Token结果（包含extraData）
     *
     * <p>返回完整的 TokenResult 对象，包括 token、expireTime、extraData，
     * 用于在登录成功回调中透传给用户端。</p>
     *
     * @return TokenResult 对象；未登录或无Token时返回失败的TokenResult
     */
    @Override
    public TokenResult getTokenResult() {
        if (acmToken == null || acmToken.isEmpty()) {
            LOGGER.warn("TokenManagerImpl.getTokenResult: No token available, returning empty TokenResult");
            return TokenResult.failure("No token available");
        }
        Map<String, String> data = extraData != null ? extraData : new HashMap<>();
        LOGGER.debug("TokenManagerImpl.getTokenResult: Returning TokenResult with extraData size={}", data.size());
        return TokenResult.success(acmToken, expireTime, data);
    }

    /**
     * 清除所有 Token 相关缓存数据
     *
     * <p>将 acmToken、expireTime、recordTimestamp、identifyId、extraData 全部重置，
     * 通常在用户登出后调用，确保下次登录重新获取 Token。
     */
    @Override
    public void clearToken() {
        this.acmToken = null;
        this.expireTime = 0;
        this.recordTimestamp = 0;
        this.identifyId = null;
        this.extraData = null;
        LOGGER.info("TokenManagerImpl.clearToken: Token data cleared");
    }
}
