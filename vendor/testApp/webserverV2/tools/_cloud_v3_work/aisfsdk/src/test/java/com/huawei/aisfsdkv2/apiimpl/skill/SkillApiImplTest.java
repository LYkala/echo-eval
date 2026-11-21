/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.skill;

import com.huawei.aisfsdkv2.api.skill.CreateSkillRequest;
import com.huawei.aisfsdkv2.api.skill.DeleteSkillRequest;
import com.huawei.aisfsdkv2.api.skill.SkillInfo;
import com.huawei.aisfsdkv2.component.facade.SkillFacade;
import com.huawei.aisfsdkv2.component.facade.model.skill.SkillException;
import com.huawei.aisfsdkv2.component.facade.model.skill.SkillModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * SkillApiImpl 单元测试
 *
 * <p>不使用 Robolectric，纯 Mockito 测试以获得正确的 JaCoCo 覆盖率。</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class SkillApiImplTest {

    @Mock
    private SkillFacade mockSkillFacade;

    private MockedStatic<SkillFacade> mockedStatic;

    @Before
    public void setUp() {
        mockedStatic = mockStatic(SkillFacade.class);
        mockedStatic.when(SkillFacade::getInstance).thenReturn(mockSkillFacade);
    }

    @After
    public void tearDown() {
        mockedStatic.close();
    }

    @Test
    public void testGetInstance() {
        SkillApiImpl instance = SkillApiImpl.getInstance();
        assertNotNull(instance);
    }

    @Test
    public void testGetInstance_SameInstance() {
        SkillApiImpl instance1 = SkillApiImpl.getInstance();
        SkillApiImpl instance2 = SkillApiImpl.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    public void testGetSkillList_Success() throws Exception {
        List<SkillModel> mockList = new ArrayList<>();
        SkillModel model = new SkillModel();
        model.setSkillId("skill-123");
        model.setSkillName("Test Skill");
        mockList.add(model);

        when(mockSkillFacade.querySkillList()).thenReturn(mockList);

        List<SkillInfo> result = SkillApiImpl.getInstance().getSkillList();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("skill-123", result.get(0).getSkillId());
    }

    @Test
    public void testGetSkillList_EmptyList() throws Exception {
        when(mockSkillFacade.querySkillList()).thenReturn(new ArrayList<>());

        List<SkillInfo> result = SkillApiImpl.getInstance().getSkillList();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetSkillList_NullList() throws Exception {
        when(mockSkillFacade.querySkillList()).thenReturn(null);

        List<SkillInfo> result = SkillApiImpl.getInstance().getSkillList();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetSkillList_Exception() throws Exception {
        when(mockSkillFacade.querySkillList())
            .thenThrow(new SkillException(-1, "Test error"));

        List<SkillInfo> result = SkillApiImpl.getInstance().getSkillList();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testCreateSkill_Success() throws Exception {
        CreateSkillRequest request = new CreateSkillRequest();
        when(mockSkillFacade.createSkill(any())).thenReturn("skill-123");

        String result = SkillApiImpl.getInstance().createSkill(request);
        assertEquals("skill-123", result);
    }

    @Test
    public void testCreateSkill_NullRequest() {
        String result = SkillApiImpl.getInstance().createSkill(null);
        assertNull(result);
    }

    @Test
    public void testCreateSkill_Exception() throws Exception {
        CreateSkillRequest request = new CreateSkillRequest();
        when(mockSkillFacade.createSkill(any()))
            .thenThrow(new SkillException(-1, "Test error"));

        String result = SkillApiImpl.getInstance().createSkill(request);
        assertNull(result);
    }

    @Test
    public void testDeleteSkill_Success() throws Exception {
        DeleteSkillRequest request = new DeleteSkillRequest();
        when(mockSkillFacade.deleteSkill(any())).thenReturn(true);

        assertTrue(SkillApiImpl.getInstance().deleteSkill(request));
    }

    @Test
    public void testDeleteSkill_NullRequest() {
        assertFalse(SkillApiImpl.getInstance().deleteSkill(null));
    }

    @Test
    public void testDeleteSkill_Exception() throws Exception {
        DeleteSkillRequest request = new DeleteSkillRequest();
        when(mockSkillFacade.deleteSkill(any()))
            .thenThrow(new SkillException(-1, "Test error"));

        assertFalse(SkillApiImpl.getInstance().deleteSkill(request));
    }

    @Test
    public void testConvertSkillList_FullFields() throws Exception {
        List<SkillModel> mockList = new ArrayList<>();
        SkillModel model = new SkillModel();
        model.setSkillId("skill-123");
        model.setSkillName("Test Skill");
        model.setSteps("step1,step2");
        model.setSummary("Test summary");
        model.setIsDefault("true");
        model.setApp("TestApp");
        model.setVariables("var1,var2");
        mockList.add(model);

        when(mockSkillFacade.querySkillList()).thenReturn(mockList);

        List<SkillInfo> result = SkillApiImpl.getInstance().getSkillList();
        assertEquals(1, result.size());
        SkillInfo info = result.get(0);
        assertEquals("skill-123", info.getSkillId());
        assertEquals("Test Skill", info.getSkillName());
        assertEquals("step1,step2", info.getSteps());
        assertEquals("Test summary", info.getSummary());
        assertEquals("true", info.getIsDefault());
        assertEquals("TestApp", info.getApp());
        assertEquals("var1,var2", info.getVariables());
    }
}
