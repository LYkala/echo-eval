/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * StringUtil 单元测试
 *
 * @author l00504561
 * @since 2026-03-31
 */
public class StringUtilTest {

    // ==================== isNullOrEmpty 测试 ====================

    /**
     * 测试 isNullOrEmpty：null 应返回 true
     */
    @Test
    public void testIsNullOrEmpty_Null() {
        assertTrue("null 应返回 true", StringUtil.isNullOrEmpty(null));
    }

    /**
     * 测试 isNullOrEmpty：空字符串应返回 true
     */
    @Test
    public void testIsNullOrEmpty_EmptyString() {
        assertTrue("空字符串应返回 true", StringUtil.isNullOrEmpty(""));
    }

    /**
     * 测试 isNullOrEmpty：空白字符应返回 false
     */
    @Test
    public void testIsNullOrEmpty_Whitespace() {
        assertFalse("空白字符应返回 false", StringUtil.isNullOrEmpty(" "));
        assertFalse("制表符应返回 false", StringUtil.isNullOrEmpty("\t"));
        assertFalse("换行符应返回 false", StringUtil.isNullOrEmpty("\n"));
    }

    /**
     * 测试 isNullOrEmpty：非空字符串应返回 false
     */
    @Test
    public void testIsNullOrEmpty_NonEmptyString() {
        assertFalse("非空字符串应返回 false", StringUtil.isNullOrEmpty("test"));
        assertFalse("单个字符应返回 false", StringUtil.isNullOrEmpty("a"));
        assertFalse("数字字符串应返回 false", StringUtil.isNullOrEmpty("123"));
    }

    // ==================== nullToEmpty 测试 ====================

    /**
     * 测试 nullToEmpty：null 应返回空字符串
     */
    @Test
    public void testNullToEmpty_Null() {
        assertEquals("null 应转为空字符串", "", StringUtil.nullToEmpty(null));
    }

    /**
     * 测试 nullToEmpty：空字符串保持不变
     */
    @Test
    public void testNullToEmpty_EmptyString() {
        assertEquals("空字符串保持不变", "", StringUtil.nullToEmpty(""));
    }

    /**
     * 测试 nullToEmpty：非空字符串保持不变
     */
    @Test
    public void testNullToEmpty_NonEmptyString() {
        assertEquals("普通字符串应保持不变", "test", StringUtil.nullToEmpty("test"));
        assertEquals("空白字符串应保持不变", "  ", StringUtil.nullToEmpty("  "));
        assertEquals("特殊字符应保持不变", "!@#$%", StringUtil.nullToEmpty("!@#$%"));
    }

    // ==================== joiner 测试 ====================

    /**
     * 测试 joiner：null 数组应返回空字符串
     */
    @Test
    public void testJoiner_NullArray() {
        assertEquals("null 数组应返回空字符串", "", StringUtil.joiner((String[]) null, ","));
    }

    /**
     * 测试 joiner：空数组应返回空字符串
     */
    @Test
    public void testJoiner_EmptyArray() {
        assertEquals("空数组应返回空字符串", "", StringUtil.joiner(new String[]{}, ","));
        assertEquals("空Integer数组应返回空字符串", "", StringUtil.joiner(new Integer[]{}, ","));
    }

    /**
     * 测试 joiner：单个元素不应添加分隔符
     */
    @Test
    public void testJoiner_SingleElement() {
        assertEquals("单个字符串元素", "test", StringUtil.joiner(new String[]{"test"}, ","));
        assertEquals("单个数字元素", "123", StringUtil.joiner(new Integer[]{123}, ","));
    }

    /**
     * 测试 joiner：多个元素正确拼接
     */
    @Test
    public void testJoiner_MultipleElements() {
        assertEquals("多个字符串用逗号拼接", 
            "a,b,c", StringUtil.joiner(new String[]{"a", "b", "c"}, ","));
        assertEquals("多个数字用分号拼接", 
            "1;2;3", StringUtil.joiner(new Integer[]{1, 2, 3}, ";"));
    }

    /**
     * 测试 joiner：不同分隔符
     */
    @Test
    public void testJoiner_DifferentSeparators() {
        String[] array = new String[]{"a", "b", "c"};
        assertEquals("逗号分隔", "a,b,c", StringUtil.joiner(array, ","));
        assertEquals("空格分隔", "a b c", StringUtil.joiner(array, " "));
        assertEquals("横线分隔", "a-b-c", StringUtil.joiner(array, "-"));
        assertEquals("空字符串分隔", "abc", StringUtil.joiner(array, ""));
    }

    /**
     * 测试 joiner：对象数组调用 toString()
     */
    @Test
    public void testJoiner_ObjectArray() {
        Object[] array = new Object[]{"str", 123, true};
        assertEquals("对象数组应调用toString", 
            "str,123,true", StringUtil.joiner(array, ","));
    }

    // ==================== 常量测试 ====================

    /**
     * 测试常量定义
     */
    @Test
    public void testConstants() {
        assertEquals("空字符串常量", "", StringUtil.EMPTY);
        assertEquals("逗号常量", ",", StringUtil.COMMA);
        assertEquals("引号常量", "\"", StringUtil.QUOTATION_MARKS);
        assertEquals("反斜杠常量", "\\", StringUtil.BACKSLASH);
        assertEquals("冒号常量", ':', StringUtil.COLON);
        assertEquals("左括号常量", "[", StringUtil.LEFT_SQUARE_BRACKET);
        assertEquals("右括号常量", "]", StringUtil.RIGHT_SQUARE_BRACKET);
        assertEquals("空格常量", " ", StringUtil.SPACE);
        assertEquals("下划线常量", "_", StringUtil.UNDERSCORE);
    }
}
