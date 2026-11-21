/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.impl;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * ACMFacadeImpl 相关模型测试
 */
public class ACMFacadeImplTest {

    // ======= AuthResult model =======

    @Test
    public void testAuthResult_TokenAndExpire() {
        com.huawei.aisfsdkv2.component.facade.model.acm.AuthResult ar =
                new com.huawei.aisfsdkv2.component.facade.model.acm.AuthResult("tok-123", 3600);
        assertEquals("tok-123", ar.getToken());
        assertEquals(3600, ar.getExpireTime());
    }

    @Test
    public void testAuthResult_NullToken() {
        com.huawei.aisfsdkv2.component.facade.model.acm.AuthResult ar =
                new com.huawei.aisfsdkv2.component.facade.model.acm.AuthResult(null, 0);
        assertNull(ar.getToken());
        assertEquals(0, ar.getExpireTime());
    }

    @Test
    public void testAuthResult_ZeroExpireTime() {
        com.huawei.aisfsdkv2.component.facade.model.acm.AuthResult ar =
                new com.huawei.aisfsdkv2.component.facade.model.acm.AuthResult("token", 0);
        assertEquals(0, ar.getExpireTime());
    }

    @Test
    public void testAuthResult_LargeExpireTime() {
        com.huawei.aisfsdkv2.component.facade.model.acm.AuthResult ar =
                new com.huawei.aisfsdkv2.component.facade.model.acm.AuthResult("token", Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, ar.getExpireTime());
    }

    // ======= UploadResult model =======

    @Test
    public void testUploadResult_FileUuid() {
        com.huawei.aisfsdkv2.component.facade.model.acm.UploadResult ur =
                new com.huawei.aisfsdkv2.component.facade.model.acm.UploadResult("file-uuid-abc");
        assertEquals("file-uuid-abc", ur.getFileUuid());
    }

    @Test
    public void testUploadResult_NullUuid() {
        com.huawei.aisfsdkv2.component.facade.model.acm.UploadResult ur =
                new com.huawei.aisfsdkv2.component.facade.model.acm.UploadResult(null);
        assertNull(ur.getFileUuid());
    }

    @Test
    public void testUploadResult_EmptyUuid() {
        com.huawei.aisfsdkv2.component.facade.model.acm.UploadResult ur =
                new com.huawei.aisfsdkv2.component.facade.model.acm.UploadResult("");
        assertEquals("", ur.getFileUuid());
    }

    // ======= AcmException model =======

    @Test
    public void testAcmException_Fields() {
        com.huawei.aisfsdkv2.component.facade.model.acm.AcmException ex =
                new com.huawei.aisfsdkv2.component.facade.model.acm.AcmException(503, "Service unavailable");
        assertEquals(503, ex.getCode());
        assertEquals("Service unavailable", ex.getMessage());
    }

    @Test
    public void testAcmException_NegativeCode() {
        com.huawei.aisfsdkv2.component.facade.model.acm.AcmException ex =
                new com.huawei.aisfsdkv2.component.facade.model.acm.AcmException(-1, "IO error");
        assertEquals(-1, ex.getCode());
        assertEquals("IO error", ex.getMessage());
    }

    @Test
    public void testAcmException_ZeroCode() {
        com.huawei.aisfsdkv2.component.facade.model.acm.AcmException ex =
                new com.huawei.aisfsdkv2.component.facade.model.acm.AcmException(0, "Unknown");
        assertEquals(0, ex.getCode());
    }

    @Test
    public void testAcmException_NullMessage() {
        com.huawei.aisfsdkv2.component.facade.model.acm.AcmException ex =
                new com.huawei.aisfsdkv2.component.facade.model.acm.AcmException(400, null);
        assertEquals(400, ex.getCode());
        assertNull(ex.getMessage());
    }
}
