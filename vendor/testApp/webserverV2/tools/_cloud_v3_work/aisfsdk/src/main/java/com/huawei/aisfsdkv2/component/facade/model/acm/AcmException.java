/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.acm;

/**
 * ACM接口异常类
 * <p>
 * 当调用ACM（Application Configuration Management）接口发生错误时抛出此异常。
 * 包含错误码和错误信息，用于上层应用进行错误处理和诊断。
 * </p>
 */
public class AcmException extends Exception {

    /** 错误码 */
    private final int code;

    /**
     * 构造方法，创建ACM异常对象
     *
     * @param code 错误码，标识具体的错误类型
     * @param message 错误描述信息
     */
    public AcmException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 获取错误码
     *
     * @return 错误码整数值
     */
    public int getCode() {
        return code;
    }
}
