/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logger.local;

import com.huawei.aisfsdkv2.api.log.LogCollectCallback;
import com.huawei.aisfsdkv2.apiimpl.log.LogApiImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.RandomAccessFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * LocalFileLogger.collectAppLog() 单元测试
 */
@RunWith(MockitoJUnitRunner.class)
public class LocalFileLoggerCollectTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private LocalFileLogger logger;
    private MockedStatic<LogApiImpl> mockedLogApiImpl;
    private LogApiImpl mockLogApi;

    @Before
    public void setUp() {
        logger = LocalFileLogger.getInstance();
        mockLogApi = mock(LogApiImpl.class);
        mockedLogApiImpl = mockStatic(LogApiImpl.class);
        mockedLogApiImpl.when(LogApiImpl::getInstance).thenReturn(mockLogApi);
    }

    @After
    public void tearDown() {
        mockedLogApiImpl.close();
    }

    private File invokeCollectAppLog() throws Exception {
        java.lang.reflect.Method method =
                LocalFileLogger.class.getDeclaredMethod("collectAppLog");
        method.setAccessible(true);
        return (File) method.invoke(logger);
    }

    // ======= 回调未注册 =======

    @Test
    public void testCollectAppLog_NoCallback_ReturnsNull() throws Exception {
        when(mockLogApi.getLogCollectCallback()).thenReturn(null);
        File result = invokeCollectAppLog();
        assertNull(result);
    }

    // ======= 回调返回 null =======

    @Test
    public void testCollectAppLog_CallbackReturnsNull_ReturnsNull() throws Exception {
        LogCollectCallback callback = mock(LogCollectCallback.class);
        when(callback.onCollectLog()).thenReturn(null);
        when(mockLogApi.getLogCollectCallback()).thenReturn(callback);
        File result = invokeCollectAppLog();
        assertNull(result);
    }

    // ======= 回调返回不存在的路径 =======

    @Test
    public void testCollectAppLog_CallbackReturnsInvalidPath_ReturnsNull() throws Exception {
        LogCollectCallback callback = mock(LogCollectCallback.class);
        when(callback.onCollectLog()).thenReturn("/nonexistent/path/app.zip");
        when(mockLogApi.getLogCollectCallback()).thenReturn(callback);
        File result = invokeCollectAppLog();
        assertNull(result);
    }

    // ======= 回调返回有效路径 =======

    @Test
    public void testCollectAppLog_CallbackReturnsValidPath_ReturnsFile() throws Exception {
        File appZip = tempFolder.newFile("app_logs.zip");
        LogCollectCallback callback = mock(LogCollectCallback.class);
        when(callback.onCollectLog()).thenReturn(appZip.getAbsolutePath());
        when(mockLogApi.getLogCollectCallback()).thenReturn(callback);
        File result = invokeCollectAppLog();
        assertNotNull(result);
        assertEquals(appZip.getAbsolutePath(), result.getAbsolutePath());
    }

    // ======= 回调返回超过 10MB 的文件 =======

    @Test
    public void testCollectAppLog_CallbackReturnsOversizedFile_ReturnsNull() throws Exception {
        File largeFile = tempFolder.newFile("large_app.zip");
        try (RandomAccessFile raf = new RandomAccessFile(largeFile, "rw")) {
            raf.setLength(11 * 1024 * 1024);
        }
        LogCollectCallback callback = mock(LogCollectCallback.class);
        when(callback.onCollectLog()).thenReturn(largeFile.getAbsolutePath());
        when(mockLogApi.getLogCollectCallback()).thenReturn(callback);
        File result = invokeCollectAppLog();
        assertNull(result);
    }

    // ======= 回调返回恰好 10MB 的文件（边界值） =======

    @Test
    public void testCollectAppLog_CallbackReturnsExactly10MB_ReturnsFile() throws Exception {
        File exactFile = tempFolder.newFile("exact_app.zip");
        try (RandomAccessFile raf = new RandomAccessFile(exactFile, "rw")) {
            raf.setLength(10 * 1024 * 1024);
        }
        LogCollectCallback callback = mock(LogCollectCallback.class);
        when(callback.onCollectLog()).thenReturn(exactFile.getAbsolutePath());
        when(mockLogApi.getLogCollectCallback()).thenReturn(callback);
        File result = invokeCollectAppLog();
        assertNotNull(result);
    }

    // ======= 回调抛出异常 =======

    @Test
    public void testCollectAppLog_CallbackThrowsException_ReturnsNull() throws Exception {
        LogCollectCallback callback = mock(LogCollectCallback.class);
        when(callback.onCollectLog()).thenThrow(new RuntimeException("App error"));
        when(mockLogApi.getLogCollectCallback()).thenReturn(callback);
        File result = invokeCollectAppLog();
        assertNull(result);
    }

    // ======= 回调返回空字符串 =======

    @Test
    public void testCollectAppLog_CallbackReturnsEmptyString_ReturnsNull() throws Exception {
        LogCollectCallback callback = mock(LogCollectCallback.class);
        when(callback.onCollectLog()).thenReturn("");
        when(mockLogApi.getLogCollectCallback()).thenReturn(callback);
        File result = invokeCollectAppLog();
        assertNull(result);
    }
}
