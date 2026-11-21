/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.location;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.base.BaseApi;
import com.huawei.aisfsdkv2.api.base.KeyFileInfo;
import com.huawei.aisfsdkv2.api.location.LocationOprResult;
import com.huawei.aisfsdkv2.message.MessageProcessor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * LocationApiImpl 单元测试
 *
 * <p>mockStatic MessageProcessor 打桩消息发送，Robolectric 保证 LocationMsgInfo 构造正常。</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LocationApiImplTest {

    @Mock
    private MessageProcessor mockMessageProcessor;

    @Mock
    private BaseApi mockBaseApi;

    private MockedStatic<MessageProcessor> mockedMessageProcessor;
    private MockedStatic<AISFSDKClient> mockedAisfSdkClient;
    private AutoCloseable mocks;
    private File tempDir;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        mockedMessageProcessor = mockStatic(MessageProcessor.class);
        mockedMessageProcessor.when(MessageProcessor::getInstance).thenReturn(mockMessageProcessor);
        mockedAisfSdkClient = mockStatic(AISFSDKClient.class);
        mockedAisfSdkClient.when(AISFSDKClient::getBaseApi).thenReturn(mockBaseApi);

        tempDir = new File(System.getProperty("java.io.tmpdir"), "location-test-" + System.nanoTime());
        tempDir.mkdirs();
        KeyFileInfo keyFileInfo = new KeyFileInfo();
        keyFileInfo.setSdkDataDir(tempDir.getAbsolutePath());
        when(mockBaseApi.getKeyFileInfo()).thenReturn(keyFileInfo);

        Field configFilePathField = LocationApiImpl.class.getDeclaredField("configFilePath");
        configFilePathField.setAccessible(true);
        configFilePathField.set(LocationApiImpl.getInstance(), null);

        Field cacheField = LocationApiImpl.class.getDeclaredField("reportLocationEnableCache");
        cacheField.setAccessible(true);
        cacheField.set(LocationApiImpl.getInstance(), null);
    }

    @After
    public void tearDown() throws Exception {
        mockedMessageProcessor.close();
        mockedAisfSdkClient.close();
        mocks.close();
        deleteDir(tempDir);
    }

    private void deleteDir(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDir(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }

    @Test
    public void testGetInstance_notNull() {
        assertNotNull(LocationApiImpl.getInstance());
    }

    @Test
    public void testGetInstance_singleton() {
        assertSame(LocationApiImpl.getInstance(), LocationApiImpl.getInstance());
    }

    @Test
    public void testDeleteUserLocationInfo_Success() {
        when(mockMessageProcessor.sendImmediately(any())).thenReturn(true);
        LocationOprResult result = LocationApiImpl.getInstance().deleteUserLocationInfo();
        assertNotNull(result);
        assertTrue(result.isResult());
        assertTrue(result.getMessage().contains("success"));
    }

    @Test
    public void testDeleteUserLocationInfo_SendFailed() {
        when(mockMessageProcessor.sendImmediately(any())).thenReturn(false);
        LocationOprResult result = LocationApiImpl.getInstance().deleteUserLocationInfo();
        assertNotNull(result);
        assertFalse(result.isResult());
        assertNotNull(result.getMessage());
    }

    @Test
    public void testDeleteUserLocationInfo_ExceptionThrown() {
        when(mockMessageProcessor.sendImmediately(any())).thenThrow(new RuntimeException("connection lost"));
        LocationOprResult result = LocationApiImpl.getInstance().deleteUserLocationInfo();
        assertNotNull(result);
        assertFalse(result.isResult());
        assertTrue(result.getMessage().contains("connection lost"));
    }

    @Test
    public void testGetReportLocationEnable_DefaultTrue() {
        assertTrue(LocationApiImpl.getInstance().getReportLocationEnable());
    }

    @Test
    public void testUpdateReportLocationEnable_True() {
        LocationApiImpl.getInstance().updateReportLocationEnable(true);
        assertTrue(LocationApiImpl.getInstance().getReportLocationEnable());
    }

    @Test
    public void testUpdateReportLocationEnable_False() {
        LocationApiImpl.getInstance().updateReportLocationEnable(false);
        assertFalse(LocationApiImpl.getInstance().getReportLocationEnable());
    }
}
