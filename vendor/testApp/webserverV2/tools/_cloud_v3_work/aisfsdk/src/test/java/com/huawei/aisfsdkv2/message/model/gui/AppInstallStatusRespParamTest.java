/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * AppInstallStatusRespParam 测试
 */
public class AppInstallStatusRespParamTest {

    private AppInstallStatusRespParam param;

    @Before
    public void setUp() {
        param = new AppInstallStatusRespParam();
    }

    @Test
    public void testConstants() {
        assertEquals(1, AppInstallStatusRespParam.INSTALLED_VALUE);
        assertEquals(2, AppInstallStatusRespParam.NOT_INSTALLED_VALUE);
    }

    @Test
    public void testDefaultIsInstall() {
        assertEquals(AppInstallStatusRespParam.NOT_INSTALLED_VALUE, param.getIsInstall());
    }

    @Test
    public void testDefaultInstallStatus() {
        assertEquals("uninstall", param.getInstallStatus());
    }

    @Test
    public void testSetInstalledStatus() {
        param.setIsInstall(AppInstallStatusRespParam.INSTALLED_VALUE);
        assertEquals(AppInstallStatusRespParam.INSTALLED_VALUE, param.getIsInstall());
        assertEquals("installed", param.getInstallStatus());
    }

    @Test
    public void testSetNotInstalledStatus() {
        param.setIsInstall(AppInstallStatusRespParam.NOT_INSTALLED_VALUE);
        assertEquals("uninstall", param.getInstallStatus());
    }

    @Test
    public void testUnknownStatusValue() {
        param.setIsInstall(99);
        assertEquals("uninstall", param.getInstallStatus());
    }

    @Test
    public void testZeroStatusValue() {
        param.setIsInstall(0);
        assertEquals("uninstall", param.getInstallStatus());
    }

    @Test
    public void testFieldOverwrite() {
        param.setIsInstall(AppInstallStatusRespParam.INSTALLED_VALUE);
        assertEquals("installed", param.getInstallStatus());
        param.setIsInstall(AppInstallStatusRespParam.NOT_INSTALLED_VALUE);
        assertEquals("uninstall", param.getInstallStatus());
    }
}
