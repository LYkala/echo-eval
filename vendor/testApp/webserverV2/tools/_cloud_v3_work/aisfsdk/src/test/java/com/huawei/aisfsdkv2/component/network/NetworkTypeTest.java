/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.network;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * NetworkType 测试
 */
public class NetworkTypeTest {

    @Test
    public void testEnumValues() {
        NetworkType[] values = NetworkType.values();
        assertEquals(3, values.length);
    }

    @Test
    public void testWifiValue() {
        NetworkType value = NetworkType.WIFI;
        assertNotNull(value);
        assertEquals("WIFI", value.name());
    }

    @Test
    public void testCellularValue() {
        NetworkType value = NetworkType.CELLULAR;
        assertNotNull(value);
        assertEquals("CELLULAR", value.name());
    }

    @Test
    public void testNoneValue() {
        NetworkType value = NetworkType.NONE;
        assertNotNull(value);
        assertEquals("NONE", value.name());
    }

    @Test
    public void testValueOf() {
        assertEquals(NetworkType.WIFI, NetworkType.valueOf("WIFI"));
        assertEquals(NetworkType.CELLULAR, NetworkType.valueOf("CELLULAR"));
        assertEquals(NetworkType.NONE, NetworkType.valueOf("NONE"));
    }

    @Test
    public void testOrdinalOrder() {
        assertEquals(0, NetworkType.WIFI.ordinal());
        assertEquals(1, NetworkType.CELLULAR.ordinal());
        assertEquals(2, NetworkType.NONE.ordinal());
    }
}
