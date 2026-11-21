/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.timertask.cron;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/**
 * 标准 5 段式 Cron 表达式解析器
 *
 * <p>支持的格式：{@code 分 时 日 月 周}，各字段范围如下：</p>
 * <ul>
 *   <li>分钟（minute）：0-59</li>
 *   <li>小时（hour）：0-23</li>
 *   <li>日（dayOfMonth）：1-31</li>
 *   <li>月（month）：1-12</li>
 *   <li>星期（dayOfWeek）：0-6（0=周日，6=周六）</li>
 * </ul>
 *
 * <p>支持的特殊字符：</p>
 * <ul>
 *   <li>{@code *}：匹配任意值</li>
 *   <li>具体数字：如 {@code 5}</li>
 *   <li>{@code *\/n}：步长，如 {@code *\/5} 表示每5个单位</li>
 *   <li>{@code ,}：枚举，如 {@code 1,3,5}</li>
 *   <li>{@code -}：范围，如 {@code 1-5}</li>
 * </ul>
 *
 * <p>示例：</p>
 * <pre>
 *   CronExpression expr = CronExpression.parse("30 8 * * 1-5");
 *   boolean matches = expr.matches(Calendar.getInstance());
 * </pre>
 *
 * @since 2026-04-08
 */
public class CronExpression {

    private static final int FIELD_COUNT = 5;
    private static final int IDX_MINUTE = 0;
    private static final int IDX_HOUR = 1;
    private static final int IDX_DAY_OF_MONTH = 2;
    private static final int IDX_MONTH = 3;
    private static final int IDX_DAY_OF_WEEK = 4;

    private static final int MIN_MINUTE = 0;
    private static final int MAX_MINUTE = 59;
    private static final int MIN_HOUR = 0;
    private static final int MAX_HOUR = 23;
    private static final int MIN_DAY_OF_MONTH = 1;
    private static final int MAX_DAY_OF_MONTH = 31;
    private static final int MIN_MONTH = 1;
    private static final int MAX_MONTH = 12;
    private static final int MIN_DAY_OF_WEEK = 0;
    private static final int MAX_DAY_OF_WEEK = 6;

    private final Set<Integer> minutes;
    private final Set<Integer> hours;
    private final Set<Integer> daysOfMonth;
    private final Set<Integer> months;
    private final Set<Integer> daysOfWeek;
    private final String expression;

    private CronExpression(String expression, Set<Integer> minutes, Set<Integer> hours,
            Set<Integer> daysOfMonth, Set<Integer> months, Set<Integer> daysOfWeek) {
        this.expression = expression;
        this.minutes = minutes;
        this.hours = hours;
        this.daysOfMonth = daysOfMonth;
        this.months = months;
        this.daysOfWeek = daysOfWeek;
    }

