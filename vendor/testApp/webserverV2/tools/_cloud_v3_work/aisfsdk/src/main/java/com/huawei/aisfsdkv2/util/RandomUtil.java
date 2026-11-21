/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * 随机数工具类
 *
 * <p>提供基于安全随机数的ID生成能力。</p>
 */
public class RandomUtil {

    private static final int SESSION_ID_RANDOM_BYTES = 24;

    private static final int UUID_RANDOM_BYTES = 24;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private RandomUtil() {
    }

    /**
     * 生成会话ID
     *
     * <p>使用 SecureRandom 生成24字节随机数，结合 UUID 保证全局唯一性。</p>
     *
     * @return 会话ID字符串
     */
    public static String getSessionId() {
        return generateSecureId(SESSION_ID_RANDOM_BYTES);
    }

    /**
     * 生成安全UUID
     *
     * <p>使用 SecureRandom 生成24字节随机数，结合 UUID 保证全局唯一性。</p>
     *
     * @return UUID字符串
     */
    public static String getUUID() {
        return generateSecureId(UUID_RANDOM_BYTES);
    }

    private static String generateSecureId(int randomBytes) {
        byte[] bytes = new byte[randomBytes];
        SECURE_RANDOM.nextBytes(bytes);
        return bytesToHex(bytes) + UUID.randomUUID().toString().replace("-", "");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
