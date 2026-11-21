/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.mimictask;

/**
 * 模仿任务动作类型枚举
 *
 * <p>映射 UserOutputObject.code 到语义化的动作类型。</p>
 *
 * @since 2026-04-21
 */
public enum MimicActionType {

    /** 开始技能 */
    START_SKILL(210001),

    /** 停止技能 */
    STOP_SKILL(210003),

    /** 异常停止技能 */
    STOP_ERROR_SKILL(210006),

    /** 未知类型 */
    UNKNOWN(-1);

    private final int code;

    MimicActionType(int code) {
        this.code = code;
    }

    /**
     * 获取协议 code 值
     *
     * @return 如 210001
     */
    public int getCode() {
        return code;
    }

    /**
     * 根据 code 值查找对应的枚举
     *
     * @param code 协议 code 值
     * @return 对应的 MimicActionType，未识别返回 UNKNOWN
     */
    public static MimicActionType fromCode(int code) {
        for (MimicActionType type : values()) {
            if (type != UNKNOWN && type.code == code) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
