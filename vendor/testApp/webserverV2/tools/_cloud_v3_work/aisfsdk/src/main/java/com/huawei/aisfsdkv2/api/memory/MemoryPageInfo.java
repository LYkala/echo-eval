/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.memory;

/**
 * 记忆分页信息
 *
 * @since 2026-04-07
 */
public class MemoryPageInfo {
    /**
     * 当前页码
     */
    private int page;

    /**
     * 每页记录数
     */
    private int limit;

    /**
     * 总记录数（"all"场景下建议使用hasNext判断分页）
     */
    private int totalSize;

    /**
     * 是否有下一页
     */
    private boolean hasNext;

    /**
     * 默认构造方法
     */
    public MemoryPageInfo() {
    }

    /**
     * 获取当前页码
     *
     * @return 当前页码
     */
    public int getPage() {
        return page;
    }

    /**
     * 设置当前页码
     *
     * @param page 当前页码
     */
    public void setPage(int page) {
        this.page = page;
    }

    /**
     * 获取每页记录数
     *
     * @return 每页记录数
     */
    public int getLimit() {
        return limit;
    }

    /**
     * 设置每页记录数
     *
     * @param limit 每页记录数
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    /**
     * 获取总记录数
     *
     * @return 总记录数
     */
    public int getTotalSize() {
        return totalSize;
    }

    /**
     * 设置总记录数
     *
     * @param totalSize 总记录数
     */
    public void setTotalSize(int totalSize) {
        this.totalSize = totalSize;
    }

    /**
     * 获取是否有下一页
     *
     * @return true 表示有下一页，false 表示没有
     */
    public boolean getHasNext() {
        return hasNext;
    }

    /**
     * 设置是否有下一页
     *
     * @param hasNext 是否有下一页
     */
    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }

    @Override
    public String toString() {
        return "MemoryPageInfo{page=" + page + ", limit=" + limit
                + ", totalSize=" + totalSize + ", hasNext=" + hasNext + "}";
    }
}
