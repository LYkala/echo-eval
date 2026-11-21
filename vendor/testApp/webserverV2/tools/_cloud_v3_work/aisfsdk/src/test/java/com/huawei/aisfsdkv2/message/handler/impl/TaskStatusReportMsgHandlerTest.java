/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler.impl;

import com.huawei.aisfsdkv2.api.session.OutputTypeEnum;
import com.huawei.aisfsdkv2.api.session.SessionResponseData;
import com.huawei.aisfsdkv2.api.session.TaskInfo;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * TaskStatusReportMsgHandler 测试
 */
public class TaskStatusReportMsgHandlerTest {

    private TaskStatusReportMsgHandler handler;
    private Method buildTaskCompletedResponse;

    @Before
    public void setUp() throws Exception {
        handler = new TaskStatusReportMsgHandler();

        buildTaskCompletedResponse = TaskStatusReportMsgHandler.class
                .getDeclaredMethod("buildTaskCompletedResponse", int.class, String.class, String.class);
        buildTaskCompletedResponse.setAccessible(true);
    }

    @Test
    public void testGetName() {
        assertEquals("TaskStatusReportMsgHandler", handler.getName());
    }

    @Test
    public void testGetPriority() {
        assertEquals(30, handler.getPriority());
    }

    @Test
    public void testHandleReceived_NullJson_ReturnsFalse() {
        assertFalse(handler.handleReceived(null));
    }

    @Test
    public void testHandleReceived_NoHeader_ReturnsFalse() throws Exception {
        JSONObject json = new JSONObject();
        assertFalse(handler.handleReceived(json));
    }

    @Test
    public void testHandleReceived_WrongHeaderMsgType_ReturnsFalse() throws Exception {
        JSONObject json = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("MessageType", "OtherType");
        json.put("Header", header);
        assertFalse(handler.handleReceived(json));
    }

    @Test
    public void testHandleReceived_NoData_ReturnsFalse() throws Exception {
        JSONObject json = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("MessageType", "TaskStatusManagement");
        json.put("Header", header);
        assertFalse(handler.handleReceived(json));
    }

    @Test
    public void testHandleReceived_WrongDataMsgType_ReturnsFalse() throws Exception {
        JSONObject json = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("MessageType", "TaskStatusManagement");
        JSONObject data = new JSONObject();
        data.put("MessageType", "OtherType");
        json.put("Header", header);
        json.put("Data", data);
        assertFalse(handler.handleReceived(json));
    }

    // ======= buildTaskCompletedResponse (reflection) =======

    @Test
    public void testBuildTaskCompletedResponse_Fields() throws Exception {
        SessionResponseData responseData = (SessionResponseData)
                buildTaskCompletedResponse.invoke(handler, 200, "task-456", "");
        assertNotNull(responseData);
        assertEquals(OutputTypeEnum.TASK_STATUS, responseData.getOutputType());
        TaskInfo taskInfo = responseData.getTaskInfo();
        assertNotNull(taskInfo);
        assertTrue(taskInfo.isTaskCompleted());
        assertEquals(200, taskInfo.getTaskStatusCode());
        assertEquals("task-456", taskInfo.getTaskId());
    }

    @Test
    public void testBuildTaskCompletedResponse_ErrorCode() throws Exception {
        SessionResponseData responseData = (SessionResponseData)
                buildTaskCompletedResponse.invoke(handler, 500, "task-err", "");
        TaskInfo taskInfo = responseData.getTaskInfo();
        assertEquals(500, taskInfo.getTaskStatusCode());
        assertTrue(taskInfo.isTaskCompleted());
    }

    @Test
    public void testBuildTaskCompletedResponse_EmptyTaskId() throws Exception {
        SessionResponseData responseData = (SessionResponseData)
                buildTaskCompletedResponse.invoke(handler, 0, "", "");
        TaskInfo taskInfo = responseData.getTaskInfo();
        assertEquals("", taskInfo.getTaskId());
        assertEquals(0, taskInfo.getTaskStatusCode());
    }

    @Test
    public void testBuildTaskCompletedResponse_WithUserOutputObject() throws Exception {
        SessionResponseData responseData = (SessionResponseData)
                buildTaskCompletedResponse.invoke(handler, 200, "task-789", "{\"code\":123}");
        assertNotNull(responseData);
        assertEquals(OutputTypeEnum.TASK_STATUS, responseData.getOutputType());
        TaskInfo taskInfo = responseData.getTaskInfo();
        assertNotNull(taskInfo);
        assertTrue(taskInfo.isTaskCompleted());
        assertEquals(200, taskInfo.getTaskStatusCode());
        assertEquals("task-789", taskInfo.getTaskId());
        assertNotNull(responseData.getNotifyInfo());
        assertEquals("{\"code\":123}", responseData.getNotifyInfo().getUserOutputObjectRawString());
    }
}
