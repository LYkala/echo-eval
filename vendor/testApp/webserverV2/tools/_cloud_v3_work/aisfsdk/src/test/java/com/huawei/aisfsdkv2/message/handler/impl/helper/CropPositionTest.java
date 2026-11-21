/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler.impl.helper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * CropPosition 测试
 */
public class CropPositionTest {

    @Test
    public void testDefaultConstructor() {
        CropPosition pos = new CropPosition();
        assertEquals(0, pos.getX());
        assertEquals(0, pos.getY());
        assertEquals(100, pos.getLeftCropPerc());
        assertEquals(100, pos.getRightCropPerc());
        assertEquals(100, pos.getUpCropPerc());
        assertEquals(100, pos.getDownCropPerc());
    }

    @Test
    public void testSymmetricConstructor() {
        CropPosition pos = new CropPosition(500, 800, 5, 100);
        assertEquals(500, pos.getX());
        assertEquals(800, pos.getY());
        assertEquals(100, pos.getLeftCropPerc());
        assertEquals(100, pos.getRightCropPerc());
        assertEquals(5, pos.getUpCropPerc());
        assertEquals(5, pos.getDownCropPerc());
    }

    @Test
    public void testSetX() {
        CropPosition pos = new CropPosition();
        pos.setX(300);
        assertEquals(300, pos.getX());
    }

    @Test
    public void testSetY() {
        CropPosition pos = new CropPosition();
        pos.setY(600);
        assertEquals(600, pos.getY());
    }

    @Test
    public void testSetLeftCropPerc() {
        CropPosition pos = new CropPosition();
        pos.setLeftCropPerc(50);
        assertEquals(50, pos.getLeftCropPerc());
    }

    @Test
    public void testSetRightCropPerc() {
        CropPosition pos = new CropPosition();
        pos.setRightCropPerc(75);
        assertEquals(75, pos.getRightCropPerc());
    }

    @Test
    public void testSetUpCropPerc() {
        CropPosition pos = new CropPosition();
        pos.setUpCropPerc(20);
        assertEquals(20, pos.getUpCropPerc());
    }

    @Test
    public void testSetDownCropPerc() {
        CropPosition pos = new CropPosition();
        pos.setDownCropPerc(30);
        assertEquals(30, pos.getDownCropPerc());
    }

    @Test
    public void testToString_ContainsXY() {
        CropPosition pos = new CropPosition(100, 200, 10, 20);
        String str = pos.toString();
        assertTrue(str.contains("100"));
        assertTrue(str.contains("200"));
        assertTrue(str.contains("10"));
        assertTrue(str.contains("20"));
    }

    @Test
    public void testToString_DefaultContains100() {
        CropPosition pos = new CropPosition();
        String str = pos.toString();
        assertTrue(str.contains("100"));
    }

    @Test
    public void testAllFieldsSetIndependently() {
        CropPosition pos = new CropPosition();
        pos.setX(10);
        pos.setY(20);
        pos.setLeftCropPerc(30);
        pos.setRightCropPerc(40);
        pos.setUpCropPerc(50);
        pos.setDownCropPerc(60);
        assertEquals(10, pos.getX());
        assertEquals(20, pos.getY());
        assertEquals(30, pos.getLeftCropPerc());
        assertEquals(40, pos.getRightCropPerc());
        assertEquals(50, pos.getUpCropPerc());
        assertEquals(60, pos.getDownCropPerc());
    }

    @Test
    public void testSymmetricConstructor_ZeroValues() {
        CropPosition pos = new CropPosition(0, 0, 0, 0);
        assertEquals(0, pos.getLeftCropPerc());
        assertEquals(0, pos.getRightCropPerc());
        assertEquals(0, pos.getUpCropPerc());
        assertEquals(0, pos.getDownCropPerc());
    }
}
