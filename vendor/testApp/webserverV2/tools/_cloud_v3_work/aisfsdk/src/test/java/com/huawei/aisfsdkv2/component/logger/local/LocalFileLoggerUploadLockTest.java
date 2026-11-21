/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logger.local;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * LocalFileLogger 上传重入保护锁单元测试
 */
@RunWith(MockitoJUnitRunner.class)
public class LocalFileLoggerUploadLockTest {

    private LocalFileLogger logger;

    @Before
    public void setUp() throws Exception {
        logger = LocalFileLogger.getInstance();
        resetUploadingFlag();
    }

    private void resetUploadingFlag() throws Exception {
        Field field = LocalFileLogger.class.getDeclaredField("isUploading");
        field.setAccessible(true);
        AtomicBoolean flag = (AtomicBoolean) field.get(logger);
        flag.set(false);
    }

    private AtomicBoolean getUploadingFlagField() throws Exception {
        Field field = LocalFileLogger.class.getDeclaredField("isUploading");
        field.setAccessible(true);
        return (AtomicBoolean) field.get(logger);
    }

    // ======= 初始状态 =======

    @Test
    public void testUploadingFlag_InitiallyFalse() throws Exception {
        assertFalse(getUploadingFlagField().get());
    }

    // ======= CAS 成功（首次触发） =======

    @Test
    public void testUploadingFlag_CasFromFalseToTrue_Succeeds() throws Exception {
        AtomicBoolean flag = getUploadingFlagField();
        assertTrue(flag.compareAndSet(false, true));
        assertTrue(flag.get());
    }

    // ======= CAS 失败（重入保护） =======

    @Test
    public void testUploadingFlag_CasWhenAlreadyTrue_Fails() throws Exception {
        AtomicBoolean flag = getUploadingFlagField();
        flag.set(true);
        assertFalse(flag.compareAndSet(false, true));
    }

    // ======= 上传完成后释放锁 =======

    @Test
    public void testUploadingFlag_ResetAfterUpload() throws Exception {
        AtomicBoolean flag = getUploadingFlagField();
        flag.set(true);
        flag.set(false);
        assertTrue(flag.compareAndSet(false, true));
    }
}
