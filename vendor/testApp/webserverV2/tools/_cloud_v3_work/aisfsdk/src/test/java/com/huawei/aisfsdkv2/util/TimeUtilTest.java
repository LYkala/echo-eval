/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.format.DateTimeParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * TimeUtil 单元测试
 *
 * @author l00504561
 * @since 2026-03-31
 */
public class TimeUtilTest {

    /**
     * 测试 getEventTimestamp() 返回非空值
     */
    @Test
    public void testGetEventTimestamp_NotNull() {
        String timestamp = TimeUtil.getEventTimestamp();
        assertNotNull("时间戳不应为null", timestamp);
    }

    /**
     * 测试 getEventTimestamp() 返回格式正确的ISO 8601时间戳
     */
    @Test
    public void testGetEventTimestamp_Format() {
        String timestamp = TimeUtil.getEventTimestamp();
        // 验证格式：yyyy-MM-dd'T'HH:mm:ss'Z'
        assertTrue("时间戳应匹配ISO 8601格式", 
            timestamp.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"));
    }

    /**
     * 测试 getEventTimestamp() 返回有效的可解析时间
     */
    @Test
    public void testGetEventTimestamp_Parseable() {
        String timestamp = TimeUtil.getEventTimestamp();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        try {
            LocalDateTime.parse(timestamp, formatter);
            // 解析成功，测试通过
        } catch (DateTimeParseException e) {
            throw new AssertionError("时间戳应能被正确解析: " + timestamp, e);
        }
    }

    /**
     * 测试多次调用返回不同的时间戳（时间流逝）
     */
    @Test
    public void testGetEventTimestamp_DifferentCalls() throws InterruptedException {
        String timestamp1 = TimeUtil.getEventTimestamp();
        Thread.sleep(1000); // 等待1秒
        String timestamp2 = TimeUtil.getEventTimestamp();
        
        assertNotNull("第一个时间戳不应为null", timestamp1);
        assertNotNull("第二个时间戳不应为null", timestamp2);
        // 由于等待了1秒，时间戳应该不同
        assertTrue("两次调用应返回不同的时间戳", !timestamp1.equals(timestamp2));
    }

    /**
     * 测试 getEventTimestamp() 返回UTC时间
     */
    @Test
    public void testGetEventTimestamp_UtcFormat() {
        String timestamp = TimeUtil.getEventTimestamp();
        // 验证以Z结尾（UTC标志）
        assertTrue("时间戳应以Z结尾表示UTC", timestamp.endsWith("Z"));
    }
}
