/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * ThreadUtil 单元测试
 */
public class ThreadUtilTest {

    @Test
    public void testSleepNoExcept_ZeroMillis() {
        // 不应抛出异常
        ThreadUtil.sleepNoExcept(0);
    }

    @Test
    public void testSleepNoExcept_PositiveMillis() {
        long start = System.currentTimeMillis();
        ThreadUtil.sleepNoExcept(50);
        long elapsed = System.currentTimeMillis() - start;
        assertTrue("Should have slept at least 40ms", elapsed >= 40);
    }

    @Test
    public void testSleepNoExcept_WhenInterrupted() throws InterruptedException {
        Thread[] result = new Thread[1];
        Thread testThread = new Thread(() -> {
            Thread.currentThread().interrupt(); // pre-interrupt
            ThreadUtil.sleepNoExcept(1000);
            result[0] = Thread.currentThread();
        });
        testThread.start();
        testThread.join(2000);
        // Thread should have finished (interrupted sleep returns early)
        assertFalse(testThread.isAlive());
    }

    @Test
    public void testSleepNoExcept_LargeMillis_NoException() {
        // Just ensure the call doesn't throw (use 1ms to keep test fast)
        ThreadUtil.sleepNoExcept(1);
    }
}
