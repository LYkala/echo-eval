/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.memory;

import java.util.List;
import java.util.Map;

/**
 * 记忆接口
 *
 * @since 2026-04-07
 */
public interface MemoryApi {

    /**
     * 获取记忆列表
     *
     * @param category 记忆类型
     * @return 对应类型的记忆列表
     */
    List<MemoryInfo> getMemoryList(MemCategoryEnum category);

    /**
     * 分页获取记忆列表
     *
     * @param category 记忆类型
     * @param page     页码
     * @param pageSize 每页大小
     * @return 记忆列表结果（含分页信息）
     */
    MemoryListResult getMemoryListWithPage(MemCategoryEnum category, int page, int pageSize);

    /**
     * 获取个人分类记忆
     *
     * @return 分类记忆，key为topic，value为该topic下的记忆列表
     */
    Map<String, List<PersonalClassifyMemoryInfo>> getPersonalClassifyMemory();

    /**
     * 获取记忆功能是否开启
     *
     * @return true表示已开启，false表示已关闭
     */
    boolean isMemoryEnabled();

    /**
     * 设置记忆功能开关
     *
     * @param enabled true表示开启，false表示关闭
     * @return true表示设置成功，false表示设置失败
     */
    boolean setMemoryEnabled(boolean enabled);

    /**
     * 删除指定记忆
     *
     * @param category 记忆类型
     * @param memoryId   记忆ID
     * @return true表示删除成功，false表示删除失败
     */
    boolean deleteMemory(MemCategoryEnum category, String memoryId);
}
