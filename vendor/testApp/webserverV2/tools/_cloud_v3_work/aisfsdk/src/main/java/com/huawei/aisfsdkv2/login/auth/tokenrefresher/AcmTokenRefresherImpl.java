/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.login.auth.tokenrefresher;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.login.LoginInfo;
import com.huawei.aisfsdkv2.api.login.TicketProvider;
import com.huawei.aisfsdkv2.component.facade.ACMFacade;
import com.huawei.aisfsdkv2.component.facade.model.acm.AuthResult;
import com.huawei.aisfsdkv2.component.facade.model.acm.AcmException;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

/**
 * ACM Token 刷新实现（单例）
 *
 * <p>负责向 ACM 服务发起 Token 获取请求，支持两种方式：
 * <ul>
 *   <li>Ticket 方式：设置了 {@link TicketProvider} 时优先使用 Ticket 换取 Token</li>
 *   <li>账号密码方式：使用 username/password 向 ACM 认证接口获取 Token</li>
 * </ul>
 *
 * <p>本类不维护缓存，缓存和过期管理由上层 {@link com.huawei.aisfsdkv2.login.auth.TokenManager} 负责。
 */
public class AcmTokenRefresherImpl implements TokenRefresher {
    private static final Logger LOGGER = LoggerFactory.getLogger("AcmTokenRefresherImpl");

    private static class Holder {
        private static final AcmTokenRefresherImpl INSTANCE = new AcmTokenRefresherImpl();
    }

    /**
     * 获取单例实例
     *
     * @return AcmTokenRefresherImpl 单例
     */
    public static AcmTokenRefresherImpl getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Ticket 提供器（可选）
     */
    private volatile TicketProvider ticketProvider;

    private AcmTokenRefresherImpl() {
    }

    /**
     * 设置 Ticket 提供器
     *
     * @param ticketProvider Ticket 提供器
     */
    @Override
    public void setTicketProvider(TicketProvider ticketProvider) {
        this.ticketProvider = ticketProvider;
        LOGGER.info("AcmTokenRefresherImpl.setTicketProvider: ticketProvider set");
    }

    /**
     * 从 ACM 服务获取 Token
     *
     * <p>获取方式优先级：
     * <ol>
     *   <li>若设置了 TicketProvider，使用 Ticket 换取 Token</li>
     *   <li>否则使用 username/password 向 ACM 认证接口获取 Token</li>
     * </ol>
     *
     * @param identifyId 设备标识 ID
     * @param username   用户名
     * @param password   密码
     * @return Token 获取结果
     */
    @Override
    public TokenResult fetch(String identifyId, String username, String password) {
        if (ticketProvider != null) {
            return doFetchByTicket();
        }
        return doFetchFromAcm(identifyId, username, password);
    }

    /**
     * 刷新 Token
     *
     * <p>ACM 无专门刷新接口，复用 fetch 逻辑：
     * <ol>
     *   <li>若设置了 TicketProvider，使用 Ticket 换取新 Token</li>
     *   <li>否则从当前登录信息中取凭据，重新向 ACM 认证接口获取 Token</li>
     * </ol>
     *
     * @param identifyId 设备标识 ID
     * @return Token 刷新结果
     */
    @Override
    public TokenResult refresh(String identifyId) {
        LOGGER.info("AcmTokenRefresherImpl.refresh: Force refreshing token from ACM");
        if (ticketProvider != null) {
            return doFetchByTicket();
        }
        LoginInfo loginInfo = AISFSDKClient.getLoginApi().getLoginInfo();
        String username = loginInfo.getUsername();
        String password = loginInfo.getPassword();
        if (username == null || username.isEmpty() || password == null) {
            LOGGER.error("AcmTokenRefresherImpl.refresh: No credentials available for refresh");
            return TokenResult.failure("No credentials available, please login again");
        }
        return doFetchFromAcm(identifyId, username, password);
    }

    /**
     * 使用 Ticket 从 ACM 服务获取 Token
     *
     * @return Token 获取结果
     */
    private TokenResult doFetchByTicket() {
        LOGGER.info("AcmTokenRefresherImpl.doFetchByTicket: Fetching token using ticket");
        try {
            String ticket = ticketProvider.getTicket();
            if (ticket == null || ticket.isEmpty()) {
                LOGGER.error("AcmTokenRefresherImpl.doFetchByTicket: Ticket is null or empty");
                return TokenResult.failure("Ticket is null or empty");
            }
            TokenResult result = ACMFacade.getInstance().authByTicket(ticket);
            if (!result.isSuccess()) {
                LOGGER.error("AcmTokenRefresherImpl.doFetchByTicket: Failed to get token from ACM, msg={}", result.getMessage());
                return result;
            }
            LOGGER.info("AcmTokenRefresherImpl.doFetchByTicket: Token acquired successfully");
            return result;
        } catch (Exception e) {
            LOGGER.error("AcmTokenRefresherImpl.doFetchByTicket: Exception: {}", e.getMessage());
            return TokenResult.failure(e.getMessage());
        }
    }

    /**
     * 使用账号密码从 ACM 服务获取 Token
     *
     * @param identifyId 设备标识 ID
     * @param username   用户名
     * @param password   密码
     * @return Token 获取结果
     */
    private TokenResult doFetchFromAcm(String identifyId, String username, String password) {
        LOGGER.info("AcmTokenRefresherImpl.doFetchFromAcm: Fetching token from ACM, identifyId={}, username={}",
                identifyId, username);
        try {
            AuthResult authResult = ACMFacade.getInstance().auth(identifyId, username, password);
            LOGGER.info("AcmTokenRefresherImpl.doFetchFromAcm: Token acquired successfully, expireTime={}s",
                    authResult.getExpireTime());
            return TokenResult.success(authResult.getToken(), authResult.getExpireTime());
        } catch (AcmException e) {
            LOGGER.error("AcmTokenRefresherImpl.doFetchFromAcm: Failed to get token: {}", e.getMessage());
            return TokenResult.failure(e.getMessage());
        }
    }
}
