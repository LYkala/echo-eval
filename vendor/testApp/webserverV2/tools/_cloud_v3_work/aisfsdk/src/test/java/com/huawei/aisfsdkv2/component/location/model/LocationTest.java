/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.location.model;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Location 测试
 */
public class LocationTest {

    private Location location;

    @Before
    public void setUp() {
        location = new Location();
    }

    @Test
    public void testDefaultValues() {
        assertEquals(0.0, location.getLatitude(), 0.0001);
        assertEquals(0.0, location.getLongitude(), 0.0001);
    }

    @Test
    public void testSetAndGetLatitude() {
        location.setLatitude(39.9042);
        assertEquals(39.9042, location.getLatitude(), 0.0001);
    }

    @Test
    public void testSetAndGetLongitude() {
        location.setLongitude(116.4074);
        assertEquals(116.4074, location.getLongitude(), 0.0001);
    }

    @Test
    public void testNegativeLatitude() {
        location.setLatitude(-33.8688);
        assertEquals(-33.8688, location.getLatitude(), 0.0001);
    }

    @Test
    public void testNegativeLongitude() {
        location.setLongitude(-151.2093);
        assertEquals(-151.2093, location.getLongitude(), 0.0001);
    }

    @Test
    public void testZeroCoordinates() {
        location.setLatitude(0.0);
        location.setLongitude(0.0);
        assertEquals(0.0, location.getLatitude(), 0.0001);
        assertEquals(0.0, location.getLongitude(), 0.0001);
    }

    @Test
    public void testMaxLatitude() {
        location.setLatitude(90.0);
        assertEquals(90.0, location.getLatitude(), 0.0001);
    }

    @Test
    public void testMinLatitude() {
        location.setLatitude(-90.0);
        assertEquals(-90.0, location.getLatitude(), 0.0001);
    }

    @Test
    public void testMaxLongitude() {
        location.setLongitude(180.0);
        assertEquals(180.0, location.getLongitude(), 0.0001);
    }

    @Test
    public void testMinLongitude() {
        location.setLongitude(-180.0);
        assertEquals(-180.0, location.getLongitude(), 0.0001);
    }

    @Test
    public void testFieldOverwrite() {
        location.setLatitude(10.0);
        assertEquals(10.0, location.getLatitude(), 0.0001);
        location.setLatitude(20.0);
        assertEquals(20.0, location.getLatitude(), 0.0001);
    }

    @Test
    public void testHighPrecision() {
        location.setLatitude(39.90421111);
        location.setLongitude(116.40741111);
        assertEquals(39.90421111, location.getLatitude(), 0.00000001);
        assertEquals(116.40741111, location.getLongitude(), 0.00000001);
    }
}
