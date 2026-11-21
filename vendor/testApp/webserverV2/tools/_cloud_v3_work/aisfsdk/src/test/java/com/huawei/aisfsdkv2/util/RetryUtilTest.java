/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * RetryUtil 单元测试
 */
public class RetryUtilTest {

    // ======= retry =======

    @Test
    public void testRetry_SuccessOnFirstTry() {
        String result = RetryUtil.retry(() -> "ok", r -> r != null, 3, 0);
        assertEquals("ok", result);
    }

    @Test
    public void testRetry_SuccessOnSecondTry() {
        AtomicInteger count = new AtomicInteger(0);
        String result = RetryUtil.retry(() -> {
            if (count.incrementAndGet() < 2) {
                return null;
            }
            return "ok";
        }, r -> r != null, 3, 0);
        assertEquals("ok", result);
        assertEquals(2, count.get());
    }

    @Test
    public void testRetry_AllFail_ReturnsNull() {
        String result = RetryUtil.retry(() -> null, r -> r != null, 3, 0);
        assertNull(result);
    }

    @Test
    public void testRetry_ExceptionRetries() {
        AtomicInteger count = new AtomicInteger(0);
        String result = RetryUtil.retry(() -> {
            if (count.incrementAndGet() < 3) {
                throw new RuntimeException("fail");
            }
            return "recovered";
        }, r -> r != null, 5, 0);
        assertEquals("recovered", result);
    }

    @Test
    public void testRetry_MaxCountExceeded_ReturnsNull() {
        AtomicInteger count = new AtomicInteger(0);
        String result = RetryUtil.retry(() -> {
            count.incrementAndGet();
            throw new RuntimeException("always fail");
        }, r -> r != null, 3, 0);
        assertNull(result);
        assertEquals(3, count.get());
    }

    @Test
    public void testRetry_ZeroRetries_ReturnsNull() {
        String result = RetryUtil.retry(() -> "ok", r -> r != null, 0, 0);
        assertNull(result);
    }

    @Test
    public void testRetry_CheckFunctionFalse_Retries() {
        AtomicInteger count = new AtomicInteger(0);
        Integer result = RetryUtil.retry(() -> count.incrementAndGet(), r -> r >= 3, 5, 0);
        assertEquals(Integer.valueOf(3), result);
    }

    // ======= retryWithReturnObject =======

    @Test
    public void testRetryWithReturnObject_SuccessOnFirstTry() {
        String result = RetryUtil.retryWithReturnObject(() -> "ok", r -> r != null, 3, 0);
        assertEquals("ok", result);
    }

    @Test
    public void testRetryWithReturnObject_AllFail_ReturnsLastResult() {
        AtomicInteger count = new AtomicInteger(0);
        Integer result = RetryUtil.retryWithReturnObject(
                count::incrementAndGet,
                r -> r > 100, // always false
                3, 0);
        assertEquals(Integer.valueOf(3), result); // last result returned
    }

    @Test
    public void testRetryWithReturnObject_AllNull_ReturnsNull() {
        String result = RetryUtil.retryWithReturnObject(() -> null, r -> r != null, 3, 0);
        assertNull(result);
    }

    @Test
    public void testRetryWithReturnObject_SuccessOnThirdTry() {
        AtomicInteger count = new AtomicInteger(0);
        String result = RetryUtil.retryWithReturnObject(() -> {
            if (count.incrementAndGet() < 3) {
                return null;
            }
            return "success";
        }, r -> r != null, 5, 0);
        assertEquals("success", result);
    }

    // ======= 常量验证 =======

    @Test
    public void testConstants() {
        assertEquals(3, RetryUtil.RETRY_COUNT_3);
        assertEquals(5, RetryUtil.RETRY_COUNT_5);
        assertEquals(10, RetryUtil.RETRY_COUNT_10);
        assertEquals(Integer.MAX_VALUE, RetryUtil.RETRY_COUNT_NA);
        assertEquals(50, RetryUtil.RETRY_BACKUP_50_MS);
        assertEquals(500, RetryUtil.RETRY_BACKUP_500_MS);
        assertEquals(1000, RetryUtil.RETRY_BACKUP_1000_MS);
        assertEquals(5000, RetryUtil.RETRY_BACKUP_5000_MS);
    }
}
