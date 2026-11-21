/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.memory;

import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 查询记忆列表请求类
 * 
 * <p>用于构建查询记忆列表的请求参数，支持设置分类、排序方式、分页等条件。</p>
 * <p>默认参数：</p>
 * <ul>
 *   <li>category: "all" (所有分类)</li>
 *   <li>order: "DESC" (降序排列)</li>
 *   <li>limit: 50 (每页50条)</li>
 *   <li>page: 1 (第一页)</li>
 * </ul>
 */
public class QueryMemoryListRequest {
    private static final Logger LOGGER = LoggerFactory.getLogger("QueryMemoryListRequest");

    /**
     * 记忆分类，默认为"all"表示所有分类
     */
    private String category = "all";

    /**
     * 排序方式，默认为"DESC"表示降序，支持"ASC"升序
     */
    private String order = "DESC";

    /**
     * 每页返回数量，默认为50
     */
    private int limit = 50;

    /**
     * 页码，从1开始，默认为1
     */
    private int page = 1;

    /**
     * 默认构造方法，使用默认参数创建请求对象
     */
    public QueryMemoryListRequest() {
    }

    /**
     * 带分类参数的构造方法
     *
     * @param category 记忆分类名称
     */
    public QueryMemoryListRequest(String category) {
        this.category = category;
    }

    /**
     * 带分类和分页参数的构造方法
     *
     * @param category 记忆分类名称
     * @param page     页码，从1开始
     * @param limit    每页返回数量
     */
    public QueryMemoryListRequest(String category, int page, int limit) {
        this.category = category;
        this.page = page;
        this.limit = limit;
    }

    /**
     * 获取记忆分类
     *
     * @return 记忆分类名称，默认"all"
     */
    public String getCategory() {
        return category;
    }

    /**
     * 设置记忆分类
     *
     * @param category 记忆分类名称，如"all"表示所有分类
     */
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * 获取排序方式
     *
     * @return 排序方式，"ASC"升序或"DESC"降序，默认"DESC"
     */
    public String getOrder() {
        return order;
    }

    /**
     * 设置排序方式
     *
     * @param order 排序方式，"ASC"升序或"DESC"降序
     */
    public void setOrder(String order) {
        this.order = order;
    }

    /**
     * 获取每页返回数量
     *
     * @return 每页返回数量，默认50
     */
    public int getLimit() {
        return limit;
    }

    /**
     * 设置每页返回数量
     *
     * @param limit 每页返回数量
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    /**
     * 获取当前页码
     *
     * @return 页码，从1开始，默认1
     */
    public int getPage() {
        return page;
    }

    /**
     * 设置当前页码
     *
     * @param page 页码，从1开始
     */
    public void setPage(int page) {
        this.page = page;
    }

    /**
     * 将请求对象转换为JSON字符串
     *
     * <p>包含以下字段：</p>
     * <ul>
     *   <li>category - 记忆分类</li>
     *   <li>order - 排序方式</li>
     *   <li>limit - 每页数量</li>
     *   <li>page - 页码</li>
     * </ul>
     *
     * @return JSON格式的字符串
     */
    public String toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("category", category);
            json.put("order", order);
            json.put("limit", limit);
            json.put("page", page);
        } catch (JSONException e) {
            LOGGER.error("QueryMemoryListRequest.toJson: QueryMemoryListRequest toJson failed: {}", e.getMessage());
        }
        return json.toString();
    }

    @Override
    public String toString() {
        return "QueryMemoryListRequest{category='" + category + "', order='" + order
                + "', limit=" + limit + ", page=" + page + "}";
    }
}
