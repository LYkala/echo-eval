/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.memory;

/**
 * 基础记忆信息
 *
 * @since 2026-04-07
 */
public class MemoryInfo {
    /**
     * 记忆ID
     */
    private String id;

    /**
     * 记忆内容
     */
    private String content;

    /**
     * 详情
     */
    private String detail;

    /**
     * 创建日期
     */
    private String date;

    /**
     * 记忆类型
     */
    private String memType;

    /**
     * 默认构造方法
     */
    public MemoryInfo() {
    }

    /**
     * 获取记忆ID
     *
     * @return 记忆ID
     */
    public String getId() {
        return id;
    }

    /**
     * 设置记忆ID
     *
     * @param id 记忆ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取记忆内容
     *
     * @return 记忆内容
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置记忆内容
     *
     * @param content 记忆内容
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * 获取详情
     *
     * @return 详情
     */
    public String getDetail() {
        return detail;
    }

    /**
     * 设置详情
     *
     * @param detail 详情
     */
    public void setDetail(String detail) {
        this.detail = detail;
    }

    /**
     * 获取创建日期
     *
     * @return 创建日期
     */
    public String getDate() {
        return date;
    }

    /**
     * 设置创建日期
     *
     * @param date 创建日期
     */
    public void setDate(String date) {
        this.date = date;
    }

    /**
     * 获取记忆类型
     *
     * @return 记忆类型
     */
    public String getMemType() {
        return memType;
    }

    /**
     * 设置记忆类型
     *
     * @param memType 记忆类型
     */
    public void setMemType(String memType) {
        this.memType = memType;
    }

    @Override
    public String toString() {
        return "MemoryInfo{id='" + id + "', content='" + content + "', detail='" + detail
                + "', date='" + date + "', memType='" + memType + "'}";
    }
}
