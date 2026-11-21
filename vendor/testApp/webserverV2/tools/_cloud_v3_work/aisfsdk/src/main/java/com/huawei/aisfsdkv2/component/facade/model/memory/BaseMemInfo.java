/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.memory;

/**
 * 基础记忆信息。
 * <p>
 * 用于存储基础的记忆信息，包括记忆ID、内容和创建日期。
 * </p>
 *
 * @author Huawei Technologies Co., Ltd.
 * @since 2024-01-01
 */
public class BaseMemInfo {

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
     * 创建日期。
     * <p>
     * 格式：yyyy-MM-dd
     * </p>
     */
    private String date;

    /**
     * 记忆类型
     */
    private String memType;

    /**
     * 默认构造方法。
     */
    public BaseMemInfo() {
    }

    /**
     * 带参数的构造方法。
     *
     * @param id      记忆ID
     * @param content 记忆内容
     * @param date    创建日期，格式：yyyy-MM-dd
     */
    public BaseMemInfo(String id, String content, String date) {
        this.id = id;
        this.content = content;
        this.date = date;
    }

    /**
     * 获取记忆ID。
     *
     * @return 记忆ID，可为null
     */
    public String getId() {
        return id;
    }

    /**
     * 设置记忆ID。
     *
     * @param id 记忆ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取记忆内容。
     *
     * @return 记忆内容，可为null
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置记忆内容。
     *
     * @param content 记忆内容
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * 获取创建日期。
     *
     * @return 创建日期，格式：yyyy-MM-dd，可为null
     */
    public String getDate() {
        return date;
    }

    /**
     * 设置创建日期。
     *
     * @param date 创建日期，格式：yyyy-MM-dd
     */
    public void setDate(String date) {
        this.date = date;
    }

    /**
     * 获取详情。
     *
     * @return 详情，可为null
     */
    public String getDetail() {
        return detail;
    }

    /**
     * 设置详情。
     *
     * @param detail 详情
     */
    public void setDetail(String detail) {
        this.detail = detail;
    }

    /**
     * 获取记忆类型。
     *
     * @return 记忆类型，可为null
     */
    public String getMemType() {
        return memType;
    }

    /**
     * 设置记忆类型。
     *
     * @param memType 记忆类型
     */
    public void setMemType(String memType) {
        this.memType = memType;
    }
}
