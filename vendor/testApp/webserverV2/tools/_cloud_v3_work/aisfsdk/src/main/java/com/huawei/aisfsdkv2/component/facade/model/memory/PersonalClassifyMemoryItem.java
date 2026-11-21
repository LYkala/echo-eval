/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.memory;

/**
 * 个人分类记忆项。
 * <p>
 * 用于存储单个分类下的记忆项信息，包含主题（或子主题）
 * 和对应的内容。
 * </p>
 *
 * @author Huawei Technologies Co., Ltd.
 * @since 2024-01-01
 */
public class PersonalClassifyMemoryItem {

    /**
     * 记忆ID
     */
    private String id;

    /**
     * 记忆类型
     */
    private String memType;

    /**
     * 主题/子主题。
     * <p>
     * 记忆项的主题或子主题名称，用于标识记忆项的类别或标题
     * </p>
     */
    private String topic;

    /**
     * 内容。
     * <p>
     * 记忆项的具体内容描述
     * </p>
     */
    private String content;

    /**
     * 默认构造方法。
     * <p>
     * 创建一个空的分类记忆项对象
     * </p>
     */
    public PersonalClassifyMemoryItem() {
    }

    /**
     * 带参数的构造方法。
     * <p>
     * 根据指定的主题和内容创建分类记忆项对象
     * </p>
     *
     * @param id 记忆ID
     * @param memType 记忆类型
     * @param topic 主题/子主题
     * @param content 内容
     */
    public PersonalClassifyMemoryItem(String id, String memType, String topic, String content) {
        this.id = id;
        this.memType = memType;
        this.topic = topic;
        this.content = content;
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

    /**
     * 获取主题/子主题。
     *
     * @return 主题/子主题，可为null
     */
    public String getTopic() {
        return topic;
    }

    /**
     * 设置主题/子主题。
     *
     * @param topic 主题/子主题
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    /**
     * 获取内容。
     *
     * @return 内容，可为null
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置内容。
     *
     * @param content 内容
     */
    public void setContent(String content) {
        this.content = content;
    }
}
