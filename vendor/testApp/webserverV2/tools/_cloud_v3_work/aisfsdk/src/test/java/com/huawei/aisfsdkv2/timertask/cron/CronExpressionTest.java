/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.timertask.cron;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * CronExpression 测试
 */
public class CronExpressionTest {

    // ======= parse() success cases =======

    @Test
    public void testParse_AllAsterisk() {
        CronExpression expr = CronExpression.parse("* * * * *");
        assertNotNull(expr);
        assertEquals("* * * * *", expr.getExpression());
    }

    @Test
    public void testParse_SpecificTime() {
        CronExpression expr = CronExpression.parse("30 8 * * *");
        assertNotNull(expr);
    }

    @Test
    public void testParse_Range() {
        CronExpression expr = CronExpression.parse("0-30 8 * * *");
        assertNotNull(expr);
    }

    @Test
    public void testParse_Enumerate() {
        CronExpression expr = CronExpression.parse("0,15,30,45 8 * * *");
        assertNotNull(expr);
    }

    @Test
    public void testParse_Step() {
        CronExpression expr = CronExpression.parse("*/5 * * * *");
        assertNotNull(expr);
    }

    @Test
    public void testParse_ComplexExpression() {
        CronExpression expr = CronExpression.parse("*/5 8,12 1,15 * 1-5");
        assertNotNull(expr);
    }

    @Test
    public void testParse_WeekdayMorning() {
        CronExpression expr = CronExpression.parse("0 9 * * 1-5");
        assertNotNull(expr);
    }

    // ======= parse() error cases =======

    @Test(expected = IllegalArgumentException.class)
    public void testParse_NullExpression() {
        CronExpression.parse(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParse_EmptyExpression() {
        CronExpression.parse("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParse_BlankExpression() {
        CronExpression.parse("   ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParse_TooFewFields() {
        CronExpression.parse("0 8 * *");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParse_TooManyFields() {
        CronExpression.parse("0 8 * * * 2024");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParse_MinuteOutOfRange() {
        CronExpression.parse("60 8 * * *");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParse_HourOutOfRange() {
        CronExpression.parse("0 24 * * *");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParse_DayOfMonthOutOfRange() {
        CronExpression.parse("0 8 32 * *");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParse_MonthOutOfRange() {
        CronExpression.parse("0 8 1 13 *");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParse_DayOfWeekOutOfRange() {
        CronExpression.parse("0 8 * * 7");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParse_InvalidInteger() {
        CronExpression.parse("abc 8 * * *");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParse_InvalidRange() {
        CronExpression.parse("30-10 8 * * *");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParse_InvalidStep() {
        CronExpression.parse("*/0 8 * * *");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParse_NegativeStep() {
        CronExpression.parse("*/-1 8 * * *");
    }

    // ======= matches() =======

    @Test
    public void testMatches_AllAsterisk() {
        CronExpression expr = CronExpression.parse("* * * * *");
        Calendar cal = Calendar.getInstance();
        assertTrue(expr.matches(cal));
    }

    @Test
    public void testMatches_SpecificMinute() {
        CronExpression expr = CronExpression.parse("30 * * * *");
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MINUTE, 30);
        assertTrue(expr.matches(cal));
        cal.set(Calendar.MINUTE, 45);
        assertFalse(expr.matches(cal));
    }

    @Test
    public void testMatches_SpecificHour() {
        CronExpression expr = CronExpression.parse("* 8 * * *");
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 8);
        assertTrue(expr.matches(cal));
        cal.set(Calendar.HOUR_OF_DAY, 9);
        assertFalse(expr.matches(cal));
    }

    @Test
    public void testMatches_Range() {
        CronExpression expr = CronExpression.parse("0-30 * * * *");
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MINUTE, 15);
        assertTrue(expr.matches(cal));
        cal.set(Calendar.MINUTE, 45);
        assertFalse(expr.matches(cal));
    }

    @Test
    public void testMatches_Enumerate() {
        CronExpression expr = CronExpression.parse("0,15,30,45 * * * *");
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MINUTE, 0);
        assertTrue(expr.matches(cal));
        cal.set(Calendar.MINUTE, 15);
        assertTrue(expr.matches(cal));
        cal.set(Calendar.MINUTE, 10);
        assertFalse(expr.matches(cal));
    }

    @Test
    public void testMatches_Step() {
        CronExpression expr = CronExpression.parse("*/10 * * * *");
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MINUTE, 0);
        assertTrue(expr.matches(cal));
        cal.set(Calendar.MINUTE, 10);
        assertTrue(expr.matches(cal));
        cal.set(Calendar.MINUTE, 20);
        assertTrue(expr.matches(cal));
        cal.set(Calendar.MINUTE, 5);
        assertFalse(expr.matches(cal));
    }

    @Test
    public void testMatches_NullCalendar() {
        CronExpression expr = CronExpression.parse("* * * * *");
        assertFalse(expr.matches(null));
    }

    // ======= toString() =======

    @Test
    public void testToString() {
        CronExpression expr = CronExpression.parse("30 8 * * 1-5");
        String str = expr.toString();
        assertTrue(str.contains("30 8 * * 1-5"));
    }

    // ======= getExpression() =======

    @Test
    public void testGetExpression() {
        String original = "30 8 * * 1-5";
        CronExpression expr = CronExpression.parse(original);
        assertEquals("30 8 * * 1-5", expr.getExpression());
    }
}
