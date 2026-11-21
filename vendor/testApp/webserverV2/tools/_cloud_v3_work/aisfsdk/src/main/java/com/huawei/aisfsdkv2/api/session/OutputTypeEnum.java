/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

/**
 * 用户输出类型枚举
 *
 * <p>定义会话响应数据的类型。</p>
 */
public enum OutputTypeEnum {
    /**
     * 文本类型
     */
    TEXT("TEXT"),

    /**
     * 通知类型
     */
    NOTIFY("NOTIFY"),

    /**
     * 任务状态类型
     */
    TASK_STATUS("TASK_STATUS"),

    /**
     * GUI任务类型
     */
    GUI_TASK("GUI_TASK"),

    /**
     * 弹窗类型
     */
    DIALOG("DIALOG");

    private final String description;

    OutputTypeEnum(String description) {
        this.description = description;
    }

    /**
     * 获取描述信息
     *
     * @return 描述字符串
     */
    public String getDescription() {
        return description;
    }
}
