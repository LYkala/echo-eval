/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logger.local;

import com.huawei.aisfsdkv2.component.facade.ACMFacade;
import com.huawei.aisfsdkv2.component.facade.model.acm.AcmException;
import com.huawei.aisfsdkv2.component.facade.model.acm.UploadResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UploadLogManager 单元测试
 */
@RunWith(MockitoJUnitRunner.class)
public class UploadLogManagerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private UploadLogManager uploadManager;
    private MockedStatic<ACMFacade> mockedAcmFacade;
    private ACMFacade mockAcmFacade;

    @Before
    public void setUp() {
        uploadManager = UploadLogManager.getInstance();
        mockAcmFacade = mock(ACMFacade.class);
        mockedAcmFacade = mockStatic(ACMFacade.class);
        mockedAcmFacade.when(ACMFacade::getInstance).thenReturn(mockAcmFacade);
    }

    @After
    public void tearDown() {
        mockedAcmFacade.close();
    }

    // ======= 上传成功 =======

    @Test
    public void testUploadLogFile_Success_ReturnsTrue() throws Exception {
        File zipFile = tempFolder.newFile("alllog_test.zip");
        when(mockAcmFacade.uploadLogFile(zipFile))
                .thenReturn(new UploadResult("uuid-123"));

        com.huawei.aisfsdkv2.component.logger.local.UploadResult result =
                uploadManager.uploadLogFile(zipFile);

        assertTrue(result.success);
        verify(mockAcmFacade).uploadLogFile(zipFile);
        assertTrue(zipFile.exists());
    }

    // ======= 上传失败（AcmException） =======

    @Test
    public void testUploadLogFile_AcmException_ReturnsFalse() throws Exception {
        File zipFile = tempFolder.newFile("alllog_test.zip");
        when(mockAcmFacade.uploadLogFile(zipFile))
                .thenThrow(new AcmException(-1, "upload failed"));

        com.huawei.aisfsdkv2.component.logger.local.UploadResult result =
                uploadManager.uploadLogFile(zipFile);

        assertFalse(result.success);
        assertTrue(zipFile.exists());
    }

    // ======= 文件不存在 =======

    @Test
    public void testUploadLogFile_FileNotExist_ReturnsFalse() {
        File nonExistent = new File(tempFolder.getRoot(), "nonexistent.zip");

        com.huawei.aisfsdkv2.component.logger.local.UploadResult result =
                uploadManager.uploadLogFile(nonExistent);

        assertFalse(result.success);
    }

    // ======= 文件为 null =======

    @Test
    public void testUploadLogFile_NullFile_ReturnsFalse() {
        com.huawei.aisfsdkv2.component.logger.local.UploadResult result =
                uploadManager.uploadLogFile(null);

        assertFalse(result.success);
    }

    // ======= 上传成功后不删除本地文件 =======

    @Test
    public void testUploadLogFile_SuccessDoesNotDeleteFile() throws Exception {
        File zipFile = tempFolder.newFile("alllog_test.zip");
        when(mockAcmFacade.uploadLogFile(zipFile))
                .thenReturn(new UploadResult("uuid-456"));

        uploadManager.uploadLogFile(zipFile);

        assertTrue("File should not be deleted after upload", zipFile.exists());
    }
}
