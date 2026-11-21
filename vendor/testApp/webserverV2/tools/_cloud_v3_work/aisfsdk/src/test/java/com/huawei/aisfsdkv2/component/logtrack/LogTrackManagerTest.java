/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logtrack;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * LogTrackManager 测试
 */
public class LogTrackManagerTest {

    @Test
    public void testGetInstanceNotNull() {
        assertNotNull(LogTrackManager.getInstance());
    }

    @Test
    public void testGetInstanceSingleton() {
        LogTrackManager instance1 = LogTrackManager.getInstance();
        LogTrackManager instance2 = LogTrackManager.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    public void testTrackSendNoException() {
        LogTrackManager.getInstance().track(LogTrackType.SEND, "test message");
    }

    @Test
    public void testTrackReceiveNoException() {
        LogTrackManager.getInstance().track(LogTrackType.RECEIVE, "test message");
    }

    @Test
    public void testTrackNullTypeNoException() {
        LogTrackManager.getInstance().track(null, "test message");
    }

    @Test
    public void testTrackNullDataNoException() {
        LogTrackManager.getInstance().track(LogTrackType.SEND, null);
    }

    @Test
    public void testTrackEmptyDataNoException() {
        LogTrackManager.getInstance().track(LogTrackType.SEND, "");
    }

    @Test
    public void testTrackWithJsonData() {
        String jsonData = "{\"Header\":{\"Sender\":\"TestSender\",\"Receiver\":\"TestReceiver\"},\"Body\":{}}";
        LogTrackManager.getInstance().track(LogTrackType.RECEIVE, jsonData);
    }

    @Test
    public void testTrackSendWithJsonHeader() {
        String jsonData = "{\"Header\":{\"Receiver\":\"SomeReceiver\"},\"Body\":{}}";
        LogTrackManager.getInstance().track(LogTrackType.SEND, jsonData);
    }

    @Test
    public void testTrackWithInvalidJson() {
        LogTrackManager.getInstance().track(LogTrackType.SEND, "not-a-json");
    }

    @Test
    public void testTrackReceiveWithEmptyHeader() {
        String jsonData = "{\"Header\":{},\"Body\":{}}";
        LogTrackManager.getInstance().track(LogTrackType.RECEIVE, jsonData);
    }

    @Test
    public void testTrackWithNoHeader() {
        String jsonData = "{\"Body\":{\"content\":\"test\"}}";
        LogTrackManager.getInstance().track(LogTrackType.SEND, jsonData);
    }
}
