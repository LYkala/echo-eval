/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2021. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logger;

/**
 * 日志工厂
 *
 * @author l00504561
 */
public final class LoggerFactory {
    private LoggerFactory() {
    }

    /**
     * 获取日志对象
     *
     * @param tag 日志标签（使用类名字符串，避免混淆问题）
     * @return 日志对象
     */
    public static Logger getLogger(String tag) {
        return new LoggerImpl(tag);
    }
}