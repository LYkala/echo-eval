/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import java.util.List;

/**
 * 字符串工具类
 *
 * @author l00504561
 * @since 2026-02-07
 */
public class StringUtil {
    /**
     * 空字符串
     */
    public static final String EMPTY = "";

    /**
     * 逗号
     */
    public static final String COMMA = ",";

    /**
     * 引号
     */
    public static final String QUOTATION_MARKS = "\"";

    /**
     * 反斜杠
     */
    public static final String BACKSLASH = "\\";

    /**
     * 冒号
     */
    public static final char COLON = ':';

    /**
     * 左括号
     */
    public static final String LEFT_SQUARE_BRACKET = "[";

    /**
     * 右括号
     */
    public static final String RIGHT_SQUARE_BRACKET = "]";

    /**
     * 空格
     */
    public static final String SPACE = " ";

    /**
     * 下划线
     */
    public static final String UNDERSCORE = "_";

    /**
     * 检查字符串是否为null或者空字串
     *
     * @param str 被检查的字符串
     * @return true-字符串为null或者空，false-字符串不为null和空
     */
    public static boolean isNullOrEmpty(String str) {
        return null == str || "".equals(str);
    }

    /**
     * 确保处理的字符串不为空
     *
     * @param string    字符串
     * @return          null -> ""
     */
    public static String nullToEmpty(String string) {
        if (string == null) {
            return StringUtil.EMPTY;
        }
        return string;
    }

    /**
     * 字符串拼接
     *
     * @param array 字符串数组
     * @param joinerOn 连接字符串
     * @return 结果
     */
    public static <T> String joiner(T[] array, String joinerOn) {
        if (array == null) {
            return StringUtil.EMPTY;
        }
        if (array.length == 0) {
            return StringUtil.EMPTY;
        }
        boolean first = true;
        StringBuilder stringBuilder = new StringBuilder();
        for (T item : array) {
            if (first) {
                stringBuilder.append(item.toString());
                first = false;
                continue;
            }
            stringBuilder.append(joinerOn).append(item.toString());
        }
        return stringBuilder.toString();
    }

    /**
     * 字符串拼接
     *
     * @param list 字符串列表
     * @param joinerOn 连接字符串
     * @return 结果
     */
    public static <T> String joiner(List<T> list, String joinerOn) {
        if (list == null || list.isEmpty()) {
            return StringUtil.EMPTY;
        }
        boolean first = true;
        StringBuilder stringBuilder = new StringBuilder();
        for (T item : list) {
            if (first) {
                stringBuilder.append(item.toString());
                first = false;
                continue;
            }
            stringBuilder.append(joinerOn).append(item.toString());
        }
        return stringBuilder.toString();
    }
}
