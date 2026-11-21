/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.memory;

/**
 * 记忆接口异常类
 *
 * <p>当记忆模块的操作出现错误时抛出此异常，包含错误码和错误信息。</p>
 */
public class MemoryException extends Exception {
    /**
     * 错误码，用于标识具体的错误类型
     */
    private final int code;

    /**
     * 构造方法
     *
     * @param code 错误码
     * @param message 错误描述信息
     */
    public MemoryException(int code, String message) {
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
