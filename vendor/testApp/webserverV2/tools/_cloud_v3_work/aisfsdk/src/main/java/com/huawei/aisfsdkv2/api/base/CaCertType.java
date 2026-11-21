/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.base;

/**
 * CA证书类型枚举
 *
 * <p>用于指定CA证书的格式，影响证书的加载方式：
 * <ul>
 *   <li>{@link #NONE} - 无CA证书，跳过服务端证书校验（信任所有）</li>
 *   <li>{@link #X509} - X.509格式CA证书，用于校验服务端证书</li>
 * </ul>
 */
public enum CaCertType {

    /** 无CA证书（信任所有服务端证书） */
    NONE,

    /** X.509格式CA证书 */
    X509
}
