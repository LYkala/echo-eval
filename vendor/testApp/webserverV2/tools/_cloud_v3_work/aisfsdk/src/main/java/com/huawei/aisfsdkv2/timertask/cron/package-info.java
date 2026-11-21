/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * Cron 表达式解析子模块
 *
 * <p>本包提供标准 5 段式 cron 表达式的解析与匹配能力，供
 * {@link com.huawei.aisfsdkv2.timertask.CrontabTaskManager} 使用。</p>
 *
 * <h3>支持的 Cron 格式</h3>
 * <pre>
 *   分(0-59)  时(0-23)  日(1-31)  月(1-12)  周(0-6, 0=周日)
 * </pre>
 *
 * <h3>支持的特殊字符</h3>
 * <ul>
 *   <li>{@code *}：匹配任意值，如 {@code * * * * *} 每分钟触发</li>
 *   <li>{@code * /n}（无空格）：步长，如每5分钟触发写作 {@code * /5 * * * *}</li>
 *   <li>{@code a-b}：范围，如 {@code 1-5} 匹配1到5</li>
 *   <li>{@code a,b,c}：枚举，如 {@code 1,3,5} 匹配1、3、5</li>
 *   <li>具体数字：如 {@code 30} 匹配第30分钟</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 每天8:30触发（周一至周五）
 *   CronExpression expr = CronExpression.parse("30 8 * * 1-5");
 *   boolean matches = expr.matches(Calendar.getInstance());
 * </pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>cron 表达式使用 5 段格式，不支持秒和年</li>
 *   <li>周字段中 0 表示周日，1-6 表示周一到周六</li>
 *   <li>复杂表达式建议先测试验证</li>
 *   <li>避免使用过于频繁的触发间隔（如每分钟）</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.timertask.cron;
