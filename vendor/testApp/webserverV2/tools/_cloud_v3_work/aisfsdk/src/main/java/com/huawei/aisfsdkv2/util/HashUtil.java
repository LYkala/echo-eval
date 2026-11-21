/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 哈希工具类
 * 提供 SHA256 等哈希算法
 */
public class HashUtil {

    private static final String SHA256_ALGORITHM = "SHA-256";

    /**
     * 计算字符串的 SHA256 哈希值
     *
     * @param input 输入字符串
     * @return SHA256 哈希值（小写十六进制），输入为空则返回空字符串
     */
    public static String sha256(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA256_ALGORITHM);
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 算法在所有 Java 平台都必须支持
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * 将字节数组转换为小写十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
