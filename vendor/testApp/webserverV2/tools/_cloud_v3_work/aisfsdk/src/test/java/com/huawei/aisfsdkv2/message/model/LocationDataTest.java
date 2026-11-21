/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * LocationData 单元测试
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LocationDataTest {

    @Test
    public void testGetSetMessageType() {
        LocationData data = new LocationData();
        data.setMessageType("LocationInfo");
        assertEquals("LocationInfo", data.getMessageType());
    }

    @Test
    public void testGetSetLatitude() {
        LocationData data = new LocationData();
        data.setLatitude("39.9042");
        assertEquals("39.9042", data.getLatitude());
    }

    @Test
    public void testGetSetLongitude() {
        LocationData data = new LocationData();
        data.setLongitude("116.4074");
        assertEquals("116.4074", data.getLongitude());
    }

    @Test
    public void testGetSetCloudPhoneInstanceId() {
        LocationData data = new LocationData();
        data.setCloudPhoneInstanceId("instance-001");
        assertEquals("instance-001", data.getCloudPhoneInstanceId());
    }

    @Test
    public void testDefaultValuesAreNull() {
        LocationData data = new LocationData();
        assertNull(data.getMessageType());
        assertNull(data.getLatitude());
        assertNull(data.getLongitude());
        assertNull(data.getCloudPhoneInstanceId());
    }

    @Test
    public void testToJson_nullFieldsDefaultToEmpty() throws JSONException {
        LocationData data = new LocationData();
        JSONObject json = data.toJson();
        assertEquals("", json.optString("MessageType"));
        assertEquals("", json.optString("Latitude"));
        assertEquals("", json.optString("Longitude"));
    }

    @Test
    public void testToJson_withAllFields() throws JSONException {
        LocationData data = new LocationData();
        data.setMessageType("LocationInfo");
        data.setLatitude("39.9042");
        data.setLongitude("116.4074");
        data.setCloudPhoneInstanceId("instance-001");
        JSONObject json = data.toJson();
        assertEquals("LocationInfo", json.optString("MessageType"));
        assertEquals("39.9042", json.optString("Latitude"));
        assertEquals("116.4074", json.optString("Longitude"));
        assertEquals("instance-001", json.optString("instanceNum"));
        assertEquals("instance-001", json.optString("cloudPhoneInstanceId"));
    }

    @Test
    public void testToJson_cloudPhoneInstanceIdNullNotIncluded() throws JSONException {
        LocationData data = new LocationData();
        data.setMessageType("LocationInfo");
        JSONObject json = data.toJson();
        assertFalse(json.has("instanceNum"));
        assertFalse(json.has("cloudPhoneInstanceId"));
    }

    @Test
    public void testToJson_notNull() throws JSONException {
        LocationData data = new LocationData();
        assertNotNull(data.toJson());
    }
}
