/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.fileserver;

import com.huawei.aisfsdkv2.api.fileserver.FileDownloadCallback;
import com.huawei.aisfsdkv2.api.fileserver.FileUploadCallback;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * FileServerApiImpl 单元测试
 *
 * <p>不使用 Robolectric，纯 Mockito 测试以获得正确的 JaCoCo 覆盖率。</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class FileServerApiImplTest {

    @Mock
    private ThreadPoolManager mockThreadPoolManager;

    @Mock
    private FileDownloadCallback mockDownloadCallback;

    @Mock
    private FileUploadCallback mockUploadCallback;

    private MockedStatic<ThreadPoolManager> mockedStatic;

    @Before
    public void setUp() {
        mockedStatic = mockStatic(ThreadPoolManager.class);
        mockedStatic.when(ThreadPoolManager::getInstance).thenReturn(mockThreadPoolManager);
    }

    @After
    public void tearDown() {
        mockedStatic.close();
    }

    @Test
    public void testGetInstance() {
        FileServerApiImpl instance = FileServerApiImpl.getInstance();
        assertNotNull(instance);
    }

    @Test
    public void testGetInstance_SameInstance() {
        FileServerApiImpl instance1 = FileServerApiImpl.getInstance();
        FileServerApiImpl instance2 = FileServerApiImpl.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    public void testDownloadFileAsync_NullFileUrl() {
        FileServerApiImpl instance = FileServerApiImpl.getInstance();
        instance.downloadFileAsync(null, "/path", mockDownloadCallback);
        verify(mockDownloadCallback).onFailure(null, "/path", -1, "fileUrl is null or empty");
    }

    @Test
    public void testDownloadFileAsync_EmptyFileUrl() {
        FileServerApiImpl instance = FileServerApiImpl.getInstance();
        instance.downloadFileAsync("", "/path", mockDownloadCallback);
        verify(mockDownloadCallback).onFailure("", "/path", -1, "fileUrl is null or empty");
    }

    @Test
    public void testDownloadFileAsync_WhitespaceFileUrl() {
        FileServerApiImpl instance = FileServerApiImpl.getInstance();
        instance.downloadFileAsync("   ", "/path", mockDownloadCallback);
        verify(mockDownloadCallback).onFailure("   ", "/path", -1, "fileUrl is null or empty");
    }

    @Test
    public void testDownloadFileAsync_NullSavePath() {
        FileServerApiImpl instance = FileServerApiImpl.getInstance();
        instance.downloadFileAsync("http://example.com/file", null, mockDownloadCallback);
        verify(mockDownloadCallback).onFailure("http://example.com/file", null, -1, "savePath is null or empty");
    }

    @Test
    public void testDownloadFileAsync_EmptySavePath() {
        FileServerApiImpl instance = FileServerApiImpl.getInstance();
        instance.downloadFileAsync("http://example.com/file", "", mockDownloadCallback);
        verify(mockDownloadCallback).onFailure("http://example.com/file", "", -1, "savePath is null or empty");
    }

    @Test
    public void testDownloadFileAsync_NullCallback() {
        FileServerApiImpl instance = FileServerApiImpl.getInstance();
        instance.downloadFileAsync("http://example.com/file", "/path", null);
        verify(mockThreadPoolManager, never()).executeSharedThreadPool(any());
    }

    @Test
    public void testDownloadFileAsync_ValidParameters() {
        FileServerApiImpl instance = FileServerApiImpl.getInstance();
        instance.downloadFileAsync("http://example.com/file", "/path/file.txt", mockDownloadCallback);
        verify(mockThreadPoolManager).executeSharedThreadPool(any(DownloadTask.class));
    }

    @Test
    public void testDownloadTask_Creation() {
        DownloadTask task = new DownloadTask("http://example.com/file", "/path/file.txt", mockDownloadCallback);
        assertNotNull(task);
    }

    @Test
    public void testUploadFileAsync_NullFileName() {
        FileServerApiImpl instance = FileServerApiImpl.getInstance();
        byte[] data = new byte[]{1, 2, 3};
        instance.uploadFileAsync(null, data, mockUploadCallback);
        verify(mockUploadCallback).onFailure(null, -1, "fileName is null or empty");
    }

    @Test
    public void testUploadFileAsync_EmptyFileName() {
        FileServerApiImpl instance = FileServerApiImpl.getInstance();
        byte[] data = new byte[]{1, 2, 3};
        instance.uploadFileAsync("", data, mockUploadCallback);
        verify(mockUploadCallback).onFailure("", -1, "fileName is null or empty");
    }

    @Test
    public void testUploadFileAsync_WhitespaceFileName() {
        FileServerApiImpl instance = FileServerApiImpl.getInstance();
        byte[] data = new byte[]{1, 2, 3};
        instance.uploadFileAsync("   ", data, mockUploadCallback);
        verify(mockUploadCallback).onFailure("   ", -1, "fileName is null or empty");
    }

    @Test
    public void testUploadFileAsync_NullFileData() {
        FileServerApiImpl instance = FileServerApiImpl.getInstance();
        instance.uploadFileAsync("test.jpg", null, mockUploadCallback);
        verify(mockUploadCallback).onFailure("test.jpg", -1, "fileData is null or empty");
    }

    @Test
    public void testUploadFileAsync_EmptyFileData() {
        FileServerApiImpl instance = FileServerApiImpl.getInstance();
        instance.uploadFileAsync("test.jpg", new byte[0], mockUploadCallback);
        verify(mockUploadCallback).onFailure("test.jpg", -1, "fileData is null or empty");
    }

    @Test
    public void testUploadFileAsync_NullCallback() {
        FileServerApiImpl instance = FileServerApiImpl.getInstance();
        byte[] data = new byte[]{1, 2, 3};
        instance.uploadFileAsync("test.jpg", data, null);
        verify(mockThreadPoolManager, never()).executeSharedThreadPool(any());
    }

    @Test
    public void testUploadFileAsync_ValidParameters() {
        FileServerApiImpl instance = FileServerApiImpl.getInstance();
        byte[] data = new byte[]{1, 2, 3};
        instance.uploadFileAsync("test.jpg", data, mockUploadCallback);
        verify(mockThreadPoolManager).executeSharedThreadPool(any(UploadTask.class));
    }

    @Test
    public void testUploadTask_Creation() {
        byte[] data = new byte[]{1, 2, 3};
        UploadTask task = new UploadTask("test.jpg", data, mockUploadCallback);
        assertNotNull(task);
    }
}
