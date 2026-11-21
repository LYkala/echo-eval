/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.timertask;

import com.huawei.aisfsdkv2.api.timertask.CreateScheduleTaskRequest;
import com.huawei.aisfsdkv2.api.timertask.DeleteScheduleTaskRequest;
import com.huawei.aisfsdkv2.api.timertask.TaskAbilityResult;
import com.huawei.aisfsdkv2.api.timertask.TaskSchedule;
import com.huawei.aisfsdkv2.api.timertask.TimerTaskModel;
import com.huawei.aisfsdkv2.api.timertask.UpdateScheduleTaskRequest;
import com.huawei.aisfsdkv2.component.facade.TimerTaskFacade;
import com.huawei.aisfsdkv2.component.facade.model.timertask.TaskAbilityModel;
import com.huawei.aisfsdkv2.component.facade.model.timertask.TimerTaskException;
import com.huawei.aisfsdkv2.timertask.TimerTaskManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * TimerTaskApiImpl 单元测试
 *
 * <p>不使用 Robolectric，纯 Mockito 测试以获得正确的 JaCoCo 覆盖率。</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class TimerTaskApiImplTest {

    @Mock
    private TimerTaskFacade mockTimerTaskFacade;

    @Mock
    private TimerTaskManager mockTimerTaskManager;

    private MockedStatic<TimerTaskFacade> mockedFacade;
    private MockedStatic<TimerTaskManager> mockedManager;

    @Before
    public void setUp() {
        mockedFacade = mockStatic(TimerTaskFacade.class);
        mockedFacade.when(TimerTaskFacade::getInstance).thenReturn(mockTimerTaskFacade);

        mockedManager = mockStatic(TimerTaskManager.class);
        mockedManager.when(TimerTaskManager::getInstance).thenReturn(mockTimerTaskManager);
    }

    @After
    public void tearDown() {
        mockedFacade.close();
        mockedManager.close();
    }

    @Test
    public void testGetInstance() {
        TimerTaskApiImpl instance = TimerTaskApiImpl.getInstance();
        assertNotNull(instance);
    }

    @Test
    public void testGetInstance_SameInstance() {
        TimerTaskApiImpl instance1 = TimerTaskApiImpl.getInstance();
        TimerTaskApiImpl instance2 = TimerTaskApiImpl.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    public void testGetTaskAbilityInfoList_Success() throws Exception {
        com.huawei.aisfsdkv2.component.facade.model.timertask.TaskAbilityResult facadeResult =
            new com.huawei.aisfsdkv2.component.facade.model.timertask.TaskAbilityResult();
        Map<String, List<TaskAbilityModel>> modelMap = new HashMap<>();
        List<TaskAbilityModel> modelList = new ArrayList<>();
        TaskAbilityModel model = new TaskAbilityModel();
        model.setId("ability-123");
        model.setShowLabel("Test Ability");
        model.setDefault(true);
        model.setDescription("Test description");
        modelList.add(model);
        modelMap.put("Skill", modelList);
        facadeResult.setTaskAbilityModelMap(modelMap);

        when(mockTimerTaskFacade.queryTaskAbilityList()).thenReturn(facadeResult);

        TaskAbilityResult result = TimerTaskApiImpl.getInstance().getTaskAbilityInfoList();
        assertNotNull(result);
        assertNotNull(result.getTaskAbilityInfoMap());
        assertTrue(result.getTaskAbilityInfoMap().containsKey("Skill"));
    }

    @Test
    public void testGetTaskAbilityInfoList_Exception() throws Exception {
        when(mockTimerTaskFacade.queryTaskAbilityList())
            .thenThrow(new TimerTaskException(-1, "Test error"));

        TaskAbilityResult result = TimerTaskApiImpl.getInstance().getTaskAbilityInfoList();
        assertNull(result);
    }

    @Test
    public void testGetScheduleTaskList() {
        TimerTaskModel<List<TaskSchedule>> mockModel = TimerTaskModel.success(new ArrayList<>());

        when(mockTimerTaskFacade.queryScheduleTaskList()).thenReturn(mockModel);

        TimerTaskModel<List<TaskSchedule>> result = TimerTaskApiImpl.getInstance().getScheduleTaskList();
        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    public void testCreateScheduleTask_NullRequest() {
        TimerTaskModel<String> result = TimerTaskApiImpl.getInstance().createScheduleTask(null);
        assertNotNull(result);
        assertEquals(-1, result.getRetCode());
        assertEquals("request is null", result.getRetMsg());
    }

    @Test
    public void testCreateScheduleTask_Success() {
        CreateScheduleTaskRequest request = new CreateScheduleTaskRequest();
        request.setEnable(true);

        TimerTaskModel<String> facadeResult = TimerTaskModel.success("task-123");

        when(mockTimerTaskFacade.createScheduleTask(any())).thenReturn(facadeResult);

        TimerTaskModel<String> result = TimerTaskApiImpl.getInstance().createScheduleTask(request);
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("task-123", result.getData());
    }

    @Test
    public void testCreateScheduleTask_Failed() {
        CreateScheduleTaskRequest request = new CreateScheduleTaskRequest();

        TimerTaskModel<String> facadeResult = TimerTaskModel.error(-1, "error");

        when(mockTimerTaskFacade.createScheduleTask(any())).thenReturn(facadeResult);

        TimerTaskModel<String> result = TimerTaskApiImpl.getInstance().createScheduleTask(request);
        assertNotNull(result);
        assertEquals(-1, result.getRetCode());
    }

    @Test
    public void testDeleteScheduleTask_NullRequest() {
        TimerTaskModel<Boolean> result = TimerTaskApiImpl.getInstance().deleteScheduleTask(null);
        assertNotNull(result);
        assertEquals(-1, result.getRetCode());
        assertEquals("request is null", result.getRetMsg());
    }

    @Test
    public void testDeleteScheduleTask_Success() {
        DeleteScheduleTaskRequest request = new DeleteScheduleTaskRequest();

        TimerTaskModel<Boolean> facadeResult = TimerTaskModel.success(true);

        when(mockTimerTaskFacade.deleteScheduleTask(any())).thenReturn(facadeResult);

        TimerTaskModel<Boolean> result = TimerTaskApiImpl.getInstance().deleteScheduleTask(request);
        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    public void testUpdateScheduleTask_NullRequest() {
        TimerTaskModel<Boolean> result = TimerTaskApiImpl.getInstance().updateScheduleTask(null);
        assertNotNull(result);
        assertEquals(-1, result.getRetCode());
        assertEquals("request is null", result.getRetMsg());
    }

    @Test
    public void testUpdateScheduleTask_Success() {
        UpdateScheduleTaskRequest request = new UpdateScheduleTaskRequest();
        request.setTaskId("task-123");
        request.setEnable(true);

        TimerTaskModel<Boolean> facadeResult = TimerTaskModel.success(true);

        when(mockTimerTaskFacade.updateScheduleTask(any())).thenReturn(facadeResult);

        TimerTaskModel<Boolean> result = TimerTaskApiImpl.getInstance().updateScheduleTask(request);
        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    public void testUpdateScheduleTask_Failed() {
        UpdateScheduleTaskRequest request = new UpdateScheduleTaskRequest();

        TimerTaskModel<Boolean> facadeResult = TimerTaskModel.error(-1, "error");

        when(mockTimerTaskFacade.updateScheduleTask(any())).thenReturn(facadeResult);

        TimerTaskModel<Boolean> result = TimerTaskApiImpl.getInstance().updateScheduleTask(request);
        assertNotNull(result);
        assertEquals(-1, result.getRetCode());
    }

    @Test
    public void testConvertAbilityList_Null() throws Exception {
        com.huawei.aisfsdkv2.component.facade.model.timertask.TaskAbilityResult facadeResult =
            new com.huawei.aisfsdkv2.component.facade.model.timertask.TaskAbilityResult();
        facadeResult.setTaskAbilityModelMap(null);

        when(mockTimerTaskFacade.queryTaskAbilityList()).thenReturn(facadeResult);

        TaskAbilityResult result = TimerTaskApiImpl.getInstance().getTaskAbilityInfoList();
        assertNotNull(result);
        assertTrue(result.getTaskAbilityInfoMap().isEmpty());
    }
}
