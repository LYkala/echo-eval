/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.memory;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆列表信息类
 * 
 * <p>封装记忆列表查询结果，包含记忆信息列表和分页信息。</p>
 */
public class MemoryListInfo {
    /**
     * 记忆信息列表
     */
    private List<BaseMemInfo> memInfoList = new ArrayList<>();

    /**
     * 分页信息
     */
    private PageInfo pageInfo;

    /**
     * 获取记忆信息列表
     *
     * @return 记忆信息列表，不为 null
     */
    public List<BaseMemInfo> getMemInfoList() {
        return memInfoList;
    }

    /**
     * 设置记忆信息列表
     *
     * @param memInfoList 记忆信息列表
     */
    public void setMemInfoList(List<BaseMemInfo> memInfoList) {
        this.memInfoList = memInfoList != null ? memInfoList : new ArrayList<>();
    }

    /**
     * 获取分页信息
     *
     * @return 分页信息对象
     */
    public PageInfo getPageInfo() {
        return pageInfo;
    }

    /**
     * 设置分页信息
     *
     * @param pageInfo 分页信息对象
     */
    public void setPageInfo(PageInfo pageInfo) {
        this.pageInfo = pageInfo;
    }
}
