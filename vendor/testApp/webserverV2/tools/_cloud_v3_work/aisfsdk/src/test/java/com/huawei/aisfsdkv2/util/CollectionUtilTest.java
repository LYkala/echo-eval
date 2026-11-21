/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * CollectionUtil 测试
 */
public class CollectionUtilTest {

    // ======= newArrayList =======

    @Test
    public void testNewArrayList() {
        List<String> list = CollectionUtil.newArrayList();
        assertNotNull(list);
        assertTrue(list instanceof ArrayList);
        assertTrue(list.isEmpty());
    }

    @Test
    public void testNewArrayList_WithCapacity() {
        List<String> list = CollectionUtil.newArrayList(100);
        assertNotNull(list);
        assertTrue(list instanceof ArrayList);
    }

    // ======= newLinkedList =======

    @Test
    public void testNewLinkedList() {
        List<String> list = CollectionUtil.newLinkedList();
        assertNotNull(list);
        assertTrue(list instanceof LinkedList);
    }

    // ======= newHashSet =======

    @Test
    public void testNewHashSet() {
        Set<String> set = CollectionUtil.newHashSet();
        assertNotNull(set);
        assertTrue(set instanceof HashSet);
    }

    @Test
    public void testNewHashSet_WithCapacity() {
        Set<String> set = CollectionUtil.newHashSet(50);
        assertNotNull(set);
        assertTrue(set instanceof HashSet);
    }

    // ======= newHashMap =======

    @Test
    public void testNewHashMap() {
        HashMap<String, String> map = CollectionUtil.newHashMap();
        assertNotNull(map);
        assertTrue(map instanceof HashMap);
    }

    @Test
    public void testNewHashMap_WithCapacity() {
        HashMap<String, String> map = CollectionUtil.newHashMap(32);
        assertNotNull(map);
        assertTrue(map instanceof HashMap);
    }

    // ======= newConcurrentHashMap =======

    @Test
    public void testNewConcurrentHashMap() {
        ConcurrentHashMap<String, String> map = CollectionUtil.newConcurrentHashMap();
        assertNotNull(map);
        assertTrue(map instanceof ConcurrentHashMap);
    }

    // ======= isEmpty =======

    @Test
    public void testIsEmpty_Null() {
        assertTrue(CollectionUtil.isEmpty(null));
    }

    @Test
    public void testIsEmpty_Empty() {
        assertTrue(CollectionUtil.isEmpty(new ArrayList<>()));
    }

    @Test
    public void testIsEmpty_NonEmpty() {
        List<String> list = Arrays.asList("a", "b");
        assertFalse(CollectionUtil.isEmpty(list));
    }

    // ======= isNotEmpty =======

    @Test
    public void testIsNotEmpty_Null() {
        assertFalse(CollectionUtil.isNotEmpty(null));
    }

    @Test
    public void testIsNotEmpty_Empty() {
        assertFalse(CollectionUtil.isNotEmpty(new ArrayList<>()));
    }

    @Test
    public void testIsNotEmpty_NonEmpty() {
        List<String> list = Arrays.asList("a");
        assertTrue(CollectionUtil.isNotEmpty(list));
    }

    // ======= exist =======

    @Test
    public void testExist_NullCollection() {
        assertFalse(CollectionUtil.exist(null, "test"));
    }

    @Test
    public void testExist_NullTarget() {
        List<String> list = Arrays.asList("a", "b");
        assertFalse(CollectionUtil.exist(list, null));
    }

    @Test
    public void testExist_Found() {
        List<String> list = Arrays.asList("a", "b", "c");
        assertTrue(CollectionUtil.exist(list, "b"));
    }

    @Test
    public void testExist_NotFound() {
        List<String> list = Arrays.asList("a", "b", "c");
        assertFalse(CollectionUtil.exist(list, "d"));
    }

    @Test
    public void testExist_Integer() {
        List<Integer> list = Arrays.asList(1, 2, 3);
        assertTrue(CollectionUtil.exist(list, 2));
        assertFalse(CollectionUtil.exist(list, 4));
    }

    @Test
    public void testExist_EmptyCollection() {
        List<String> list = new ArrayList<>();
        assertFalse(CollectionUtil.exist(list, "a"));
    }
}
