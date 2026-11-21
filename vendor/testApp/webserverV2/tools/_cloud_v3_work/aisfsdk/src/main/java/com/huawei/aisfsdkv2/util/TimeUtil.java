/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * 时间工具类
 *
 * <p>提供时间戳生成和解析功能</p>
 */
public final class TimeUtil {
    private static final DateTimeFormatter UTC_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

    private TimeUtil() {
        // 工具类禁止实例化
    }

    /**
     * 获取WebSocket消息Header中的EventTimeStamp字段值
     *
     * <p><b>专用格式：</b>此方法专门用于WebSocket协议消息的EventTimeStamp字段，
     * 格式为ISO 8601 UTC格式（yyyy-MM-dd'T'HH:mm:ss'Z'）。</p>
     *
     * <p><b>使用场景：</b></p>
     * <ul>
     *   <li>设置消息Header的EventTimeStamp字段</li>
     *   <li>不建议用于其他用途</li>
     * </ul>
     *
     * @return ISO 8601 UTC格式时间字符串，示例：2024-03-31T12:30:45Z
     */
    public static String getEventTimestamp() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(UTC_FORMATTER);
    }

    /**
     * 解析触发时间字符串为毫秒时间戳
     *
     * <p>支持两种格式：
     * <ol>
     *   <li>ISO 8601 格式（推荐）："2026-04-14T19:50:15+08:00"</li>
     *   <li>毫秒时间戳："1713097815000"</li>
     * </ol>
     * </p>
     *
     * @param triggerTime 触发时间字符串
     * @return 毫秒时间戳，解析失败返回 -1
     */
    public static long parseTriggerTime(String triggerTime) {
        if (triggerTime == null || triggerTime.isEmpty()) {
            return -1;
        }
        triggerTime = triggerTime.trim();
        // 尝试解析为纯数字（毫秒时间戳）
        try {
            return Long.parseLong(triggerTime);
        } catch (NumberFormatException ignored) {
            // 不是纯数字，继续尝试 ISO 8601 格式
        }
        // 尝试解析 ISO 8601 格式
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(triggerTime, DateTimeFormatter.ISO_DATE_TIME);
            return zdt.toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return -1;
        }
    }
}
