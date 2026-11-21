/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.acm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * ACM 模型类测试：AuthResult、UploadResult、AcmException
 */
public class AcmModelsTest {

    // ======= AuthResult =======

    @Test
    public void testAuthResult_Constructor() {
        AuthResult result = new AuthResult("token-abc", 3600);
        assertEquals("token-abc", result.getToken());
        assertEquals(3600, result.getExpireTime());
    }

    @Test
    public void testAuthResult_NullToken() {
        AuthResult result = new AuthResult(null, 0);
        assertNull(result.getToken());
        assertEquals(0, result.getExpireTime());
    }

    @Test
    public void testAuthResult_ZeroExpire() {
        AuthResult result = new AuthResult("tok", 0);
        assertEquals(0, result.getExpireTime());
    }

    // ======= UploadResult =======

    @Test
    public void testUploadResult_Constructor() {
        UploadResult result = new UploadResult("uuid-12345");
        assertEquals("uuid-12345", result.getFileUuid());
    }

    @Test
    public void testUploadResult_NullUuid() {
        UploadResult result = new UploadResult(null);
        assertNull(result.getFileUuid());
    }

    // ======= AcmException =======

    @Test
    public void testAcmException_CodeAndMessage() {
        AcmException ex = new AcmException(404, "Not found");
        assertEquals(404, ex.getCode());
        assertEquals("Not found", ex.getMessage());
    }

    @Test
    public void testAcmException_NegativeCode() {
        AcmException ex = new AcmException(-1, "IO error");
        assertEquals(-1, ex.getCode());
        assertEquals("IO error", ex.getMessage());
    }

    @Test
    public void testAcmException_IsException() {
        AcmException ex = new AcmException(500, "Server error");
        assertNotNull(ex);
        assertEquals(500, ex.getCode());
    }
}
