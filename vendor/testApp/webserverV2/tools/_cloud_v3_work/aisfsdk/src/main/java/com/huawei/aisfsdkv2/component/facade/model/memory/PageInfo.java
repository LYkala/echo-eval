/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.memory;

/**
 * 分页信息。
 * <p>
 * 用于记忆查询结果的分页控制，包含当前页码、每页数量、
 * 总记录数和是否有下一页等信息。
 * </p>
 *
 * @author Huawei Technologies Co., Ltd.
 * @since 2024-01-01
 */
public class PageInfo {

    /**
     * 当前页码。
     * <p>
     * 从1开始计数
     * </p>
     */
    private int page;

    /**
     * 每页记录数。
     * <p>
     * 表示每页返回的最大记录数量
     * </p>
     */
    private int limit;

    /**
     * 总记录数。
     * <p>
     * 符合查询条件的所有记录总数
     * </p>
     */
    private int totalSize;

    /**
     * 是否有下一页。
     * <p>
     * true: 有下一页
     * false: 没有下一页
     * </p>
     */
    private boolean hasNext;

    /**
     * 默认构造方法。
     * <p>
     * 创建一个空的分页信息对象，各字段初始值为0
     * </p>
     */
    public PageInfo() {
    }

    /**
     * 带参数的构造方法。
     * <p>
     * 根据指定的分页参数创建分页信息对象
     * </p>
     *
     * @param page 当前页码（从1开始）
     * @param limit 每页记录数
     * @param totalSize 总记录数
     * @param hasNext 是否有下一页
     */
    public PageInfo(int page, int limit, int totalSize, boolean hasNext) {
        this.page = page;
        this.limit = limit;
        this.totalSize = totalSize;
        this.hasNext = hasNext;
    }

    /**
     * 获取当前页码。
     *
     * @return 当前页码（从1开始）
     */
    public int getPage() {
        return page;
    }

    /**
     * 设置当前页码。
     *
     * @param page 当前页码（从1开始）
     */
    public void setPage(int page) {
        this.page = page;
    }

    /**
     * 获取每页记录数。
     *
     * @return 每页记录数
     */
    public int getLimit() {
        return limit;
    }

    /**
     * 设置每页记录数。
     *
     * @param limit 每页记录数
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    /**
     * 获取总记录数。
     *
     * @return 符合查询条件的总记录数
     */
    public int getTotalSize() {
        return totalSize;
    }

    /**
     * 设置总记录数。
     *
     * @param totalSize 总记录数
     */
    public void setTotalSize(int totalSize) {
        this.totalSize = totalSize;
    }

    /**
     * 获取是否有下一页的标记。
     *
     * @return true 表示有下一页，false 表示没有
     */
    public boolean getHasNext() {
        return hasNext;
    }

    /**
     * 设置是否有下一页的标记。
     *
     * @param hasNext 是否有下一页
     */
    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }
}
