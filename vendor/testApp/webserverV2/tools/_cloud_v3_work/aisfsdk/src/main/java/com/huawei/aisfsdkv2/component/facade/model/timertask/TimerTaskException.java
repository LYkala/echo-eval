/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.timertask;

/**
 * 定时任务异常
 *
 * @since 2026-04-08
 */
public class TimerTaskException extends Exception {

    private final int code;

    /**
     * 构造方法
     *
     * @param code 错误码
     * @param message 错误信息
     */
    public TimerTaskException(int code, String message) {
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
