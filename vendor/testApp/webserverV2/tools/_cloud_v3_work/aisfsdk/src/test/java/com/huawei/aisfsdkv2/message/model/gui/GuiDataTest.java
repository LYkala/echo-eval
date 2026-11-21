/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

/**
 * GuiData 单元测试
 *
 * <p>测试 GUI 数据模型的字段和扩展参数</p>
 *
 * @author l00504561
 * @since 2026-03-31
 */
public class GuiDataTest {

    // ==================== 基本构造测试 ====================

    /**
     * 测试默认构造
     */
    @Test
    public void testDefaultConstructor() {
        GuiData guiData = new GuiData();
        assertNotNull("构造后对象不应为null", guiData);
    }

    // ==================== MessageType 测试 ====================

    /**
     * 测试 MessageType setter/getter
     */
    @Test
    public void testMessageType() {
        GuiData guiData = new GuiData();
        guiData.setMessageType("DataExchange");
        assertEquals("MessageType 应正确设置", "DataExchange", guiData.getMessageType());
    }

    /**
     * 测试 MessageType 设置为 null
     */
    @Test
    public void testMessageType_Null() {
        GuiData guiData = new GuiData();
        guiData.setMessageType(null);
        assertNull("MessageType 可为 null", guiData.getMessageType());
    }

    // ==================== UserOutputText 测试 ====================

    /**
     * 测试 UserOutputText setter/getter
     */
    @Test
    public void testUserOutputText() {
        GuiData guiData = new GuiData();
        guiData.setUserOutputText("Hello World");
        assertEquals("UserOutputText 应正确设置", "Hello World", guiData.getUserOutputText());
    }

    // ==================== ExtParam 测试 ====================

    /**
     * 测试 ExtParam 默认值不为 null
     */
    @Test
    public void testExtParam_DefaultNotNull() {
        GuiData guiData = new GuiData();
        Map<String, Object> extParam = guiData.getExtParam();
        assertNotNull("默认 ExtParam 不应为 null", extParam);
    }

    /**
     * 测试向 ExtParam 添加参数
     */
    @Test
    public void testExtParam_AddParam() {
        GuiData guiData = new GuiData();
        guiData.getExtParam().put("key1", "value1");
        guiData.getExtParam().put("key2", 123);
        
        assertEquals("value1", guiData.getExtParam().get("key1"));
        assertEquals(123, guiData.getExtParam().get("key2"));
    }

    /**
     * 测试 ExtParam 可存储不同类型
     */
    @Test
    public void testExtParam_DifferentTypes() {
        GuiData guiData = new GuiData();
        
        guiData.getExtParam().put("string", "test");
        guiData.getExtParam().put("integer", 42);
        guiData.getExtParam().put("boolean", true);
        guiData.getExtParam().put("double", 3.14);
        
        assertEquals("test", guiData.getExtParam().get("string"));
        assertEquals(42, guiData.getExtParam().get("integer"));
        assertEquals(true, guiData.getExtParam().get("boolean"));
        assertEquals(3.14, guiData.getExtParam().get("double"));
    }

    // ==================== 综合测试 ====================

    /**
     * 测试所有字段独立设置
     */
    @Test
    public void testAllFieldsIndependent() {
        GuiData guiData = new GuiData();
        
        guiData.setMessageType("UserInput");
        guiData.setUserOutputText("test output");
        guiData.getExtParam().put("UserInputObject", "test object");
        guiData.getExtParam().put("extra", "data");
        
        assertEquals("UserInput", guiData.getMessageType());
        assertEquals("test output", guiData.getUserOutputText());
        assertEquals("test object", guiData.getExtParam().get("UserInputObject"));
        assertEquals("data", guiData.getExtParam().get("extra"));
    }

    /**
     * 测试多次修改字段
     */
    @Test
    public void testFieldOverwrite() {
        GuiData guiData = new GuiData();
        
        guiData.setMessageType("Type1");
        assertEquals("Type1", guiData.getMessageType());
        
        guiData.setMessageType("Type2");
        assertEquals("Type2", guiData.getMessageType());
        
        guiData.setMessageType(null);
        assertNull(guiData.getMessageType());
    }

    /**
     * 测试 toString 方法
     */
    @Test
    public void testToString() {
        GuiData guiData = new GuiData();
        guiData.setMessageType("TestType");
        
        String str = guiData.toString();
        assertNotNull("toString 不应返回 null", str);
        assertTrue("toString 应返回非空字符串", str.length() > 0);
    }

    /**
     * 测试空对象状态
     */
    @Test
    public void testEmptyObject() {
        GuiData guiData = new GuiData();
        
        assertEquals("默认 MessageType 应为 DataExchange", "DataExchange", guiData.getMessageType());
        assertEquals("默认 UserOutputText 应为空字符串", "", guiData.getUserOutputText());
        assertNotNull("默认 ExtParam 不应为 null", guiData.getExtParam());
        assertTrue("默认 ExtParam 应为空", guiData.getExtParam().isEmpty());
    }

    /**
     * 测试 ExtParam 是同一个实例（引用不变）
     */
    @Test
    public void testExtParam_SameInstance() {
        GuiData guiData = new GuiData();
        Map<String, Object> extParam1 = guiData.getExtParam();
        Map<String, Object> extParam2 = guiData.getExtParam();
        
        assertTrue("多次 getExtParam 应返回同一实例", extParam1 == extParam2);
    }
}
