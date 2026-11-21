/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import android.util.Base64;

import java.nio.charset.StandardCharsets;

/**
 * Base64编解码工具类
 *
 * @author l00504561
 * @since 2026-04-10
 */
public class Base64Util {

    private Base64Util() {
    }

    /**
     * Base64编码
     *
     * @param input 原始字符串
     * @return 编码后的字符串，输入为null时返回null
     */
    public static String encode(String input) {
        if (input == null) {
            return null;
        }
        return Base64.encodeToString(input.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    /**
     * Base64解码
     *
     * @param input 编码后的字符串
     * @return 解码后的字符串，输入为null时返回null
     */
    public static String decode(String input) {
        if (input == null) {
            return null;
        }
        return new String(Base64.decode(input, Base64.NO_WRAP), StandardCharsets.UTF_8);
    }
}
