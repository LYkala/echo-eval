/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.base;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * DeviceTypeEnum 测试
 */
public class DeviceTypeEnumTest {

    @Test
    public void testEnumCount() {
        assertEquals(5, DeviceTypeEnum.values().length);
    }

    @Test
    public void testGetName() {
        assertEquals("CLOUD_PHONE", DeviceTypeEnum.CLOUD_PHONE.getName());
        assertEquals("PHY_PHONE", DeviceTypeEnum.PHY_PHONE.getName());
        assertEquals("COMPUTING_HOST", DeviceTypeEnum.COMPUTING_HOST.getName());
        assertEquals("CLOUD_PAD", DeviceTypeEnum.CLOUD_PAD.getName());
        assertEquals("DX_PHY_PHONE", DeviceTypeEnum.DX_PHY_PHONE.getName());
    }

    @Test
    public void testGetValue() {
        assertEquals("1", DeviceTypeEnum.CLOUD_PHONE.getValue());
        assertEquals("2", DeviceTypeEnum.PHY_PHONE.getValue());
        assertEquals("3", DeviceTypeEnum.COMPUTING_HOST.getValue());
        assertEquals("4", DeviceTypeEnum.CLOUD_PAD.getValue());
        assertEquals("5", DeviceTypeEnum.DX_PHY_PHONE.getValue());
    }

    @Test
    public void testValuesAreUnique() {
        DeviceTypeEnum[] values = DeviceTypeEnum.values();
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                assertFalse("Duplicate value: " + values[i].getValue(),
                        values[i].getValue().equals(values[j].getValue()));
                assertFalse("Duplicate name: " + values[i].getName(),
                        values[i].getName().equals(values[j].getName()));
            }
        }
    }

    @Test
    public void testValueOf() {
        assertEquals(DeviceTypeEnum.CLOUD_PHONE, DeviceTypeEnum.valueOf("CLOUD_PHONE"));
        assertEquals(DeviceTypeEnum.PHY_PHONE, DeviceTypeEnum.valueOf("PHY_PHONE"));
    }
}
