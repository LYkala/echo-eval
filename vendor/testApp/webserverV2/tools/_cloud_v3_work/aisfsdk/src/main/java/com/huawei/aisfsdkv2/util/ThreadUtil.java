/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

/**
 * 线程工具类
 *
 * <p>提供线程相关的通用工具方法</p>
 */
public class ThreadUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger("ThreadUtil");

    /**
     * 安全睡眠（不抛出异常）
     *
     * <p>执行指定时间的睡眠，捕获并处理 InterruptedException，
     * 保持线程中断状态</p>
     *
     * @param millis 睡眠毫秒数
     */
    public static void sleepNoExcept(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("ThreadUtil.sleepNoExcept: sleep interrupted");
        }
    }
}
