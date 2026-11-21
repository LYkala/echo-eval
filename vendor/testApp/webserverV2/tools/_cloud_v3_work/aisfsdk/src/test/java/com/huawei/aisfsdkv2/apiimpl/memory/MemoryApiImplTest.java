/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.memory;

import com.huawei.aisfsdkv2.api.memory.MemCategoryEnum;
import com.huawei.aisfsdkv2.api.memory.MemoryListResult;
import com.huawei.aisfsdkv2.component.facade.MemoryFacade;
import com.huawei.aisfsdkv2.component.facade.model.memory.BaseMemInfo;
import com.huawei.aisfsdkv2.component.facade.model.memory.MemoryException;
import com.huawei.aisfsdkv2.component.facade.model.memory.MemoryListInfo;
import com.huawei.aisfsdkv2.component.facade.model.memory.PageInfo;
import com.huawei.aisfsdkv2.component.facade.model.memory.PersonalClassifyMemoryResult;

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
 * MemoryApiImpl 单元测试
 *
 * <p>不使用 Robolectric，纯 Mockito 测试以获得正确的 JaCoCo 覆盖率。</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class MemoryApiImplTest {

    @Mock
    private MemoryFacade mockMemoryFacade;

    private MockedStatic<MemoryFacade> mockedStatic;

    @Before
    public void setUp() {
        mockedStatic = mockStatic(MemoryFacade.class);
        mockedStatic.when(MemoryFacade::getInstance).thenReturn(mockMemoryFacade);
    }

    @After
    public void tearDown() {
        mockedStatic.close();
    }

    @Test
    public void testGetInstance() {
        MemoryApiImpl instance = MemoryApiImpl.getInstance();
        assertNotNull(instance);
    }

    @Test
    public void testGetInstance_SameInstance() {
        MemoryApiImpl instance1 = MemoryApiImpl.getInstance();
        MemoryApiImpl instance2 = MemoryApiImpl.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    public void testGetMemoryList_Success() throws Exception {
        MemoryListInfo mockResult = createMockMemoryListInfo(1, false);
        when(mockMemoryFacade.queryMemoryList(any())).thenReturn(mockResult);

        List<?> result = MemoryApiImpl.getInstance().getMemoryList(MemCategoryEnum.COMMON);
        assertNotNull(result);
    }

    @Test
    public void testGetMemoryList_Exception() throws Exception {
        when(mockMemoryFacade.queryMemoryList(any()))
            .thenThrow(new MemoryException(-1, "Test error"));

        List<?> result = MemoryApiImpl.getInstance().getMemoryList(MemCategoryEnum.COMMON);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetMemoryListWithPage_Success() throws Exception {
        MemoryListInfo mockResult = createMockMemoryListInfo(1, false);
        when(mockMemoryFacade.queryMemoryList(any())).thenReturn(mockResult);

        MemoryListResult result = MemoryApiImpl.getInstance()
            .getMemoryListWithPage(MemCategoryEnum.COMMON, 1, 10);
        assertNotNull(result);
    }

    @Test
    public void testGetMemoryListWithPage_Exception() throws Exception {
        when(mockMemoryFacade.queryMemoryList(any()))
            .thenThrow(new MemoryException(-1, "Test error"));

        MemoryListResult result = MemoryApiImpl.getInstance()
            .getMemoryListWithPage(MemCategoryEnum.COMMON, 1, 10);
        assertNotNull(result);
        assertTrue(result.getMemoryList().isEmpty());
    }

    @Test
    public void testGetPersonalClassifyMemory_Success() throws Exception {
        PersonalClassifyMemoryResult mockResult = new PersonalClassifyMemoryResult();
        Map<String, List<com.huawei.aisfsdkv2.component.facade.model.memory.PersonalClassifyMemoryItem>> categoryMap = new HashMap<>();
        mockResult.setCategoryMap(categoryMap);
        when(mockMemoryFacade.queryPersonalClassifyMemory()).thenReturn(mockResult);

        Map<?, ?> result = MemoryApiImpl.getInstance().getPersonalClassifyMemory();
        assertNotNull(result);
    }

    @Test
    public void testGetPersonalClassifyMemory_Exception() throws Exception {
        when(mockMemoryFacade.queryPersonalClassifyMemory())
            .thenThrow(new MemoryException(-1, "Test error"));

        Map<?, ?> result = MemoryApiImpl.getInstance().getPersonalClassifyMemory();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testIsMemoryEnabled_Success() throws Exception {
        when(mockMemoryFacade.queryMemoryEnabled()).thenReturn(true);

        assertTrue(MemoryApiImpl.getInstance().isMemoryEnabled());
    }

    @Test
    public void testIsMemoryEnabled_False() throws Exception {
        when(mockMemoryFacade.queryMemoryEnabled()).thenReturn(false);

        assertFalse(MemoryApiImpl.getInstance().isMemoryEnabled());
    }

    @Test
    public void testIsMemoryEnabled_Exception() throws Exception {
        when(mockMemoryFacade.queryMemoryEnabled())
            .thenThrow(new MemoryException(-1, "Test error"));

        assertFalse(MemoryApiImpl.getInstance().isMemoryEnabled());
    }

    @Test
    public void testSetMemoryEnabled_Success() throws Exception {
        doNothing().when(mockMemoryFacade).setMemoryEnabled(true);

        assertTrue(MemoryApiImpl.getInstance().setMemoryEnabled(true));
    }

    @Test
    public void testSetMemoryEnabled_Exception() throws Exception {
        doThrow(new MemoryException(-1, "Test error"))
            .when(mockMemoryFacade).setMemoryEnabled(anyBoolean());

        assertFalse(MemoryApiImpl.getInstance().setMemoryEnabled(true));
    }

    @Test
    public void testDeleteMemory_Success() throws Exception {
        when(mockMemoryFacade.deleteMemory(anyString(), anyString())).thenReturn(true);

        assertTrue(MemoryApiImpl.getInstance().deleteMemory(MemCategoryEnum.COMMON, "mem123"));
    }

    @Test
    public void testDeleteMemory_Exception() throws Exception {
        when(mockMemoryFacade.deleteMemory(anyString(), anyString()))
            .thenThrow(new MemoryException(-1, "Test error"));

        assertFalse(MemoryApiImpl.getInstance().deleteMemory(MemCategoryEnum.COMMON, "mem123"));
    }

    private MemoryListInfo createMockMemoryListInfo(int page, boolean hasNext) {
        MemoryListInfo info = new MemoryListInfo();
        List<BaseMemInfo> memList = new ArrayList<>();
        BaseMemInfo memInfo = new BaseMemInfo();
        memInfo.setId("test-id");
        memInfo.setContent("test-content");
        memList.add(memInfo);
        info.setMemInfoList(memList);
        PageInfo pageInfo = new PageInfo();
        pageInfo.setPage(page);
        pageInfo.setLimit(10);
        pageInfo.setTotalSize(1);
        pageInfo.setHasNext(hasNext);
        info.setPageInfo(pageInfo);
        return info;
    }
}
