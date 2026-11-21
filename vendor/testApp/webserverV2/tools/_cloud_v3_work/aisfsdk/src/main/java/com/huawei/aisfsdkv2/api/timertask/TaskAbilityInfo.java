/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.timertask;

/**
 * 任务能力信息
 *
 * @since 2026-04-07
 */
public class TaskAbilityInfo {
    /**
     * 能力ID
     */
    private String id;

    /**
     * 展示标签
     */
    private String showLabel;

    /**
     * 是否为默认能力
     */
    private boolean isDefault;

    /**
     * 能力描述
     */
    private String description;

    /**
     * 默认构造方法
     */
    public TaskAbilityInfo() {
    }

    /**
     * 获取能力ID
     *
     * @return 能力ID
     */
    public String getId() {
        return id;
    }

    /**
     * 设置能力ID
     *
     * @param id 能力ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取展示标签
     *
     * @return 展示标签
     */
    public String getShowLabel() {
        return showLabel;
    }

    /**
     * 设置展示标签
     *
     * @param showLabel 展示标签
     */
    public void setShowLabel(String showLabel) {
        this.showLabel = showLabel;
    }

    /**
     * 获取是否为默认能力
     *
     * @return true表示默认能力
     */
    public boolean isDefault() {
        return isDefault;
    }

    /**
     * 设置是否为默认能力
     *
     * @param isDefault 是否为默认能力
     */
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    /**
     * 获取能力描述
     *
     * @return 能力描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置能力描述
     *
     * @param description 能力描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "TaskAbilityInfo{id='" + id + "', showLabel='" + showLabel
                + "', isDefault=" + isDefault + ", description='" + description + "'}";
    }
}
