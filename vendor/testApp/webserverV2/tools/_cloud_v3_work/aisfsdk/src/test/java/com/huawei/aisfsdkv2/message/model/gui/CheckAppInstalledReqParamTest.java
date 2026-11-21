/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * CheckAppInstalledReqParam 单元测试
 *
 * <p>测试检查应用安装请求参数</p>
 *
 * @author l00504561
 * @since 2026-03-31
 */
public class CheckAppInstalledReqParamTest {

    // ==================== 基本构造测试 ====================

    /**
     * 测试默认构造
     */
    @Test
    public void testDefaultConstructor() {
        CheckAppInstalledReqParam param = new CheckAppInstalledReqParam();
        assertNotNull("构造后对象不应为null", param);
    }

    // ==================== PackageName 测试 ====================

    /**
     * 测试 PackageName setter/getter
     */
    @Test
    public void testPackageName() {
        CheckAppInstalledReqParam param = new CheckAppInstalledReqParam();
        param.setPackageName("com.test.app");
        assertEquals("PackageName 应正确设置", "com.test.app", param.getPackageName());
    }

    /**
     * 测试 PackageName 设置为 null
     */
    @Test
    public void testPackageName_Null() {
        CheckAppInstalledReqParam param = new CheckAppInstalledReqParam();
        param.setPackageName(null);
        assertNull("PackageName 可为 null", param.getPackageName());
    }

    /**
     * 测试 PackageName 设置为空字符串
     */
    @Test
    public void testPackageName_Empty() {
        CheckAppInstalledReqParam param = new CheckAppInstalledReqParam();
        param.setPackageName("");
        assertEquals("PackageName 可为空字符串", "", param.getPackageName());
    }

    /**
     * 测试 PackageName 设置为长字符串
     */
    @Test
    public void testPackageName_Long() {
        CheckAppInstalledReqParam param = new CheckAppInstalledReqParam();
        String longPackageName = "com.example.very.long.package.name.for.testing";
        param.setPackageName(longPackageName);
        assertEquals(longPackageName, param.getPackageName());
    }

    // ==================== 边界值测试 ====================

    /**
     * 测试 PackageName 包含特殊字符
     */
    @Test
    public void testPackageName_SpecialChars() {
        CheckAppInstalledReqParam param = new CheckAppInstalledReqParam();
        param.setPackageName("com.test.app_123");
        assertEquals("com.test.app_123", param.getPackageName());
    }

    /**
     * 测试 PackageName 多次设置
     */
    @Test
    public void testPackageName_Overwrite() {
        CheckAppInstalledReqParam param = new CheckAppInstalledReqParam();
        
        param.setPackageName("com.first.app");
        assertEquals("com.first.app", param.getPackageName());
        
        param.setPackageName("com.second.app");
        assertEquals("com.second.app", param.getPackageName());
        
        param.setPackageName(null);
        assertNull(param.getPackageName());
    }

    // ==================== 常见包名测试 ====================

    /**
     * 测试常见应用包名
     */
    @Test
    public void testCommonPackageNames() {
        CheckAppInstalledReqParam param = new CheckAppInstalledReqParam();
        
        // 测试微信
        param.setPackageName("com.tencent.mm");
        assertEquals("com.tencent.mm", param.getPackageName());
        
        // 测试淘宝
        param.setPackageName("com.taobao.taobao");
        assertEquals("com.taobao.taobao", param.getPackageName());
        
        // 测试系统应用
        param.setPackageName("com.android.settings");
        assertEquals("com.android.settings", param.getPackageName());
    }

    // ==================== 综合测试 ====================

    /**
     * 测试默认值为空字符串
     */
    @Test
    public void testDefaultValue() {
        CheckAppInstalledReqParam param = new CheckAppInstalledReqParam();
        assertEquals("默认 PackageName 应为空字符串", "", param.getPackageName());
    }

    /**
     * 测试 toString 方法
     */
    @Test
    public void testToString() {
        CheckAppInstalledReqParam param = new CheckAppInstalledReqParam();
        param.setPackageName("com.test.app");
        
        String str = param.toString();
        assertNotNull("toString 不应返回 null", str);
        assertTrue("toString 应返回非空字符串", str.length() > 0);
        // toString 继承自父类，格式可能不同，仅验证不为 null
    }

    /**
     * 测试空对象 toString
     */
    @Test
    public void testToString_Empty() {
        CheckAppInstalledReqParam param = new CheckAppInstalledReqParam();
        
        String str = param.toString();
        assertNotNull("空对象 toString 不应返回 null", str);
    }

    /**
     * 测试多个实例独立
     */
    @Test
    public void testMultipleInstances() {
        CheckAppInstalledReqParam param1 = new CheckAppInstalledReqParam();
        param1.setPackageName("com.app1");
        
        CheckAppInstalledReqParam param2 = new CheckAppInstalledReqParam();
        param2.setPackageName("com.app2");
        
        assertEquals("com.app1", param1.getPackageName());
        assertEquals("com.app2", param2.getPackageName());
    }
}
