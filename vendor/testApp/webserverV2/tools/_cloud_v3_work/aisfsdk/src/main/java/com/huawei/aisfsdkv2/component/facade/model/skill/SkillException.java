/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.skill;

/**
 * 技能异常
 *
 * @since 2026-04-07
 */
public class SkillException extends Exception {

    private final int code;

    /**
     * 构造方法
     *
     * @param code 错误码
     * @param message 错误信息
     */
    public SkillException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    public int getCode() {
        return code;
    }
}
