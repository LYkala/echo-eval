/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.memory;

import java.util.List;
import java.util.Map;

/**
 * 个人分类记忆结果。
 * <p>
 * 用于存储按分类组织的记忆信息，以分类名称为键，
 * 分类下的记忆项列表为值。
 * </p>
 *
 * @author Huawei Technologies Co., Ltd.
 * @since 2024-01-01
 */
public class PersonalClassifyMemoryResult {

    /**
     * 分类记忆映射。
     * <p>
     * Key: 分类名称（如"人物"、"地点"等）
     * Value: 该分类下的记忆项列表
     * </p>
     */
    private Map<String, List<PersonalClassifyMemoryItem>> categoryMap;

    /**
     * 获取分类记忆映射。
     *
     * @return 分类记忆映射，Key为分类名称，Value为该分类下的记忆项列表
     */
    public Map<String, List<PersonalClassifyMemoryItem>> getCategoryMap() {
        return categoryMap;
    }

    /**
     * 设置分类记忆映射。
     *
     * @param categoryMap 分类记忆映射，Key为分类名称，Value为该分类下的记忆项列表
     */
    public void setCategoryMap(Map<String, List<PersonalClassifyMemoryItem>> categoryMap) {
        this.categoryMap = categoryMap;
    }
}
