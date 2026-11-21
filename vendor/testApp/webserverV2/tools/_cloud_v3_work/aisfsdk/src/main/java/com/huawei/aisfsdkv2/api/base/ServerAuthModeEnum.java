/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.base;

/**
 * 服务器鉴权模式枚举
 * <p>标识 SDK 与服务器之间的鉴权方式，影响 TLS 配置及后续 Token 获取流程。</p>
 *
 * @author l00504561
 * @since 2026-04-08
 */
public enum ServerAuthModeEnum {

    /**
     * 通过 API 网关鉴权，启用 TLS
     */
    APIGW(true),

    /**
     * 通过 ACM 鉴权，不启用 TLS
     */
    ACM(true);

    private final boolean tls;

    ServerAuthModeEnum(boolean tls) {
        this.tls = tls;
    }

    /**
     * 是否启用 TLS
     *
     * @return true 启用，false 不启用
     */
    public boolean isTls() {
        return tls;
    }
}
