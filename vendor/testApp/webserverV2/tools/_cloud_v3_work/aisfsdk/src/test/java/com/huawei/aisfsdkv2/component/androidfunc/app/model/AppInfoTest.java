/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.app.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * AppInfo 测试
 */
public class AppInfoTest {

    @Test
    public void testConstructor() {
        AppInfo appInfo = new AppInfo("com.test.app", "TestApp", "1.0.0", false);
        assertEquals("com.test.app", appInfo.getPackageName());
        assertEquals("TestApp", appInfo.getAppName());
        assertEquals("1.0.0", appInfo.getVersionName());
        assertFalse(appInfo.isSystemApp());
    }

    @Test
    public void testSystemApp() {
        AppInfo appInfo = new AppInfo("com.android.settings", "Settings", "14.0", true);
        assertTrue(appInfo.isSystemApp());
    }

    @Test
    public void testNonSystemApp() {
        AppInfo appInfo = new AppInfo("com.user.app", "UserApp", "2.0", false);
        assertFalse(appInfo.isSystemApp());
    }

    @Test
    public void testNullValues() {
        AppInfo appInfo = new AppInfo(null, null, null, false);
        assertNull(appInfo.getPackageName());
        assertNull(appInfo.getAppName());
        assertNull(appInfo.getVersionName());
    }

    @Test
    public void testEmptyValues() {
        AppInfo appInfo = new AppInfo("", "", "", false);
        assertEquals("", appInfo.getPackageName());
        assertEquals("", appInfo.getAppName());
        assertEquals("", appInfo.getVersionName());
    }

    @Test
    public void testToString() {
        AppInfo appInfo = new AppInfo("com.test", "Test", "1.0", true);
        String str = appInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("com.test"));
        assertTrue(str.contains("Test"));
        assertTrue(str.contains("1.0"));
        assertTrue(str.contains("true"));
        assertTrue(str.contains("AppInfo"));
    }

    @Test
    public void testToStringWithNulls() {
        AppInfo appInfo = new AppInfo(null, null, null, false);
        String str = appInfo.toString();
        assertNotNull(str);
        assertTrue(str.contains("AppInfo"));
    }

    @Test
    public void testGetPackageName() {
        AppInfo appInfo = new AppInfo("com.example.myapp", "MyApp", "3.2.1", false);
        assertEquals("com.example.myapp", appInfo.getPackageName());
    }

    @Test
    public void testGetVersionName() {
        AppInfo appInfo = new AppInfo("pkg", "app", "10.20.30", false);
        assertEquals("10.20.30", appInfo.getVersionName());
    }
}