    /**
     * 解析 cron 表达式字符串
     *
     * <p>解析流程：</p>
     * <ol>
     *   <li>按空白字符分割为 5 个字段</li>
     *   <li>依次解析每个字段为整数集合</li>
     *   <li>验证各字段值是否在允许范围内</li>
     * </ol>
     *
     * @param expression 5 段式 cron 表达式字符串，不可为 null 或空
     * @return 解析后的 {@link CronExpression} 实例
     * @throws IllegalArgumentException 若表达式格式不正确或字段值超出范围
     */
    public static CronExpression parse(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Cron expression must not be null or empty");
        }
        String[] fields = expression.trim().split("\\s+");
        if (fields.length != FIELD_COUNT) {
            throw new IllegalArgumentException(
                    "Cron expression must have exactly 5 fields (minute hour dayOfMonth month dayOfWeek), got: "
                            + fields.length);
        }
        Set<Integer> minutes = parseField(fields[IDX_MINUTE], MIN_MINUTE, MAX_MINUTE, "minute");
        Set<Integer> hours = parseField(fields[IDX_HOUR], MIN_HOUR, MAX_HOUR, "hour");
        Set<Integer> daysOfMonth = parseField(fields[IDX_DAY_OF_MONTH], MIN_DAY_OF_MONTH, MAX_DAY_OF_MONTH,
                "dayOfMonth");
        Set<Integer> months = parseField(fields[IDX_MONTH], MIN_MONTH, MAX_MONTH, "month");
        Set<Integer> daysOfWeek = parseField(fields[IDX_DAY_OF_WEEK], MIN_DAY_OF_WEEK, MAX_DAY_OF_WEEK,
                "dayOfWeek");
        return new CronExpression(expression, minutes, hours, daysOfMonth, months, daysOfWeek);
    }

    /**
     * 判断给定的时刻是否与 cron 表达式匹配
     *
     * <p>匹配规则：分钟、小时、日、月、星期同时满足时返回 true。</p>
     *
     * @param cal 待匹配的时刻，不可为 null
     * @return true 表示该时刻与 cron 表达式匹配
     */
    public boolean matches(Calendar cal) {
        if (cal == null) {
            return false;
        }
        int minute = cal.get(Calendar.MINUTE);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
        int month = cal.get(Calendar.MONTH) + 1;
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1;
        return minutes.contains(minute)
                && hours.contains(hour)
                && daysOfMonth.contains(dayOfMonth)
                && months.contains(month)
                && daysOfWeek.contains(dayOfWeek);
    }

    /**
     * 获取原始 cron 表达式字符串
     *
     * @return cron 表达式字符串
     */
    public String getExpression() {
        return expression;
    }

    /**
     * 解析单个 cron 字段为整数集合
     *
     * <p>支持以下格式：</p>
     * <ol>
     *   <li>{@code *}：范围内所有值</li>
     *   <li>{@code *\/n}：从 min 开始按步长 n 枚举</li>
     *   <li>{@code a-b}：从 a 到 b 的范围</li>
     *   <li>{@code a,b,c}：枚举多个值（每个值可为数字或范围）</li>
     *   <li>{@code n}：单个数字</li>
     * </ol>
     *
     * @param field     字段字符串
     * @param min       字段最小值（含）
     * @param max       字段最大值（含）
     * @param fieldName 字段名称，用于错误信息
     * @return 字段对应的整数集合
     * @throws IllegalArgumentException 若字段格式不合法或值超出范围
     */
    private static Set<Integer> parseField(String field, int min, int max, String fieldName) {
        Set<Integer> values = new HashSet<>();
        if ("*".equals(field)) {
            for (int i = min; i <= max; i++) {
                values.add(i);
            }
            return values;
        }
        if (field.startsWith("*/")) {
            int step = parseIntSafe(field.substring(2), fieldName);
            if (step <= 0) {
                throw new IllegalArgumentException(
                        "Step value must be positive in field '" + fieldName + "': " + field);
            }
            for (int i = min; i <= max; i += step) {
                values.add(i);
            }
            return values;
        }
        String[] parts = field.split(",");
        for (String part : parts) {
            if (part.contains("-")) {
                String[] range = part.split("-", 2);
                int from = parseIntSafe(range[0].trim(), fieldName);
                int to = parseIntSafe(range[1].trim(), fieldName);
                if (from > to) {
                    throw new IllegalArgumentException(
                            "Range start must not exceed range end in field '" + fieldName + "': " + part);
                }
                for (int i = from; i <= to; i++) {
                    validateRange(i, min, max, fieldName);
                    values.add(i);
                }
            } else {
                int val = parseIntSafe(part.trim(), fieldName);
                validateRange(val, min, max, fieldName);
                values.add(val);
            }
        }
        return values;
    }

    /**
     * 安全解析整数，失败时抛出 IllegalArgumentException
     *
     * @param str       待解析字符串
     * @param fieldName 字段名称，用于错误信息
     * @return 解析后的整数值
     * @throws IllegalArgumentException 若字符串不是有效整数
     */
    private static int parseIntSafe(String str, String fieldName) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid integer value in field '" + fieldName + "': " + str, e);
        }
    }

    /**
     * 验证整数值是否在指定范围内
     *
     * @param value     待验证的值
     * @param min       最小值（含）
     * @param max       最大值（含）
     * @param fieldName 字段名称，用于错误信息
     * @throws IllegalArgumentException 若值超出范围
     */
    private static void validateRange(int value, int min, int max, String fieldName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    "Value " + value + " out of range [" + min + ", " + max + "] in field '" + fieldName + "'");
        }
    }

    @Override
    public String toString() {
        return "CronExpression{expression='" + expression + "'}";
    }
}
