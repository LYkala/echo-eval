/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 集合工具类
 *
 * @author l00504561
 * @since 2026-02-06
 */
public class CollectionUtil {
    private static final int MAP_INIT_CAPACITY = 16;

    /**
     * 创建ArrayList
     *
     * @param <T> 类型
     * @return ArrayList
     */
    public static <T> List<T> newArrayList() {
        return new ArrayList<>();
    }

    /**
     * 创建ArrayList
     *
     * @param initCapacity 初始容量
     * @param <T>          类型
     * @return ArrayList
     */
    public static <T> List<T> newArrayList(int initCapacity) {
        return new ArrayList<>(initCapacity);
    }

    /**
     * 创建LinkedList
     *
     * @param <T> 类型
     * @return LinkedList
     */
    public static <T> List<T> newLinkedList() {
        return new LinkedList<>();
    }

    /**
     * 创建hashSet
     *
     * @param <T> 类型
     * @return HashSet
     */
    public static <T> Set<T> newHashSet() {
        return new HashSet<>();
    }

    /**
     * 创建hashSet
     *
     * @param initCapacity 初始容量
     * @param <T>          类型
     * @return HashSet
     */
    public static <T> Set<T> newHashSet(int initCapacity) {
        return new HashSet<>(initCapacity);
    }

    /**
     * 创建hashMap
     *
     * @param <K> key类型
     * @param <V> value类型
     * @return HashMap
     */
    public static <K, V> HashMap<K, V> newHashMap() {
        return new HashMap<>(MAP_INIT_CAPACITY);
    }

    /**
     * 创建hashMap
     *
     * @param initCapacity 初始容量
     * @param <K>          key类型
     * @param <V>          value类型
     * @return HashMap
     */
    public static <K, V> HashMap<K, V> newHashMap(int initCapacity) {
        return new HashMap<>(initCapacity);
    }

    /**
     * 创建hashMap
     *
     * @param <K> key类型
     * @param <V> value类型
     * @return ConcurrentHashMap
     */
    public static <K, V> ConcurrentHashMap<K, V> newConcurrentHashMap() {
        return new ConcurrentHashMap<>();
    }

    /**
     * 集合判空
     *
     * @param collection 集合对象
     * @param <T>        类型
     * @return true 如果是空或者为空集合
     */
    public static <T> boolean isEmpty(Collection<T> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * 集合判非空
     *
     * @param collection 集合对象
     * @param <T>        类型
     * @return true 如果不是空或者不为空集合
     */
    public static <T> boolean isNotEmpty(Collection<T> collection) {
        boolean result = isEmpty(collection);
        return !result;
    }

    /**
     * 判断是否存在
     *
     * @param collection 集合
     * @param target     目标
     * @param <T>        类型
     * @return 是否存在
     */
    public static <T> boolean exist(Collection<T> collection, T target) {
        if (collection == null || target == null) {
            return false;
        }
        for (T t : collection) {
            if (target.equals(t)) {
                return true;
            }
        }
        return false;
    }
}
