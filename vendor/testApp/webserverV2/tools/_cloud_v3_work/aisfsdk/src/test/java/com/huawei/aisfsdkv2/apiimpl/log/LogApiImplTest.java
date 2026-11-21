/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.log;

import com.huawei.aisfsdkv2.api.log.LogCollectCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

/**
 * LogApiImpl 单元测试 - LogCollectCallback 相关
 */
@RunWith(MockitoJUnitRunner.class)
public class LogApiImplTest {

    private LogApiImpl logApi;

    @Before
    public void setUp() {
        logApi = LogApiImpl.getInstance();
        logApi.setLogCollectCallback(null);
    }

    @After
    public void tearDown() {
        logApi.setLogCollectCallback(null);
    }

    // ======= 单例 =======

    @Test
    public void testSingleton_ReturnsSameInstance() {
        LogApiImpl instance1 = LogApiImpl.getInstance();
        LogApiImpl instance2 = LogApiImpl.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    public void testSetLogCollectCallback_WithCallback_GetReturnsSame() {
        LogCollectCallback mockCallback = mock(LogCollectCallback.class);
        logApi.setLogCollectCallback(mockCallback);
        assertSame(mockCallback, logApi.getLogCollectCallback());
    }

    @Test
    public void testSetLogCollectCallback_WithNull_GetReturnsNull() {
        LogCollectCallback mockCallback = mock(LogCollectCallback.class);
        logApi.setLogCollectCallback(mockCallback);
        logApi.setLogCollectCallback(null);
        assertNull(logApi.getLogCollectCallback());
    }

    @Test
    public void testSetLogCollectCallback_ReplaceCallback_GetReturnsNew() {
        LogCollectCallback callback1 = mock(LogCollectCallback.class);
        LogCollectCallback callback2 = mock(LogCollectCallback.class);
        logApi.setLogCollectCallback(callback1);
        logApi.setLogCollectCallback(callback2);
        assertSame(callback2, logApi.getLogCollectCallback());
    }

    @Test
    public void testGetLogCollectCallback_InitiallyNull() {
        assertNull(logApi.getLogCollectCallback());
    }
}
