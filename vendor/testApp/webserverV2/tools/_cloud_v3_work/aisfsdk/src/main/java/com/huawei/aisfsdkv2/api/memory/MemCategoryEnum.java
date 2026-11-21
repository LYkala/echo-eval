/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.memory;

/**
 * 记忆类型枚举
 *
 * <p>用于指定查询或删除记忆时的类型范围。</p>
 *
 * @since 2026-04-07
 */
public enum MemCategoryEnum {

    /**
     * 所有类型
     */
    ALL("all"),

    /**
     * 通用记忆
     */
    COMMON("common"),

    /**
     * 核心记忆
     */
    CORE("core"),

    /**
     * 语义记忆
     */
    SEMANTIC("semantic"),

    /**
     * 情节记忆
     */
    EPISODIC("episodic"),

    /**
     * 用户输入记忆
     */
    USER_INPUT("userInput");

    private final String value;

    MemCategoryEnum(String value) {
        this.value = value;
    }

    /**
     * 获取对应的字符串值（用于服务端接口传参）
     *
     * @return 枚举对应的字符串值
     */
    public String getValue() {
        return value;
    }

    /**
     * 将字符串转换为枚举值
     *
     * <p>支持按枚举名称（如 "ALL"）或字符串值（如 "all"）进行匹配，忽略大小写。
     * 如果不匹配则返回 {@link #ALL}。</p>
     *
     * @param value 字符串值，可为 null 或空
     * @return 对应的枚举值，如果不匹配则返回 ALL
     */
    public static MemCategoryEnum fromValue(String value) {
        if (value == null || value.isEmpty()) {
            return ALL;
        }
        for (MemCategoryEnum e : values()) {
            if (e.value.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        return ALL;
    }
}
