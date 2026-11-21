/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.memory;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆列表结果（含分页信息）
 *
 * @since 2026-04-07
 */
public class MemoryListResult {
    /**
     * 记忆列表
     */
    private List<MemoryInfo> memoryList;

    /**
     * 分页信息
     */
    private MemoryPageInfo pageInfo;

    /**
     * 默认构造方法
     */
    public MemoryListResult() {
        this.memoryList = new ArrayList<>();
        this.pageInfo = new MemoryPageInfo();
    }

    /**
     * 获取记忆列表
     *
     * @return 记忆列表
     */
    public List<MemoryInfo> getMemoryList() {
        return memoryList;
    }

    /**
     * 设置记忆列表
     *
     * @param memoryList 记忆列表
     */
    public void setMemoryList(List<MemoryInfo> memoryList) {
        this.memoryList = memoryList;
    }

    /**
     * 获取分页信息
     *
     * @return 分页信息
     */
    public MemoryPageInfo getPageInfo() {
        return pageInfo;
    }

    /**
     * 设置分页信息
     *
     * @param pageInfo 分页信息
     */
    public void setPageInfo(MemoryPageInfo pageInfo) {
        this.pageInfo = pageInfo;
    }

    @Override
    public String toString() {
        return "MemoryListResult{memoryList=" + memoryList + ", pageInfo=" + pageInfo + "}";
    }
}
