/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.memory;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * PersonalClassifyMemoryResult 测试
 */
public class PersonalClassifyMemoryResultTest {

    @Test
    public void testDefaultConstructor() {
        PersonalClassifyMemoryResult result = new PersonalClassifyMemoryResult();
        assertNull(result.getCategoryMap());
    }

    @Test
    public void testSetAndGetCategoryMap() {
        PersonalClassifyMemoryResult result = new PersonalClassifyMemoryResult();
        Map<String, List<PersonalClassifyMemoryItem>> map = new HashMap<>();
        List<PersonalClassifyMemoryItem> items = new ArrayList<>();
        items.add(new PersonalClassifyMemoryItem("id-1", "core", "hobby", "likes reading"));
        map.put("Interests", items);
        result.setCategoryMap(map);
        assertNotNull(result.getCategoryMap());
        assertEquals(1, result.getCategoryMap().size());
        assertTrue(result.getCategoryMap().containsKey("Interests"));
        assertEquals(1, result.getCategoryMap().get("Interests").size());
    }

    @Test
    public void testSetCategoryMapNull() {
        PersonalClassifyMemoryResult result = new PersonalClassifyMemoryResult();
        result.setCategoryMap(null);
        assertNull(result.getCategoryMap());
    }

    @Test
    public void testMultipleCategories() {
        PersonalClassifyMemoryResult result = new PersonalClassifyMemoryResult();
        Map<String, List<PersonalClassifyMemoryItem>> map = new HashMap<>();
        map.put("Work", new ArrayList<>());
        map.put("Family", new ArrayList<>());
        map.put("Health", new ArrayList<>());
        result.setCategoryMap(map);
        assertEquals(3, result.getCategoryMap().size());
    }
}
