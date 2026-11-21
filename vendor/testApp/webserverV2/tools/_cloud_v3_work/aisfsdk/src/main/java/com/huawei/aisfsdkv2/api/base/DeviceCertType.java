/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.base;

/**
 * 设备证书类型枚举
 *
 * <p>用于指定设备证书的格式，影响证书和私钥的加载方式：
 * <ul>
 *   <li>{@link #NONE} - 无设备证书，仅使用CA证书进行单向认证</li>
 *   <li>{@link #PEM} - PEM格式，证书和私钥分开存储（deviceCertPath + deviceKeyPath）</li>
 *   <li>{@link #PKCS12} - PKCS12格式，证书和私钥在同一文件中（deviceCertPath），需要口令</li>
 * </ul>
 */
public enum DeviceCertType {

    /** 无设备证书（单向认证） */
    NONE,

    /** PEM格式证书（证书和私钥分开存储） */
    PEM,

    /** PKCS12格式证书库（证书和私钥在同一文件，需要口令） */
    PKCS12
}
